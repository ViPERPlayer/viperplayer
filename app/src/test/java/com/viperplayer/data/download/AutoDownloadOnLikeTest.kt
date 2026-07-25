package com.viperplayer.data.download

import com.viperplayer.domain.model.ArtistRef
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.Song
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for the [shouldAutoDownloadOnLike] decider: it must gate the auto-download-on-like
 * enqueue on the setting being on, the source being a remote plugin, and the song not already being
 * downloaded — and never fire for local, radio-derived, or already-downloaded songs, or a null song.
 */
class AutoDownloadOnLikeTest {

    private fun pluginSong(downloaded: Boolean = false) = Song(
        id = MediaId.Plugin(pluginId = "testsource", sourceId = "123"),
        title = "Remote Track",
        artists = listOf(ArtistRef(name = "Artist")),
        isDownloaded = downloaded,
    )

    private fun localSong() = Song(
        id = MediaId.Local(sourceId = "42"),
        title = "Local Track",
        artists = listOf(ArtistRef(name = "Artist")),
    )

    @Test
    fun enqueues_remoteUndownloadedSong_whenSettingOn() {
        assertTrue(shouldAutoDownloadOnLike(pluginSong(), settingEnabled = true))
    }

    @Test
    fun skips_whenSettingOff() {
        assertFalse(shouldAutoDownloadOnLike(pluginSong(), settingEnabled = false))
    }

    @Test
    fun skips_localSong_evenWhenSettingOn() {
        assertFalse(shouldAutoDownloadOnLike(localSong(), settingEnabled = true))
    }

    @Test
    fun skips_alreadyDownloadedSong_evenWhenSettingOn() {
        assertFalse(shouldAutoDownloadOnLike(pluginSong(downloaded = true), settingEnabled = true))
    }

    @Test
    fun skips_radioSong_evenWhenSettingOn() {
        val radio = Song(
            id = MediaId.Radio(seed = MediaId.Plugin(pluginId = "testsource", sourceId = "123")),
            title = "Radio",
        )
        assertFalse(shouldAutoDownloadOnLike(radio, settingEnabled = true))
    }

    @Test
    fun skips_nullSong() {
        assertFalse(shouldAutoDownloadOnLike(null, settingEnabled = true))
    }
}
