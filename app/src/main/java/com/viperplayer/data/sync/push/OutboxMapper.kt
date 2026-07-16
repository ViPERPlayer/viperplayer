package com.viperplayer.data.sync.push

import com.viperplayer.data.local.entity.OutboxEntity
import com.viperplayer.data.local.entity.OutboxType
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Bridges the pure [LibraryMutation] model and its durable [OutboxEntity] row: a stable [type]
 * discriminator plus a JSON payload, so adding a mutation kind never touches the DB schema. Kept out
 * of the pure package so [LibraryMutation]/[MutationCoalescer] stay serialization-free.
 */
object OutboxMapper {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun toEntity(mutation: LibraryMutation): OutboxEntity = when (mutation) {
        is LibraryMutation.SetLiked -> row(mutation.pluginId, OutboxType.SET_LIKED, LikedPayload(mutation.trackId, mutation.liked))
        is LibraryMutation.SetFollowed -> row(mutation.pluginId, OutboxType.SET_FOLLOWED, FollowedPayload(mutation.artistId, mutation.followed))
        is LibraryMutation.CreatePlaylist -> row(mutation.pluginId, OutboxType.CREATE_PLAYLIST, CreatePayload(mutation.localPlaylistId, mutation.name))
        is LibraryMutation.RenamePlaylist -> row(mutation.pluginId, OutboxType.RENAME_PLAYLIST, RenamePayload(mutation.playlistId, mutation.name))
        is LibraryMutation.DeletePlaylist -> row(mutation.pluginId, OutboxType.DELETE_PLAYLIST, DeletePayload(mutation.playlistId))
        is LibraryMutation.AddTrackToPlaylist -> row(mutation.pluginId, OutboxType.ADD_TRACK, TrackPayload(mutation.playlistId, mutation.trackId))
        is LibraryMutation.RemoveTrackFromPlaylist -> row(mutation.pluginId, OutboxType.REMOVE_TRACK, TrackPayload(mutation.playlistId, mutation.trackId))
    }

    /** Decode a stored row back to a mutation, or null if the row is corrupt/unknown (skipped safely). */
    fun toMutation(entity: OutboxEntity): LibraryMutation? = try {
        when (entity.type) {
            OutboxType.SET_LIKED -> json.decodeFromString<LikedPayload>(entity.payloadJson)
                .let { LibraryMutation.SetLiked(entity.pluginId, it.trackId, it.liked) }
            OutboxType.SET_FOLLOWED -> json.decodeFromString<FollowedPayload>(entity.payloadJson)
                .let { LibraryMutation.SetFollowed(entity.pluginId, it.artistId, it.followed) }
            OutboxType.CREATE_PLAYLIST -> json.decodeFromString<CreatePayload>(entity.payloadJson)
                .let { LibraryMutation.CreatePlaylist(entity.pluginId, it.localPlaylistId, it.name) }
            OutboxType.RENAME_PLAYLIST -> json.decodeFromString<RenamePayload>(entity.payloadJson)
                .let { LibraryMutation.RenamePlaylist(entity.pluginId, it.playlistId, it.name) }
            OutboxType.DELETE_PLAYLIST -> json.decodeFromString<DeletePayload>(entity.payloadJson)
                .let { LibraryMutation.DeletePlaylist(entity.pluginId, it.playlistId) }
            OutboxType.ADD_TRACK -> json.decodeFromString<TrackPayload>(entity.payloadJson)
                .let { LibraryMutation.AddTrackToPlaylist(entity.pluginId, it.playlistId, it.trackId) }
            OutboxType.REMOVE_TRACK -> json.decodeFromString<TrackPayload>(entity.payloadJson)
                .let { LibraryMutation.RemoveTrackFromPlaylist(entity.pluginId, it.playlistId, it.trackId) }
            else -> null
        }
    } catch (e: SerializationException) {
        null
    }

    /** Map a stored row to a pure [OutboxEntry] (id becomes the coalescer seq), or null if corrupt. */
    fun toEntry(entity: OutboxEntity): OutboxEntry? =
        toMutation(entity)?.let { OutboxEntry(seq = entity.id, mutation = it) }

    private inline fun <reified T> row(pluginId: String, type: String, payload: T): OutboxEntity =
        OutboxEntity(pluginId = pluginId, type = type, payloadJson = json.encodeToString(payload))

    @Serializable private data class LikedPayload(val trackId: String, val liked: Boolean)
    @Serializable private data class FollowedPayload(val artistId: String, val followed: Boolean)
    @Serializable private data class CreatePayload(val localPlaylistId: String, val name: String)
    @Serializable private data class RenamePayload(val playlistId: String, val name: String)
    @Serializable private data class DeletePayload(val playlistId: String)
    @Serializable private data class TrackPayload(val playlistId: String, val trackId: String)
}
