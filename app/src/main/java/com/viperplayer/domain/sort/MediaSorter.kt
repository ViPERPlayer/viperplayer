package com.viperplayer.domain.sort

import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.model.SortDirection
import com.viperplayer.domain.model.SortOption
import com.viperplayer.domain.model.SortOrder

/**
 * Pure sorting for library/detail lists. Applies a [SortOrder] to a list of domain items, leaving the
 * list untouched (original order) for [SortOption.DEFAULT] or any field a given item type can't sort by.
 *
 * All comparisons for name-like fields go through [NaturalOrderComparator] so "Track 2" sorts before
 * "Track 10" and leading articles ("The ") are handled sensibly. Sorting is stable, so items that tie
 * on the chosen key keep their original relative order.
 *
 * The `sortSongs`/`sortAlbums`/etc. helpers are pure and side-effect free — they're the single place
 * the UI layer's sort selection is turned into an actual ordering, and are unit-tested directly.
 */
object MediaSorter {

    private val naturalComparator = NaturalOrderComparator.DEFAULT

    /**
     * Apply [order] to [songs]. Supported fields: TITLE, ARTIST, ALBUM, DURATION, TRACK_NUMBER.
     * Everything else (including DEFAULT) returns the list unchanged.
     */
    fun sortSongs(songs: List<Song>, order: SortOrder): List<Song> {
        if (order.isDefault) return songs
        val comparator: Comparator<Song> = when (order.option) {
            SortOption.TITLE -> compareBy(naturalComparator) { it.title }
            SortOption.ARTIST -> compareBy(naturalComparator) { it.artistNames }
            SortOption.ALBUM -> compareBy(naturalComparator) { it.album?.name }
            SortOption.DURATION -> compareBy { it.durationMs ?: 0L }
            SortOption.TRACK_NUMBER -> compareBy(
                { it.discNumber ?: Int.MAX_VALUE },
                { it.trackNumber ?: Int.MAX_VALUE },
            )
            else -> return songs // Unsupported field for songs → leave as-is.
        }
        return songs.sortedWith(order.direction.orient(comparator))
    }

    /**
     * Apply [order] to [albums]. Supported fields: TITLE, ALBUM_ARTIST (the album's artist), YEAR.
     * Everything else (including DEFAULT) returns the list unchanged.
     */
    fun sortAlbums(albums: List<Album>, order: SortOrder): List<Album> {
        if (order.isDefault) return albums
        val comparator: Comparator<Album> = when (order.option) {
            SortOption.TITLE -> compareBy(naturalComparator) { it.name }
            SortOption.ARTIST, SortOption.ALBUM_ARTIST ->
                compareBy(naturalComparator) { it.artists.firstOrNull()?.name }
            SortOption.YEAR -> compareBy { it.releaseYear ?: Int.MIN_VALUE }
            else -> return albums
        }
        return albums.sortedWith(order.direction.orient(comparator))
    }

    /**
     * Apply [order] to [artists]. Supported field: TITLE (the artist name). Everything else
     * (including DEFAULT) returns the list unchanged.
     */
    fun sortArtists(artists: List<Artist>, order: SortOrder): List<Artist> {
        if (order.isDefault) return artists
        val comparator: Comparator<Artist> = when (order.option) {
            SortOption.TITLE -> compareBy(naturalComparator) { it.name }
            else -> return artists
        }
        return artists.sortedWith(order.direction.orient(comparator))
    }

    /**
     * Apply [order] to [playlists]. Supported field: TITLE (the playlist name). Everything else
     * (including DEFAULT) returns the list unchanged.
     */
    fun sortPlaylists(playlists: List<Playlist>, order: SortOrder): List<Playlist> {
        if (order.isDefault) return playlists
        val comparator: Comparator<Playlist> = when (order.option) {
            SortOption.TITLE -> compareBy(naturalComparator) { it.name }
            else -> return playlists
        }
        return playlists.sortedWith(order.direction.orient(comparator))
    }

    /** Reverse [comparator] for a DESCENDING [SortDirection]; ASCENDING keeps it as-is. */
    private fun <T> SortDirection.orient(comparator: Comparator<T>): Comparator<T> =
        if (this == SortDirection.DESCENDING) comparator.reversed() else comparator
}
