package com.viperplayer.domain.sort

import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.ArtistRef
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.model.SortDirection
import com.viperplayer.domain.model.SortOption
import com.viperplayer.domain.model.SortOrder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Unit tests for [MediaSorter] — the pure sort-application logic. Covers the DEFAULT passthrough
 * (original order preserved, same list instance), each supported field, direction, and the graceful
 * no-op when a field doesn't apply to an item type.
 */
class MediaSorterTest {

    private var nextId = 0
    private fun song(
        title: String,
        artist: String? = null,
        album: String? = null,
        durationMs: Long? = null,
        track: Int? = null,
        disc: Int? = null,
    ): Song = Song(
        id = MediaId("test", "song${nextId++}"),
        title = title,
        artists = artist?.let { listOf(ArtistRef(it)) } ?: emptyList(),
        album = album?.let { Album(id = MediaId("test", "alb$it"), name = it) },
        durationMs = durationMs,
        trackNumber = track,
        discNumber = disc,
    )

    private fun album(name: String, artist: String? = null, year: Int? = null): Album = Album(
        id = MediaId("test", "album${nextId++}"),
        name = name,
        artists = artist?.let { listOf(ArtistRef(it)) } ?: emptyList(),
        releaseYear = year,
    )

    private fun artist(name: String) = Artist(id = MediaId("test", "artist${nextId++}"), name = name)
    private fun playlist(name: String) = Playlist(id = MediaId("test", "pl${nextId++}"), name = name)

    private val asc = SortDirection.ASCENDING
    private val desc = SortDirection.DESCENDING

    private val originalLocale: Locale = Locale.getDefault()

    @After
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

    // --- DEFAULT passthrough ---

    @Test
    fun default_preservesOriginalOrderAndReturnsSameInstance() {
        val songs = listOf(song("Zebra"), song("Alpha"), song("Mango"))
        val result = MediaSorter.sortSongs(songs, SortOrder.DEFAULT)
        // Same list, same order — no reordering at all.
        assertSame(songs, result)
        assertEquals(listOf("Zebra", "Alpha", "Mango"), result.map { it.title })
    }

    @Test
    fun default_forAllItemTypes_isPassthrough() {
        val albums = listOf(album("B"), album("A"))
        val artists = listOf(artist("B"), artist("A"))
        val playlists = listOf(playlist("B"), playlist("A"))
        assertSame(albums, MediaSorter.sortAlbums(albums, SortOrder.DEFAULT))
        assertSame(artists, MediaSorter.sortArtists(artists, SortOrder.DEFAULT))
        assertSame(playlists, MediaSorter.sortPlaylists(playlists, SortOrder.DEFAULT))
    }

    // --- songs ---

    @Test
    fun songs_byTitle_ascendingUsesNaturalOrder() {
        val songs = listOf(song("Track 10"), song("Track 2"), song("Track 1"))
        val result = MediaSorter.sortSongs(songs, SortOrder(SortOption.TITLE, asc))
        assertEquals(listOf("Track 1", "Track 2", "Track 10"), result.map { it.title })
    }

    @Test
    fun songs_byTitle_descendingReversesNaturalOrder() {
        val songs = listOf(song("Track 1"), song("Track 10"), song("Track 2"))
        val result = MediaSorter.sortSongs(songs, SortOrder(SortOption.TITLE, desc))
        assertEquals(listOf("Track 10", "Track 2", "Track 1"), result.map { it.title })
    }

    @Test
    fun songs_byArtist_handlesLeadingArticle() {
        val songs = listOf(
            song("s1", artist = "Coldplay"),
            song("s2", artist = "The Beatles"),
            song("s3", artist = "Beach House"),
        )
        val result = MediaSorter.sortSongs(songs, SortOrder(SortOption.ARTIST, asc))
        assertEquals(listOf("Beach House", "The Beatles", "Coldplay"), result.map { it.artistNames })
    }

    @Test
    fun songs_byDuration_isNumeric() {
        val songs = listOf(song("a", durationMs = 300), song("b", durationMs = 100), song("c", durationMs = 200))
        val result = MediaSorter.sortSongs(songs, SortOrder(SortOption.DURATION, asc))
        assertEquals(listOf(100L, 200L, 300L), result.map { it.durationMs })
    }

