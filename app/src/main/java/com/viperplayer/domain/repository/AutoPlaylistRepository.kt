package com.viperplayer.domain.repository

import com.viperplayer.domain.autoplaylist.AutoPlaylistType
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.Song
import kotlinx.coroutines.flow.Flow

/**
 * Supplies the dynamic ("smart") auto-playlists, computed live from the library and the listening
 * play-history via the pure `AutoPlaylistGenerator`. All the data-gathering (library songs + stats
 * history) and the generator invocation live behind this repository; the Library / PlaylistDetail
 * ViewModels just collect its flows and the screens render them.
 */
interface AutoPlaylistRepository {

    /**
     * The auto-playlists to surface in the Library, as lightweight [Playlist] headers (name, artwork,
     * song count — no `songs` payload). Recomputes as the library / play-history change. Play-based
     * playlists are omitted while there is no play-history, and any playlist that would be empty is
     * dropped so the section never shows dead entries.
     */
    fun getAutoPlaylists(): Flow<List<Playlist>>

    /**
     * The fully-populated auto-playlist of [type] — the header plus its generated [Playlist.songs],
     * recomputed live. Backs the detail screen. Emits an empty-song playlist (never null) when there
     * is nothing to generate.
     */
    fun getAutoPlaylist(type: AutoPlaylistType): Flow<Playlist>

    /** The generated songs for [type] as a plain list (for playback: play-all / shuffle). */
    fun getAutoPlaylistSongs(type: AutoPlaylistType): Flow<List<Song>>
}
