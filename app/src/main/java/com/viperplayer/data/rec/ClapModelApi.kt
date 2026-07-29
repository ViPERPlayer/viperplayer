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
 * HTTP transport for the on-device CLAP recommender model. The model is a PUBLIC static asset (not user
 * data), so it is DECOUPLED from the private backend: it downloads from a dedicated public model-host
 * manifest URL ([BuildConfig.CLAP_MODEL_MANIFEST_URL]) when configured, else falls back to the private
 * backend's `/v1/models/manifest` ([BuildConfig.VIPER_BACKEND_URL]). Public GET, over the shared Ktor
 * [HttpClient].
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

    /** The configured private-backend base URL (no trailing slash), or null when not built in. */
    private val backendBaseUrl: String?
        get() = BackendConfig.baseUrlOf(BuildConfig.VIPER_BACKEND_URL)

    /**
     * A dedicated PUBLIC model-host manifest URL (a static host — GitHub Release / CDN / object storage),
     * or null when the build field is empty/placeholder. The model is a public static asset, so this
     * decouples it from the private [backendBaseUrl].
     */
    private val modelManifestUrl: String?
        get() = BuildConfig.CLAP_MODEL_MANIFEST_URL
            .takeIf { it.isNotBlank() && it != BackendConfig.PLACEHOLDER }

    /**
     * The manifest URL to fetch: the dedicated public model host if configured, else the private
     * backend's `/v1/models/manifest`, else null (no model source built in).
     */
    private fun manifestUrl(): String? =
        modelManifestUrl ?: backendBaseUrl?.let { "$it/v1/models/manifest" }

    /** True iff SOME model source (public manifest URL or the private backend) is built into this build. */
    val isConfigured: Boolean get() = manifestUrl() != null

    /**
     * Fetches and parses the model manifest (from the dedicated public model URL if configured, else the
     * backend). Returns [ManifestResult.NotConfigured] when no model source is built in,
     * [ManifestResult.Unavailable] on any transport/HTTP/parse failure (the model may simply not be
     * placed on the host yet → 404), else [ManifestResult.Success].
     */
    suspend fun fetchManifest(): ManifestResult {
        val url = manifestUrl() ?: return ManifestResult.NotConfigured
        val bodyText = try {
            val response = httpClient.get(url) {
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
        // A public manifest points at an ABSOLUTE model URL (static host); a private-backend manifest uses
        // a path relative to the backend base. Support both.
        val path = entry.path
        val url = when {
            path.startsWith("http://", ignoreCase = true) || path.startsWith("https://", ignoreCase = true) -> path
            else -> backendBaseUrl?.plus(path) ?: return DownloadResult.NotConfigured
        }
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
