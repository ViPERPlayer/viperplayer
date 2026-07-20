package com.viperplayer.presentation.library

import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.Song

/**
 * One entry in the Library's unified "all types" feed — a single interleaved list of songs, albums,
 * artists and playlists shown when no filter chip is selected. The wrapper keeps the concrete media
 * item so the screen can render each row with its natural affordances and route a tap to its natural
 * destination (a song plays; an album/artist/playlist navigates).
 *
 * [id] is the wrapped item's [MediaId], used as a stable, type-disambiguated LazyColumn key.
 */
sealed interface LibraryFeedItem {
    val id: MediaId

    data class SongItem(val song: Song) : LibraryFeedItem {
        override val id: MediaId get() = song.id
    }

    data class AlbumItem(val album: Album) : LibraryFeedItem {
        override val id: MediaId get() = album.id
    }

    data class ArtistItem(val artist: Artist) : LibraryFeedItem {
        override val id: MediaId get() = artist.id
    }

    data class PlaylistItem(val playlist: Playlist) : LibraryFeedItem {
        override val id: MediaId get() = playlist.id
    }
}

/**
 * A stable, type-disambiguated LazyColumn key for a [LibraryFeedItem]: a short type tag plus the
 * item's [MediaId] string. Two items of different types can never collide (the tag differs) and the
 * [MediaId] guarantees uniqueness within a type.
 */
val LibraryFeedItem.stableKey: String
    get() {
        val tag = when (this) {
            is LibraryFeedItem.SongItem -> "song"
            is LibraryFeedItem.AlbumItem -> "album"
            is LibraryFeedItem.ArtistItem -> "artist"
            is LibraryFeedItem.PlaylistItem -> "playlist"
        }
        return "$tag-$id"
    }