    @Test
    fun songs_byTrackNumber_ordersByDiscThenTrack() {
        val songs = listOf(
            song("a", track = 2, disc = 1),
            song("b", track = 1, disc = 2),
            song("c", track = 1, disc = 1),
        )
        val result = MediaSorter.sortSongs(songs, SortOrder(SortOption.TRACK_NUMBER, asc))
        assertEquals(listOf("c", "a", "b"), result.map { it.title })
    }

    @Test
    fun songs_byUnsupportedField_returnsUnchanged() {
        // YEAR is not a song field → passthrough.
        val songs = listOf(song("Zebra"), song("Alpha"))
        val result = MediaSorter.sortSongs(songs, SortOrder(SortOption.YEAR, asc))
        assertEquals(listOf("Zebra", "Alpha"), result.map { it.title })
    }

    @Test
    fun songs_sortIsStableForTies() {
        // Two songs with the same title keep their input order.
        val first = song("Same", durationMs = 1)
        val second = song("Same", durationMs = 2)
        val result = MediaSorter.sortSongs(listOf(first, second), SortOrder(SortOption.TITLE, asc))
        assertEquals(listOf(first.id, second.id), result.map { it.id })
    }

    // --- albums ---

    @Test
    fun albums_byYear_ascending() {
        val albums = listOf(album("a", year = 2001), album("b", year = 1999), album("c", year = 2010))
        val result = MediaSorter.sortAlbums(albums, SortOrder(SortOption.YEAR, asc))
        assertEquals(listOf(1999, 2001, 2010), result.map { it.releaseYear })
    }

    @Test
    fun albums_byAlbumArtist_usesFirstArtistNaturally() {
        val albums = listOf(album("x", artist = "The Doors"), album("y", artist = "ABBA"))
        val result = MediaSorter.sortAlbums(albums, SortOrder(SortOption.ALBUM_ARTIST, asc))
        // "ABBA" before "Doors" (article stripped).
        assertEquals(listOf("y", "x"), result.map { it.name })
    }

    @Test
    fun albums_byTitle_descending() {
        val albums = listOf(album("Apple"), album("Cherry"), album("Banana"))
        val result = MediaSorter.sortAlbums(albums, SortOrder(SortOption.TITLE, desc))
        assertEquals(listOf("Cherry", "Banana", "Apple"), result.map { it.name })
    }

    // --- artists / playlists ---

    @Test
    fun artists_byTitle_ascending() {
        val artists = listOf(artist("Zed"), artist("The Amazing"), artist("Bob"))
        val result = MediaSorter.sortArtists(artists, SortOrder(SortOption.TITLE, asc))
        // "The Amazing" → "Amazing" (A), then "Bob", then "Zed".
        assertEquals(listOf("The Amazing", "Bob", "Zed"), result.map { it.name })
    }

    @Test
    fun playlists_byTitle_descending() {
        val playlists = listOf(playlist("Chill"), playlist("Workout"), playlist("Focus"))
        val result = MediaSorter.sortPlaylists(playlists, SortOrder(SortOption.TITLE, desc))
        assertEquals(listOf("Workout", "Focus", "Chill"), result.map { it.name })
    }

    @Test
    fun emptyList_returnsEmpty() {
        assertEquals(emptyList<Song>(), MediaSorter.sortSongs(emptyList(), SortOrder(SortOption.TITLE, asc)))
    }

    // --- per-call locale resolution (issue #2) ---

    @Test
    fun sort_resolvesCurrentDefaultLocalePerCall_notFrozenAtClassLoad() {
        // MediaSorter must build its comparator from Locale.getDefault() on EACH call, so a runtime
        // locale change is honored without a process restart. In Swedish 'å' collates after 'z'; in
        // US English it folds near 'a'. The same input therefore sorts differently depending on the
        // default locale in effect at call time.
        val titles = listOf(album("Zeta"), album("Åska"))

        Locale.setDefault(Locale.US)
        val us = MediaSorter.sortAlbums(titles, SortOrder(SortOption.TITLE, asc)).map { it.name }
        // US: "Åska" (Å near A) before "Zeta".
        assertEquals(listOf("Åska", "Zeta"), us)

        Locale.setDefault(Locale.forLanguageTag("sv"))
        val swedish = MediaSorter.sortAlbums(titles, SortOrder(SortOption.TITLE, asc)).map { it.name }
        // Swedish: "Åska" (Å after Z) after "Zeta" — proving the new locale took effect for this pass.
        assertEquals(listOf("Zeta", "Åska"), swedish)

        // Sanity: the two locales genuinely disagreed on the same input.
        assertTrue(us != swedish)
    }
}
