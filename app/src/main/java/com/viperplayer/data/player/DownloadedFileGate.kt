package com.viperplayer.data.player

/**
 * Pure decision for offline playback: should this song be played from its on-disk downloaded file
 * (built as a progressive [androidx.media3.exoplayer.source.MediaSource] from a `file://` URI)
 * instead of re-resolving and re-streaming it through the plugin?
 *
 * Uses the local file only when ALL of:
 * - [isDownloaded] is set on the persisted song row (the [DownloadManager] finished writing it), and
 * - [downloadPath] is a non-blank on-disk path (persisted alongside the flag), and
 * - [fileExists] — the file is actually still present (guards against a download that was cleared or
 *   evicted out from under the flag, e.g. by the OS clearing app storage).
 *
 * When it returns false, playback falls through to the normal plugin stream-resolution path. A
 * downloaded track is always a progressive HTTP download (the [DownloadManager] marks DASH/HLS/PCM
 * UNSUPPORTED), so the local path only ever needs the progressive source.
 *
 * Android-free so it can be unit-tested directly; [ViperMediaSource.prepareSource] applies it.
 */
fun shouldUseLocalFile(isDownloaded: Boolean, downloadPath: String?, fileExists: Boolean): Boolean =
    isDownloaded && !downloadPath.isNullOrBlank() && fileExists
