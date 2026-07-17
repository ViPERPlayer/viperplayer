package com.viperplayer.data.librarysync

import kotlinx.serialization.Serializable

/**
 * JSON DTOs for the ViPER backend library-sync API (the `/library` routes; github.com/iscle/
 * viper-backend). The camelCase field names mirror the backend's Go structs exactly
 * (internal/library/store.go + internal/httpapi/library.go), so no `@SerialName` remapping is needed.
 *
 * Concurrency model: every mutation is coordinated by a per-user monotonic
 * [LibrarySnapshotDto.revision]. A playlist upsert carries the base revision the client last saw for
 * that playlist; the server rejects a stale base as a conflict ([UpsertPlaylistResponseDto.conflict]
 * = true) instead of clobbering a concurrent edit, so the client can pull-merge-retry.
 */

/** A track reference, portable across sessions via the plugin that resolves it. */
@Serializable
data class TrackRefDto(
    val pluginId: String,
    val sourceId: String,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val artworkUrl: String = "",
    val durationMs: Long = 0,
)

/** A named, ordered list of tracks with a server-assigned revision. */
@Serializable
data class PlaylistDto(
    val id: String,
    val name: String = "",
    val tracks: List<TrackRefDto> = emptyList(),
    val revision: Long = 0,
    val updatedAtMs: Long = 0,
)

/** `GET /library` response: the user's whole synced state plus the high-water revision. */
@Serializable
data class LibrarySnapshotDto(
    val playlists: List<PlaylistDto> = emptyList(),
    val likedTracks: List<TrackRefDto> = emptyList(),
    val revision: Long = 0,
)

/**
 * `PUT /library/playlists` request. [baseRevision] is the revision the client last saw for this
 * playlist; the server rejects the write as a conflict when it is older than the stored revision.
 */
@Serializable
data class UpsertPlaylistRequestDto(
    val playlist: PlaylistDto,
    val baseRevision: Long,
)

/**
 * `PUT /library/playlists` response. On success [playlist] carries its new server-assigned revision;
 * when [conflict] is true the write was rejected as stale and [playlist] is the current stored copy.
 */
@Serializable
data class UpsertPlaylistResponseDto(
    val playlist: PlaylistDto,
    val conflict: Boolean = false,
)

/** `POST /library/playlists/delete` request. */
@Serializable
data class DeletePlaylistRequestDto(val playlistId: String)

/** `POST /library/likes` request: set or unset a like on a track. */
@Serializable
data class SetLikeRequestDto(
    val track: TrackRefDto,
    val liked: Boolean,
)

/** `POST /library/playlists/delete` + `POST /library/likes` response: the new library revision. */
@Serializable
data class RevisionResponseDto(val revision: Long)
