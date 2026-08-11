package com.viperplayer.data.download

import android.content.Context
import com.viperplayer.data.source.PluginDataSource
import com.viperplayer.domain.download.DownloadProgress
import com.viperplayer.domain.download.DownloadState
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.repository.MediaLibraryRepository
import com.viperplayer.plugin.model.UrlStream
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.request.headers
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline download engine for progressive HTTP(S) URL streams.
 *
 * A song is resolved through its plugin; only the [UrlStream] variant with an http(s) URL is
 * downloadable here. DASH / HLS / PCM (and DRM) streams are deliberately marked
 * [DownloadState.UNSUPPORTED]: true adaptive/DRM offline support would require a media3 `DownloadService`
 * plus a `CacheDataSource` — a larger rearchitecture that is intentionally not implemented here.
 */
@Singleton
class DownloadManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val pluginDataSource: PluginDataSource,
    private val httpClient: HttpClient,
    private val mediaLibraryRepository: MediaLibraryRepository,
) : AutoDownloader {
    /** State of a single download. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Bound concurrency so a bulk enqueue doesn't open dozens of sockets at once.
    private val semaphore = Semaphore(permits = 2)

    private val _downloads = MutableStateFlow<Map<MediaId, DownloadProgress>>(emptyMap())
    val downloads: StateFlow<Map<MediaId, DownloadProgress>> = _downloads.asStateFlow()

    private val downloadsDir: File
        get() = File(context.filesDir, "downloads").apply { mkdirs() }

    private fun update(mediaId: MediaId, state: DownloadState, progress: Float, mimeType: String? = null) {
        _downloads.update {
            it + (mediaId to DownloadProgress(mediaId, state, progress, mimeType = mimeType))
        }
    }

    /** Running-progress update carrying live byte counters + transfer rate for the in-progress row. */
    private fun updateRunning(
        mediaId: MediaId,
        progress: Float,
        downloadedBytes: Long,
        totalBytes: Long,
        bytesPerSec: Long,
        mimeType: String?,
    ) {
        _downloads.update {
            it + (mediaId to DownloadProgress(
                mediaId = mediaId,
                state = DownloadState.RUNNING,
                progress = progress,
                downloadedBytes = downloadedBytes,
                totalBytes = totalBytes,
                bytesPerSec = bytesPerSec,
                mimeType = mimeType,
            ))
        }
    }

    /**
     * Queue [song] for download. Resolves its stream, and if it's a progressive URL streams the
     * bytes to internal storage reporting progress; otherwise marks it [DownloadState.UNSUPPORTED]. On
     * success the song row is persisted and flagged downloaded; on error the partial file is deleted.
     */
    override fun enqueue(song: Song) {
        val mediaId = song.id
        // Ignore a re-enqueue of something already in flight.
        val existing = _downloads.value[mediaId]?.state
        if (existing == DownloadState.QUEUED || existing == DownloadState.RUNNING) return

        update(mediaId, DownloadState.QUEUED, 0f)
        scope.launch {
            semaphore.withPermit {
                runCatching { download(song) }
                    .onFailure { Timber.e(it, "Download failed for $mediaId") }
            }
        }
    }

    private suspend fun download(song: Song) {
        val mediaId = song.id
        update(mediaId, DownloadState.RUNNING, 0f)

        val resolved = pluginDataSource.getStream(mediaId, isVideo = false).getOrElse {
            Timber.e(it, "Could not resolve stream for $mediaId")
            update(mediaId, DownloadState.FAILED, 0f)
            return
        }

        val source = resolved.source
        // Only a progressive http(s) URL is downloadable in this pass.
        val urlStream = source as? UrlStream
        val url = urlStream?.url
        if (urlStream == null || url == null || !url.startsWith("http", ignoreCase = true)) {
            update(mediaId, DownloadState.UNSUPPORTED, 0f)
            return
        }

        val mimeType = urlStream.mimeType
        val target = File(downloadsDir, "${sanitize(mediaId)}.${extensionFor(mimeType)}")
        val result = runCatching {
            streamToFile(url, urlStream.headers, target) { progress, downloadedBytes, totalBytes, bytesPerSec ->
                updateRunning(mediaId, progress, downloadedBytes, totalBytes, bytesPerSec, mimeType)
            }
        }

        result.onSuccess {
            runCatching {
                mediaLibraryRepository.saveSong(song)
                mediaLibraryRepository.setSongDownloaded(mediaId, true, target.absolutePath)
            }.onFailure { Timber.e(it, "Failed to persist download state for $mediaId") }
            update(mediaId, DownloadState.COMPLETED, 1f)
        }.onFailure {
            Timber.e(it, "Error writing download for $mediaId")
            runCatching { target.delete() }
            update(mediaId, DownloadState.FAILED, 0f)
        }
    }

    /**
     * Stream [url] to [target], invoking [onProgress] with the 0..1 fraction (0 when length unknown),
     * the running downloaded/total byte counters (total is `-1` when unknown) and the transfer rate
     * in bytes/sec. Progress callbacks are throttled to [PROGRESS_THROTTLE_MS] to avoid flooding the
     * download StateFlow (the final chunk always emits so the row settles on the true byte count).
     */
    private suspend fun streamToFile(
        url: String,
        headers: Map<String, String>,
        target: File,
        onProgress: (progress: Float, downloadedBytes: Long, totalBytes: Long, bytesPerSec: Long) -> Unit,
    ) {
        httpClient.prepareGet(url) {
            headers { headers.forEach { (name, value) -> append(name, value) } }
        }.execute { response: HttpResponse ->
            val contentLength = response.contentLength() ?: -1L
            val channel: ByteReadChannel = response.bodyAsChannel()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var readBytes = 0L

            // Throttled speed sampling: measure bytes delta over a >=PROGRESS_THROTTLE_MS window.
            var windowStartTs = System.currentTimeMillis()
            var windowStartBytes = 0L
            var lastEmitTs = windowStartTs
            var bytesPerSec = 0L

            target.outputStream().use { output ->
                while (!channel.isClosedForRead) {
                    val count = channel.readAvailable(buffer, 0, buffer.size)
                    if (count <= 0) continue
                    output.write(buffer, 0, count)
                    readBytes += count

                    val now = System.currentTimeMillis()
                    if (now - lastEmitTs >= PROGRESS_THROTTLE_MS) {
                        val elapsedMs = now - windowStartTs
                        if (elapsedMs > 0) {
                            bytesPerSec = (readBytes - windowStartBytes) * 1000L / elapsedMs
                        }
                        windowStartTs = now
                        windowStartBytes = readBytes
                        lastEmitTs = now
                        val fraction =
                            if (contentLength > 0) (readBytes.toFloat() / contentLength).coerceIn(0f, 1f) else 0f
                        onProgress(fraction, readBytes, contentLength, bytesPerSec)
                    }
                }
                output.flush()
            }
            // Final emit so the row reflects the true completed byte count / 100%.
            val fraction =
                if (contentLength > 0) (readBytes.toFloat() / contentLength).coerceIn(0f, 1f) else 0f
            onProgress(fraction, readBytes, contentLength, bytesPerSec)
        }
    }

    /** Delete a completed/in-progress download and clear its persisted flag + map entry. */
    suspend fun remove(mediaId: MediaId) {
        runCatching {
            downloadsDir.listFiles()
                ?.filter { it.name.startsWith(sanitize(mediaId) + ".") }
                ?.forEach { it.delete() }
        }.onFailure { Timber.w(it, "Failed to delete download file for $mediaId") }

        runCatching { mediaLibraryRepository.setSongDownloaded(mediaId, false, null) }
            .onFailure { Timber.w(it, "Failed to clear downloaded flag for $mediaId") }

        _downloads.update { it - mediaId }
    }

    private fun sanitize(mediaId: MediaId): String =
        "${mediaId.routingPluginId}_${mediaId.sourceId}".replace(Regex("[^A-Za-z0-9._-]"), "_")

    private fun extensionFor(mimeType: String?): String = when {
        mimeType == null -> "bin"
        mimeType.contains("mpeg", ignoreCase = true) -> "mp3"
        mimeType.contains("mp4", ignoreCase = true) || mimeType.contains("aac", ignoreCase = true) -> "m4a"
        mimeType.contains("flac", ignoreCase = true) -> "flac"
        mimeType.contains("ogg", ignoreCase = true) || mimeType.contains("opus", ignoreCase = true) -> "ogg"
        mimeType.contains("wav", ignoreCase = true) -> "wav"
        else -> "bin"
    }

    companion object {
        /**
         * Minimum spacing between running-progress emissions. Bounds the download StateFlow update
         * rate (so a fast transfer doesn't spam the UI) and defines the window the transfer rate is
         * sampled over.
         */
        private const val PROGRESS_THROTTLE_MS = 500L

        /**
         * A short, human-readable codec/container label for a stream's MIME type (e.g. `FLAC`, `M4A`,
         * `MP3`), or `null` when unknown. Mirrors [extensionFor]'s mapping so the in-progress row's
         * codec chip matches the file the download will land as.
         */
    }
}
