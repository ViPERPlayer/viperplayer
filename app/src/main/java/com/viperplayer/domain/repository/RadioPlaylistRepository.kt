package com.viperplayer.domain.repository

import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.Playlist

/**
 * Supplies the virtual "Song radio" playlist (issue #7). Given a seed song's [MediaId] it builds the
 * radio queue — the seed followed by the plugin's related songs (deduplicated, seed first) — and wraps
 * it in a [Playlist] header so the standard `PlaylistDetail` screen renders it like any other playlist.
 *
 * All the plugin fetching and queue construction live behind this repository; the `PlaylistDetail`
 * ViewModel just asks for the playlist and the screen renders it (play-all / shuffle / tap-a-song).
 */
interface RadioPlaylistRepository {

    /**
     * The radio [Playlist] seeded by [seedId]: its header (a "Song radio" title and the seed's artwork)
     * plus the generated [Playlist.songs] (seed first, then related). Fetches the plugin's related songs
     * once; returns a seed-only radio when the lookup fails or yields nothing (never null).
     */
    suspend fun getRadioPlaylist(seedId: MediaId): Playlist
}
