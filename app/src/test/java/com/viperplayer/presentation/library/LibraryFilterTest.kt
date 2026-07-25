package com.viperplayer.presentation.library

import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.ArtistRef
import com.viperplayer.domain.model.Genre
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure in-place library filter behind the Library toolbar search field. Verify the
 * "off" (blank) query passes lists through unchanged, that a non-blank query narrows case-insensitively
 * over the display name/title, and that a song matches on its artist name too.
 */
class LibraryFilterTest {

    private var nextId = 0

    private fun song(title: String, artist: String? = null): Song = Song(
        id = MediaId.Plugin("test", "song${nextId++}"),
        title = title,
        artists = artist?.let { listOf(ArtistRef(name = it)) } ?: emptyList(),
    )

    private fun album(name: String, artist: String? = null): Album = Album(
        id = MediaId.Plugin("test", "album${nextId++}"),
        name = name,
        artists = artist?.let { listOf(ArtistRef(name = it)) } ?: emptyList(),
    )

    private fun artist(name: String): Artist =
        Artist(id = MediaId.Plugin("test", "artist${nextId++}"), name = name)

    private fun playlist(name: String): Playlist =
        Playlist(id = MediaId.Plugin("test", "playlist${nextId++}"), name = name)

    private fun genre(name: String): Genre = Genre(id = nextId++.toLong(), name = name, songCount = 1)

    // --- isBlank / off state ---

    @Test
    fun blankQueryReportsBlank() {
        assertTrue(LibraryFilter.isBlank(""))
        assertTrue(LibraryFilter.isBlank("   "))
        assertTrue(!LibraryFilter.isBlank("a"))
    }

    @Test
    fun emptyQueryReturnsSameSongsListInstance() {
        val songs = listOf(song("Alpha"), song("Beta"))
        assertSame(songs, LibraryFilter.songs(songs, ""))
        assertSame(songs, LibraryFilter.songs(songs, "   "))
    }

    @Test
    fun emptyQueryReturnsAllForEveryType() {
        val albums = listOf(album("A"), album("B"))
        val artists = listOf(artist("A"), artist("B"))
        val playlists = listOf(playlist("A"), playlist("B"))
        val genres = listOf(genre("A"), genre("B"))
        assertSame(albums, LibraryFilter.albums(albums, ""))
        assertSame(artists, LibraryFilter.artists(artists, ""))
        assertSame(playlists, LibraryFilter.playlists(playlists, ""))
        assertSame(genres, LibraryFilter.genres(genres, ""))
    }

    // --- narrowing ---

    @Test
    fun songsNarrowByTitleSubstring() {
        val a = song("Bohemian Rhapsody")
        val b = song("We Will Rock You")
        val result = LibraryFilter.songs(listOf(a, b), "rock")
        assertEquals(listOf(b), result)
    }

    @Test
    fun songsMatchArtistName() {
        val a = song("Bohemian Rhapsody", artist = "Queen")
        val b = song("Thriller", artist = "Michael Jackson")
        // Query matches neither title but does match an artist name.
        val result = LibraryFilter.songs(listOf(a, b), "queen")
        assertEquals(listOf(a), result)
    }

    @Test
    fun matchingIsCaseInsensitive() {
        val a = song("Uptown Funk")
        assertEquals(listOf(a), LibraryFilter.songs(listOf(a), "UPTOWN"))
        assertEquals(listOf(a), LibraryFilter.songs(listOf(a), "uptown"))
        assertEquals(listOf(a), LibraryFilter.songs(listOf(a), "FuNk"))
    }

    @Test
    fun queryIsTrimmedBeforeMatching() {
        val a = song("Africa")
        assertEquals(listOf(a), LibraryFilter.songs(listOf(a), "  afri  "))
    }

    @Test
    fun noMatchReturnsEmpty() {
        val songs = listOf(song("Alpha"), song("Beta"))
        assertTrue(LibraryFilter.songs(songs, "zzz").isEmpty())
    }

    @Test
    fun albumsMatchNameOrArtist() {
        val byName = album("Abbey Road")
        val byArtist = album("Let It Be", artist = "The Beatles")
        assertEquals(listOf(byName), LibraryFilter.albums(listOf(byName, byArtist), "abbey"))
        assertEquals(listOf(byArtist), LibraryFilter.albums(listOf(byName, byArtist), "beatles"))
    }

    @Test
    fun artistsPlaylistsGenresNarrowByName() {
        val radiohead = artist("Radiohead")
        val muse = artist("Muse")
        assertEquals(listOf(radiohead), LibraryFilter.artists(listOf(radiohead, muse), "radio"))

        val workout = playlist("Workout Mix")
        val chill = playlist("Chill")
        assertEquals(listOf(workout), LibraryFilter.playlists(listOf(workout, chill), "work"))

        val rock = genre("Rock")
        val jazz = genre("Jazz")
        assertEquals(listOf(rock), LibraryFilter.genres(listOf(rock, jazz), "rock"))
    }

    // --- unified feed ---

    @Test
    fun unifiedFeedFiltersAcrossTypes() {
        val items = listOf(
            LibraryFeedItem.SongItem(song("Rocket Man", artist = "Elton John")),
            LibraryFeedItem.AlbumItem(album("Rocks", artist = "Aerosmith")),
            LibraryFeedItem.ArtistItem(artist("Beethoven")),
            LibraryFeedItem.PlaylistItem(playlist("Sleep")),
        )
        val result = LibraryFilter.unified(items, "rock")
        // Song title "Rocket Man" and album "Rocks" both contain "rock"; the others don't.
        assertEquals(2, result.size)
        assertTrue(result.all { it is LibraryFeedItem.SongItem || it is LibraryFeedItem.AlbumItem })
    }

    @Test
    fun unifiedBlankQueryReturnsSameInstance() {
        val items = listOf(LibraryFeedItem.SongItem(song("X")))
        assertSame(items, LibraryFilter.unified(items, ""))
    }
}
