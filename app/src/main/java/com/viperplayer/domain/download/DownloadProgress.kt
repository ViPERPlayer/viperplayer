package com.viperplayer.domain.download

import com.viperplayer.domain.model.MediaId

/** Where one song's download has got to. */
enum class DownloadState { QUEUED, RUNNING, COMPLETED, FAILED, UNSUPPORTED }

/**
 * Progress snapshot for one song's download.
 *
 * [downloadedBytes]/[totalBytes] are the running byte counters ([totalBytes] is `-1` when the server
 * sends no `Content-Length`); [bytesPerSec] is the most recent throttled transfer rate; [mimeType]
 * is the resolved stream's MIME (drives the codec label in the UI).
 */
data class DownloadProgress(
    val mediaId: MediaId,
    val state: DownloadState,
    val progress: Float,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L,
    val bytesPerSec: Long = 0L,
    val mimeType: String? = null,
)

/**
 * Short, user-facing codec label for a stream's [mimeType], or null when it is unknown or not one
 * the UI has a name for.
 *
 * Pure string mapping rather than a `DownloadManager` member, so the downloads screen can label a
 * row without depending on the data-layer manager that produced it.
 */
fun codecLabelFor(mimeType: String?): String? = when {
    mimeType == null -> null
    mimeType.contains("flac", ignoreCase = true) -> "FLAC"
    mimeType.contains("mp4", ignoreCase = true) || mimeType.contains("aac", ignoreCase = true) -> "M4A"
    mimeType.contains("mpeg", ignoreCase = true) -> "MP3"
    mimeType.contains("opus", ignoreCase = true) -> "OPUS"
    mimeType.contains("ogg", ignoreCase = true) -> "OGG"
    mimeType.contains("wav", ignoreCase = true) -> "WAV"
    else -> null
}
