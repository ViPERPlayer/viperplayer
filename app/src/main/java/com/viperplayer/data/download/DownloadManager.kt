package com.viperplayer.data.download

import android.content.Context
import com.viperplayer.data.source.PluginDataSource
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
 * downloadable here. DASH / HLS / PCM (and DRM) streams are marked [State.UNSUPPORTED] — true
 * adaptive/DRM offline support needs a media3 `DownloadService`, which is out of scope for this pass.
 *
 * TODO: adaptive/DRM (DASH/HLS) downloads via media3 DownloadService + a CacheDataSource.
 */
@Singleton
class DownloadManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val pluginDataSource: PluginDataSource,
    private val httpClient: HttpClient,
    private val mediaLibraryRepository: MediaLibraryRepository,
) {
    /** State of a single download. */
    enum class State { QUEUED, RUNNING, COMPLETED, FAILED, UNSUPPORTED }

    /** Progress snapshot for one song's download, keyed by [mediaId] in [downloads]. */
    data class DownloadProgress(
        val mediaId: MediaId,
        val state: State,
        val progress: Float,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Bound concurrency so a bulk enqueue doesn't open dozens of sockets at once.
    private val semaphore = Semaphore(permits = 2)

    private val _downloads = MutableStateFlow<Map<MediaId, DownloadProgress>>(emptyMap())
    val downloads: StateFlow<Map<MediaId, DownloadProgress>> = _downloads.asStateFlow()

    private val downloadsDir: File
        get() = File(context.filesDir, "downloads").apply { mkdirs() }

    private fun update(mediaId: MediaId, state: State, progress: Float) {
        _downloads.update { it + (mediaId to DownloadProgress(mediaId, state, progress)) }
    }

    /**
     * Queue [song] for download. Resolves its stream, and if it's a progressive URL streams the
     * bytes to internal storage reporting progress; otherwise marks it [State.UNSUPPORTED]. On
     * success the song row is persisted and flagged downloaded; on error the partial file is deleted.
     */
    fun enqueue(song: Song) {
        val mediaId = song.id
        // Ignore a re-enqueue of something already in flight.
        val existing = _downloads.value[mediaId]?.state
        if (existing == State.QUEUED || existing == State.RUNNING) return

        update(mediaId, State.QUEUED, 0f)
        scope.launch {
            semaphore.withPermit {
                runCatching { download(song) }
                    .onFailure { Timber.e(it, "Download failed for $mediaId") }
            }
        }
    }

    private suspend fun download(song: Song) {
        val mediaId = song.id
        update(mediaId, State.RUNNING, 0f)

        val resolved = pluginDataSource.getStream(mediaId, isVideo = false).getOrElse {
            Timber.e(it, "Could not resolve stream for $mediaId")
            update(mediaId, State.FAILED, 0f)
            return
        }

        val source = resolved.source
        // Only a progressive http(s) URL is downloadable in this pass.
        val urlStream = source as? UrlStream
        val url = urlStream?.url
        if (urlStream == null || url == null || !url.startsWith("http", ignoreCase = true)) {
            update(mediaId, State.UNSUPPORTED, 0f)
            return
        }

        val target = File(downloadsDir, "${sanitize(mediaId)}.${extensionFor(urlStream.mimeType)}")
        val result = runCatching {
            streamToFile(url, urlStream.headers, target) { progress ->
                update(mediaId, State.RUNNING, progress)
            }
        }

        result.onSuccess {
            runCatching {
                mediaLibraryRepository.saveSong(song)
                mediaLibraryRepository.setSongDownloaded(mediaId, true, target.absolutePath)
            }.onFailure { Timber.e(it, "Failed to persist download state for $mediaId") }
            update(mediaId, State.COMPLETED, 1f)
        }.onFailure {
            Timber.e(it, "Error writing download for $mediaId")
            runCatching { target.delete() }
            update(mediaId, State.FAILED, 0f)
        }
    }

    /** Stream [url] to [target], invoking [onProgress] with a 0..1 fraction (0 when length unknown). */
    private suspend fun streamToFile(
        url: String,
        headers: Map<String, String>,
        target: File,
        onProgress: (Float) -> Unit,
    ) {
        httpClient.prepareGet(url) {
            headers { headers.forEach { (name, value) -> append(name, value) } }
        }.execute { response: HttpResponse ->
            val contentLength = response.contentLength() ?: -1L
            val channel: ByteReadChannel = response.bodyAsChannel()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var readBytes = 0L
            target.outputStream().use { output ->
                while (!channel.isClosedForRead) {
                    val count = channel.readAvailable(buffer, 0, buffer.size)
                    if (count <= 0) continue
                    output.write(buffer, 0, count)
                    readBytes += count
                    if (contentLength > 0) {
                        onProgress((readBytes.toFloat() / contentLength).coerceIn(0f, 1f))
                    }
                }
                output.flush()
            }
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
        "${mediaId.pluginId}_${mediaId.sourceId}".replace(Regex("[^A-Za-z0-9._-]"), "_")

    private fun extensionFor(mimeType: String?): String = when {
        mimeType == null -> "bin"
        mimeType.contains("mpeg", ignoreCase = true) -> "mp3"
        mimeType.contains("mp4", ignoreCase = true) || mimeType.contains("aac", ignoreCase = true) -> "m4a"
        mimeType.contains("flac", ignoreCase = true) -> "flac"
        mimeType.contains("ogg", ignoreCase = true) || mimeType.contains("opus", ignoreCase = true) -> "ogg"
        mimeType.contains("wav", ignoreCase = true) -> "wav"
        else -> "bin"
    }
}
