package com.viperplayer.presentation.library

import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.Genre
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.Song

/**
 * Pure, Android-free in-place filtering for the Library's per-tab lists. Factored out of
 * [LibraryViewModel] so the case-insensitive substring matching can be unit-tested without a player
 * or a repository, and so the composable stays free of any matching logic (MVVM).
 *
 * The match is a case-insensitive substring over the item's display name/title. For songs the artist
 * name is also matched, so typing an artist narrows the Songs tab to that artist's tracks. A blank
 * query is the "off" state and returns the input list unchanged (same instance), preserving the
 * caller's existing sort order.
 */
object LibraryFilter {

    /** True when [query] is blank — the filter is off and every list passes through untouched. */
    fun isBlank(query: String): Boolean = query.isBlank()

    /** Case-insensitive "does [haystack] contain the trimmed [query]" (blank query always matches). */
    private fun matches(haystack: String?, needle: String): Boolean {
        if (haystack.isNullOrEmpty()) return false
        return haystack.contains(needle, ignoreCase = true)
    }

    fun songs(items: List<Song>, query: String): List<Song> {
        if (query.isBlank()) return items
        val needle = query.trim()
        return items.filter { matches(it.title, needle) || matches(it.artistNames, needle) }
    }

    fun albums(items: List<Album>, query: String): List<Album> {
        if (query.isBlank()) return items
        val needle = query.trim()
        return items.filter { matches(it.name, needle) || matches(it.artistName, needle) }
    }

    fun artists(items: List<Artist>, query: String): List<Artist> {
        if (query.isBlank()) return items
        val needle = query.trim()
        return items.filter { matches(it.name, needle) }
    }

    fun playlists(items: List<Playlist>, query: String): List<Playlist> {
        if (query.isBlank()) return items
        val needle = query.trim()
        return items.filter { matches(it.name, needle) }
    }

    fun genres(items: List<Genre>, query: String): List<Genre> {
        if (query.isBlank()) return items
        val needle = query.trim()
        return items.filter { matches(it.name, needle) }
    }

    /** Filter the unified all-types feed by the same per-type rules its wrapped items use. */
    fun unified(items: List<LibraryFeedItem>, query: String): List<LibraryFeedItem> {
        if (query.isBlank()) return items
        val needle = query.trim()
        return items.filter { item ->
            when (item) {
                is LibraryFeedItem.SongItem ->
                    matches(item.song.title, needle) || matches(item.song.artistNames, needle)
                is LibraryFeedItem.AlbumItem ->
                    matches(item.album.name, needle) || matches(item.album.artistName, needle)
                is LibraryFeedItem.ArtistItem -> matches(item.artist.name, needle)
                is LibraryFeedItem.PlaylistItem -> matches(item.playlist.name, needle)
            }
        }
    }
}
