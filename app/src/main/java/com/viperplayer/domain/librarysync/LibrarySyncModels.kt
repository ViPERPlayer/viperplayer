package com.viperplayer.domain.librarysync

/**
 * Domain models for the ViPER backend library-sync surface. Transport-agnostic — the data layer maps
 * the backend's JSON DTOs into these, so nothing above the repository sees an HTTP/JSON detail.
 */

/** A track reference, portable across sessions via the plugin that resolves it. */
data class SyncedTrack(
    val pluginId: String,
    val sourceId: String,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val artworkUrl: String = "",
    val durationMs: Long = 0,
)

/** A named, ordered list of tracks with a server-assigned [revision]. */
data class SyncedPlaylist(
    val id: String,
    val name: String = "",
    val tracks: List<SyncedTrack> = emptyList(),
    val revision: Long = 0,
    val updatedAtMs: Long = 0,
)

/** A whole synced library snapshot plus its high-water [revision]. */
data class LibrarySnapshot(
    val playlists: List<SyncedPlaylist> = emptyList(),
    val likedTracks: List<SyncedTrack> = emptyList(),
    val revision: Long = 0,
)

/**
 * The outcome of a playlist upsert. On success [playlist] carries its new server-assigned revision;
 * when [conflict] is true the write was rejected as stale (the caller's base revision was older than
 * the stored one) and [playlist] is the current stored copy to reconcile against.
 */
data class UpsertResult(
    val playlist: SyncedPlaylist,
    val conflict: Boolean,
)
