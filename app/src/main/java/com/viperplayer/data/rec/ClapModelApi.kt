package com.viperplayer.data.rec

import com.viperplayer.BuildConfig
import com.viperplayer.data.social.BackendConfig
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * HTTP transport for the ViPER backend recommender-model endpoints (the `/v1/models/` routes;
 * github.com/iscle/viper-backend). Public GET (a model file is a static asset, not user data), over
 * the shared Ktor [HttpClient].
 *
 * Split into a small [fetchManifest] (JSON) and a streaming [openDownload] (which hands the caller
 * the response's [ByteReadChannel]) so the worker can stream ~73MB to a `.part` file — with resume
 * support and progress — without ever buffering the body in memory.
 */
@Singleton
class ClapModelApi @Inject constructor(
    private val httpClient: HttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** The configured backend base URL (no trailing slash), or null when not built in. */
    val baseUrl: String?
        get() = BackendConfig.baseUrlOf(BuildConfig.VIPER_BACKEND_URL)

    val isConfigured: Boolean get() = baseUrl != null

    /**
     * Fetches and parses `GET /v1/models/manifest`. Returns [ManifestResult.NotConfigured] when no
     * backend URL is built in, [ManifestResult.Unavailable] on any transport/HTTP/parse failure (the
     * model may simply not be placed on the server yet → 404), else [ManifestResult.Success].
     */
    suspend fun fetchManifest(): ManifestResult {
        val base = baseUrl ?: return ManifestResult.NotConfigured
        val bodyText = try {
            val response = httpClient.get("$base/v1/models/manifest") {
                header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            }
            if (!response.status.isSuccess()) {
                Timber.w("CLAP manifest fetch: HTTP ${response.status.value}")
                return ManifestResult.Unavailable
            }
            response.bodyAsText()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w("CLAP manifest fetch failed: ${e.javaClass.simpleName}")
            return ManifestResult.Unavailable
        }
        val entry = runCatching { json.decodeFromString<ClapModelManifest>(bodyText).clapAudioInt8 }
            .getOrElse {
                Timber.w("CLAP manifest parse failed: ${it.javaClass.simpleName}")
                return ManifestResult.Unavailable
            }
        return ManifestResult.Success(entry)
    }

    /**
     * Opens a streamed GET for [entry] and runs [block] with the response's [ByteReadChannel] while
     * the connection is open. [resumeFromByte] > 0 issues an HTTP `Range` request so a partial
     * `.part` file can resume (the backend advertises `Accept-Ranges: bytes`); the server replies 206
     * and the channel carries only the remaining bytes.
     *
     * [block] receives the channel and the response's `Content-Length` (bytes remaining in THIS
     * response — for a resumed 206 that is the tail length, not the full file). It must consume the
     * channel fully. Returns [DownloadResult.Success] once [block] completes, or an error otherwise.
     * The body is never buffered whole; the shared client sets no read timeout, so a generous
     * per-request window is applied here.
     */
    suspend fun openDownload(
        entry: ClapModelManifestEntry,
        resumeFromByte: Long = 0L,
        block: suspend (channel: ByteReadChannel, contentLength: Long?) -> Unit,
    ): DownloadResult {
        val base = baseUrl ?: return DownloadResult.NotConfigured
        val url = "$base${entry.path}"
        return try {
            httpClient.prepareGet(url) {
                timeout {
                    requestTimeoutMillis = DOWNLOAD_TIMEOUT_MS
                    socketTimeoutMillis = DOWNLOAD_SOCKET_TIMEOUT_MS
                }
                if (resumeFromByte > 0L) {
                    header(HttpHeaders.Range, "bytes=$resumeFromByte-")
                }
            }.execute { response ->
                if (!response.status.isSuccess()) {
                    Timber.w("CLAP model download: HTTP ${response.status.value}")
                    return@execute DownloadResult.Error
                }
                val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                block(response.bodyAsChannel(), contentLength)
                DownloadResult.Success
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w("CLAP model download failed: ${e.javaClass.simpleName}")
            DownloadResult.Error
        }
    }

    /** Outcome of [fetchManifest]. */
    sealed interface ManifestResult {
        data class Success(val entry: ClapModelManifestEntry) : ManifestResult
        data object NotConfigured : ManifestResult
        data object Unavailable : ManifestResult
    }

    /** Outcome of [openDownload]. */
    sealed interface DownloadResult {
        data object Success : DownloadResult
        data object NotConfigured : DownloadResult
        data object Error : DownloadResult
    }

    private companion object {
        // The model is ~73MB; the shared client has no read timeout and OkHttp's 10s default would
        // trip a large streamed download on a slow link. Give it a generous per-request window.
        private const val DOWNLOAD_TIMEOUT_MS = 10 * 60 * 1000L // 10 min overall
        private const val DOWNLOAD_SOCKET_TIMEOUT_MS = 60 * 1000L // 60s between reads
    }
}
