package com.viperplayer.data.librarysync

import com.viperplayer.domain.librarysync.LibrarySnapshot
import com.viperplayer.domain.librarysync.SyncedPlaylist
import com.viperplayer.domain.librarysync.SyncedTrack

/**
 * Pure DTO ↔ domain conversions for the ViPER backend library-sync surface. Kept separate from the
 * transport ([LibraryApi]) and repository so the field-by-field mapping is trivially unit-testable
 * and the repository stays focused on the auth/error seam.
 */

internal fun TrackRefDto.toDomain(): SyncedTrack = SyncedTrack(
    pluginId = pluginId,
    sourceId = sourceId,
    title = title,
    artist = artist,
    album = album,
    artworkUrl = artworkUrl,
    durationMs = durationMs,
)

internal fun SyncedTrack.toDto(): TrackRefDto = TrackRefDto(
    pluginId = pluginId,
    sourceId = sourceId,
    title = title,
    artist = artist,
    album = album,
    artworkUrl = artworkUrl,
    durationMs = durationMs,
)

internal fun PlaylistDto.toDomain(): SyncedPlaylist = SyncedPlaylist(
    id = id,
    name = name,
    tracks = tracks.map { it.toDomain() },
    revision = revision,
    updatedAtMs = updatedAtMs,
)

internal fun SyncedPlaylist.toDto(): PlaylistDto = PlaylistDto(
    id = id,
    name = name,
    tracks = tracks.map { it.toDto() },
    revision = revision,
    updatedAtMs = updatedAtMs,
)

internal fun LibrarySnapshotDto.toDomain(): LibrarySnapshot = LibrarySnapshot(
    playlists = playlists.map { it.toDomain() },
    likedTracks = likedTracks.map { it.toDomain() },
    revision = revision,
)
