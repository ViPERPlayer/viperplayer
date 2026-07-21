package com.viperplayer.data.social

import com.viperplayer.domain.model.ArtistRef
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.Song
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [Song.toSessionTrack] — the host-side mapping of a domain [Song] to the wire-facing
 * [com.viperplayer.domain.model.SessionTrack] the host broadcasts.
 */
class SessionTrackMapperTest {

    @Test
    fun mapsIdentity_metadata_andBlankAlbum() {
        val song = Song(
            id = MediaId.Plugin("testsource", "12345"),
            title = "Blinding Lights",
            artists = listOf(ArtistRef("The Weeknd"), ArtistRef("Rosalía")),
            durationMs = 200_040,
            artworkUrl = "http://art/blinding.jpg",
        )

        val track = song.toSessionTrack()

        assertEquals("testsource", track.pluginId)
        assertEquals("12345", track.sourceId)
        assertEquals("Blinding Lights", track.title)
        assertEquals("The Weeknd, Rosalía", track.artist)
        assertEquals("http://art/blinding.jpg", track.artworkUrl)
        assertEquals(200_040L, track.durationMs)
        // Backend MediaRef has no album field; the mapper leaves it blank so the round-trip is faithful.
        assertEquals("", track.album)
        // Round-trips back to the same portable MediaId.
        assertEquals(song.id, track.mediaId)
    }

    @Test
    fun blankOptionalFields_becomeEmptyNotNull() {
        val song = Song(
            id = MediaId.Local("song-1"),
            title = "Untitled",
            artists = emptyList(),
            durationMs = null,
            artworkUrl = null,
        )

        val track = song.toSessionTrack()

        assertEquals("", track.artist)
        assertEquals("", track.artworkUrl)
        assertEquals(0L, track.durationMs)
    }
}
