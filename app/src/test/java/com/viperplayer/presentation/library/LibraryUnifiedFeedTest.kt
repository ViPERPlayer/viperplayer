package com.viperplayer.presentation.library

import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.ArtistRef
import com.viperplayer.domain.model.HistoryEntry
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [buildUnifiedRecencyFeed] — the pure builder of the Library's default unified feed
 * (all types interleaved, most-recently-played first). Covers: play recency ordering across types,
 * album/artist recency inherited from their songs' plays, playlists (no history) trailing, and the
 * stable library-order tail for never-played items.
 */
class LibraryUnifiedFeedTest {

    private fun id(source: String) = MediaId("local", source)

    private fun song(source: String, albumId: MediaId? = null, artistId: MediaId? = null) = Song(
        id = id(source),
        title = source,
        album = albumId?.let { Album(id = it, name = "album-$it") },
        artists = artistId?.let { listOf(ArtistRef(name = "artist-$it", id = it)) } ?: emptyList(),
    )

    private fun history(song: Song, playedAt: Long) = HistoryEntry(song = song, playedAt = playedAt)

    @Test
    fun songs_orderedByMostRecentPlay_newestFirst() {
        val a = song("a")
        val b = song("b")
        val c = song("c")
        val feed = buildUnifiedRecencyFeed(
            songs = listOf(a, b, c),
            albums = emptyList(),
            artists = emptyList(),
            playlists = emptyList(),
            history = listOf(
                // Newest-first history: b @ 300, a @ 200, c @ 100.
                history(b, 300),
                history(a, 200),
                history(c, 100),
            ),
        )
        assertEquals(listOf(id("b"), id("a"), id("c")), feed.map { it.id })
    }

    @Test
    fun neverPlayed_trailInLibraryOrder_afterPlayed() {
        val played = song("played")
        val neverA = song("neverA")
        val neverB = song("neverB")
        val feed = buildUnifiedRecencyFeed(
            songs = listOf(neverA, neverB, played),
            albums = emptyList(),
            artists = emptyList(),
            playlists = emptyList(),
            history = listOf(history(played, 500)),
        )
        // Played first, then the two never-played in their incoming library order.
        assertEquals(listOf(id("played"), id("neverA"), id("neverB")), feed.map { it.id })
    }

    @Test
    fun albumAndArtist_inheritRecency_fromTheirSongsPlays() {
        val albumId = id("album1")
        val artistId = id("artist1")
        val s = song("s1", albumId = albumId, artistId = artistId)
        val album = Album(id = albumId, name = "album1")
        val artist = Artist(id = artistId, name = "artist1")
        // An unrelated, more-recently-played song so the played-vs-unplayed split is exercised.
        val other = song("other")

        val feed = buildUnifiedRecencyFeed(
            songs = listOf(s, other),
            albums = listOf(album),
            artists = listOf(artist),
            playlists = emptyList(),
            history = listOf(
                history(other, 900), // newest
                history(s, 400),
            ),
        )
        // `other` (900) is newest. `s`, its album and its artist all carry 400 and precede any
        // never-played item. All four here are played/derived, so no unplayed tail.
        val ids = feed.map { it.id }
        assertEquals(id("other"), ids.first())
        assertTrue("album inherits song's play recency", albumId in ids)
        assertTrue("artist inherits song's play recency", artistId in ids)
        // The album and artist (both @400, inherited from s) sit after `other` and around `s`.
        assertTrue(ids.indexOf(id("other")) < ids.indexOf(albumId))
        assertTrue(ids.indexOf(id("other")) < ids.indexOf(artistId))
    }

    @Test
    fun playlists_haveNoHistory_soTrailAfterPlayedItems() {
        val played = song("played")
        val playlist = Playlist(id = id("pl1"), name = "My Playlist")
        val feed = buildUnifiedRecencyFeed(
            songs = listOf(played),
            albums = emptyList(),
            artists = emptyList(),
            playlists = listOf(playlist),
            history = listOf(history(played, 100)),
        )
        // Played song first; the playlist (never any play history) trails.
        assertEquals(listOf(id("played"), id("pl1")), feed.map { it.id })
    }

    @Test
    fun emptyInputs_yieldEmptyFeed() {
        val feed = buildUnifiedRecencyFeed(
            songs = emptyList(),
            albums = emptyList(),
            artists = emptyList(),
            playlists = emptyList(),
            history = emptyList(),
        )
        assertTrue(feed.isEmpty())
    }
}
