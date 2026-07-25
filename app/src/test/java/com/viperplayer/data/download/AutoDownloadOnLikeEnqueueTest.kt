package com.viperplayer.data.download

import com.viperplayer.domain.model.ArtistRef
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the exact enqueue gate the like path uses — `if (shouldAutoDownloadOnLike(song, on))
 * autoDownloader.enqueue(song)` — against a fake [AutoDownloader] that just records enqueues. Verifies
 * that a remote, not-yet-downloaded song enqueues when the setting is ON, does NOT enqueue when it is
 * OFF, and that a local / already-downloaded song is skipped even with the setting ON.
 *
 * (A full [com.viperplayer.data.repository.MediaLibraryRepositoryImpl] test would require a Room/Android
 * harness; the auto-download decision the repo delegates to lives entirely in this pure gate.)
 */
class AutoDownloadOnLikeEnqueueTest {

    /** Minimal in-memory AutoDownloader recording every enqueued song (mirrors the fake-DAO idiom). */
    private class RecordingAutoDownloader : AutoDownloader {
        val enqueued = mutableListOf<Song>()
        override fun enqueue(song: Song) { enqueued.add(song) }
    }

    /** The like path, distilled: enqueue iff the decider says so. */
    private fun onLiked(song: Song?, settingEnabled: Boolean, downloader: AutoDownloader) {
        if (shouldAutoDownloadOnLike(song, settingEnabled)) downloader.enqueue(song!!)
    }

    private fun pluginSong(downloaded: Boolean = false) = Song(
        id = MediaId.Plugin(pluginId = "testsource", sourceId = "123"),
        title = "Remote Track",
        artists = listOf(ArtistRef(name = "Artist")),
        isDownloaded = downloaded,
    )

    private fun localSong() = Song(
        id = MediaId.Local(sourceId = "42"),
        title = "Local Track",
    )

    @Test
    fun settingOn_remoteSong_enqueuesOnce() {
        val downloader = RecordingAutoDownloader()
        val song = pluginSong()
        onLiked(song, settingEnabled = true, downloader = downloader)
        assertEquals(listOf(song), downloader.enqueued)
    }

    @Test
    fun settingOff_remoteSong_doesNotEnqueue() {
        val downloader = RecordingAutoDownloader()
        onLiked(pluginSong(), settingEnabled = false, downloader = downloader)
        assertTrue(downloader.enqueued.isEmpty())
    }

    @Test
    fun settingOn_localSong_doesNotEnqueue() {
        val downloader = RecordingAutoDownloader()
        onLiked(localSong(), settingEnabled = true, downloader = downloader)
        assertTrue(downloader.enqueued.isEmpty())
    }

    @Test
    fun settingOn_alreadyDownloadedSong_doesNotEnqueue() {
        val downloader = RecordingAutoDownloader()
        onLiked(pluginSong(downloaded = true), settingEnabled = true, downloader = downloader)
        assertTrue(downloader.enqueued.isEmpty())
    }
}
