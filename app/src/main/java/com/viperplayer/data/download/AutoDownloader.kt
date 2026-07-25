package com.viperplayer.data.download

import com.viperplayer.domain.model.Song

/**
 * Narrow seam for "auto-download on like" so the media library repository can enqueue a download
 * without depending on the full [DownloadManager] (which itself depends on the repository — a Hilt
 * cycle). [DownloadManager] implements this; tests supply a fake that just records enqueues.
 */
interface AutoDownloader {
    /** Queue [song] for offline download (idempotent for an already in-flight download). */
    fun enqueue(song: Song)
}
