package com.viperplayer.data.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for [shouldUseLocalFile], the offline-playback decision applied in
 * [ViperMediaSource.prepareSource] before any plugin stream resolution. A downloaded song plays from
 * its on-disk file only when it is flagged downloaded, has a persisted path, and the file still
 * exists; anything else falls through to the normal streaming path.
 */
class DownloadedFileGateTest {

    @Test
    fun usesLocalFile_whenDownloadedWithPathAndFilePresent() {
        assertTrue(shouldUseLocalFile(isDownloaded = true, downloadPath = "/data/song.mp3", fileExists = true))
    }

    @Test
    fun streams_whenNotDownloaded() {
        assertFalse(shouldUseLocalFile(isDownloaded = false, downloadPath = "/data/song.mp3", fileExists = true))
    }

    @Test
    fun streams_whenDownloadPathMissing() {
        assertFalse(shouldUseLocalFile(isDownloaded = true, downloadPath = null, fileExists = true))
    }

    @Test
    fun streams_whenDownloadPathBlank() {
        assertFalse(shouldUseLocalFile(isDownloaded = true, downloadPath = "  ", fileExists = true))
    }

    @Test
    fun streams_whenFileNoLongerOnDisk() {
        // Flag + path persisted but the file was cleared/evicted out from under it → re-stream.
        assertFalse(shouldUseLocalFile(isDownloaded = true, downloadPath = "/data/song.mp3", fileExists = false))
    }
}
