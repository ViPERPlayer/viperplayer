package com.viperplayer.portable

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable

@Serializable
data class PluginHttpRequest(
    val url: String,
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
)

@Serializable
data class PluginHttpResponse(val status: Int, val body: String)

fun interface PluginHttp {
    suspend fun request(request: PluginHttpRequest): PluginHttpResponse
}

/** Checked before every host call, including when the transport is replaced in tests. */
class HttpPolicy(private val allowedHosts: Set<String>) {
    fun validate(request: PluginHttpRequest) {
        val uri = runCatching { URI(request.url) }.getOrNull()
        if (uri == null || uri.scheme != "https" || uri.host !in allowedHosts ||
            uri.rawUserInfo != null || uri.port !in setOf(-1, 443) || uri.fragment != null
        ) throw PortablePluginException("PERMISSION_DENIED", "HTTP destination is not permitted")
        if (request.method !in setOf("GET", "POST")) {
            throw PortablePluginException("INVALID_REQUEST", "Only GET and POST are supported")
        }
        if ((request.body?.toByteArray()?.size ?: 0) > 262_144 || request.headers.size > 32) {
            throw PortablePluginException("INVALID_REQUEST", "HTTP request is too large")
        }
        val forbidden = setOf("host", "connection", "content-length", "transfer-encoding", "upgrade", "cookie", "authorization", "proxy-authorization")
        if (request.headers.any { (name, value) ->
            name.lowercase() in forbidden || !name.matches(Regex("[A-Za-z0-9-]+")) ||
                value.length > 4096 || '\r' in value || '\n' in value
        }) throw PortablePluginException("PERMISSION_DENIED", "HTTP header is not permitted")
    }
}

/** Anonymous HTTPS only. Redirects are rejected so they cannot escape manifest permissions. */
class UrlConnectionPluginHttp : PluginHttp {
    override suspend fun request(request: PluginHttpRequest): PluginHttpResponse = coroutineScope {
        suspendCancellableCoroutine { continuation ->
            val active = AtomicReference<HttpURLConnection?>()
            continuation.invokeOnCancellation { active.get()?.disconnect() }
            launch(Dispatchers.IO) {
                var connection: HttpURLConnection? = null
                try {
                    if (!continuation.isActive) return@launch
                    connection = URI(request.url).toURL().openConnection() as HttpURLConnection
                    active.set(connection)
                    if (!continuation.isActive) return@launch
                    connection.instanceFollowRedirects = false
                    connection.connectTimeout = 10_000
                    connection.readTimeout = 10_000
                    connection.useCaches = false
                    connection.requestMethod = request.method
                    request.headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
                    request.body?.let { body ->
                        connection.doOutput = true
                        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                    }
                    val status = connection.responseCode
                    if (status in 300..399) throw PortablePluginException("NETWORK", "Redirects are not supported")
                    val stream = if (status >= 400) connection.errorStream else connection.inputStream
                    val body = stream?.use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            if (!continuation.isActive) return@launch
                            val read = input.read(buffer)
                            if (read < 0) break
                            if (output.size() + read > 2_097_152) {
                                throw PortablePluginException("NETWORK", "HTTP response exceeds 2 MiB")
                            }
                            output.write(buffer, 0, read)
                        }
                        output.toString("UTF-8")
                    }.orEmpty()
                    if (continuation.isActive) continuation.resumeWith(Result.success(PluginHttpResponse(status, body)))
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWith(Result.failure(error))
                } finally {
                    active.set(null)
                    connection?.disconnect()
                }
            }
        }
    }
}
