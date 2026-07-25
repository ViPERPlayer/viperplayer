package com.viperplayer.presentation.widget

import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.Playlist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure selection logic for the playlist / quick-launch widget rows. Android-free: entries carry a
 * typed [MediaId], so no `android.net.Uri` encoding runs during the test.
 */
class PlaylistWidgetDataTest {

    private fun playlist(id: String, name: String, songCount: Int) = Playlist(
        id = MediaId.Local(id),
        name = name,
        songCount = songCount,
    )

    private fun likedSongs(count: Int) = Playlist(
        id = MediaId.Local("liked_songs"),
        name = "Liked Songs",
        songCount = count,
    )

    @Test
    fun likedSongsFirst_thenLocalsInOrder() {
        val result = PlaylistWidgetData.select(
            likedSongs = likedSongs(5),
            localPlaylists = listOf(
                playlist("a", "Alpha", 3),
                playlist("b", "Beta", 7),
            ),
        )
        assertEquals(listOf("Liked Songs", "Alpha", "Beta"), result.map { it.name })
        assertEquals(5, result.first().songCount)
        assertEquals(MediaId.Local("a"), result[1].mediaId)
    }

    @Test
    fun emptyLikedSongs_isExcluded() {
        val result = PlaylistWidgetData.select(
            likedSongs = likedSongs(0),
            localPlaylists = listOf(playlist("a", "Alpha", 3)),
        )
        assertEquals(listOf("Alpha"), result.map { it.name })
    }

    @Test
    fun cappedAtLimit() {
        val locals = (1..20).map { playlist("p$it", "P$it", it) }
        val result = PlaylistWidgetData.select(
            likedSongs = likedSongs(4),
            localPlaylists = locals,
            limit = 5,
        )
        assertEquals(5, result.size)
        // Liked Songs takes the first slot, then the first four locals.
        assertEquals(listOf("Liked Songs", "P1", "P2", "P3", "P4"), result.map { it.name })
    }

    @Test
    fun emptyEverything_isEmpty() {
        val result = PlaylistWidgetData.select(
            likedSongs = likedSongs(0),
            localPlaylists = emptyList(),
        )
        assertTrue(result.isEmpty())
    }
}
