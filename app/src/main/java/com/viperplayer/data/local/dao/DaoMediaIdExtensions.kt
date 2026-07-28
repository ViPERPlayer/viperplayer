package com.viperplayer.data.local.dao

import com.viperplayer.data.local.entity.AlbumEntity
import com.viperplayer.data.local.entity.ArtistEntity
import com.viperplayer.data.local.entity.PlaylistEntity
import com.viperplayer.data.local.entity.SongEntity
import com.viperplayer.data.local.mapper.entityPluginId
import com.viperplayer.data.local.mapper.idType
import com.viperplayer.domain.model.MediaId
import kotlinx.coroutines.flow.Flow

/**
 * [MediaId]-typed convenience overloads over the raw column-based DAO methods. They decompose a
 * [MediaId] into its persisted (idType, pluginId, sourceId) columns so callers stay in terms of the
 * domain identity rather than the storage layout. The reserved `local` idType keeps its own namespace,
 * so synthetic ids can never collide with a real plugin id.
 */

// Songs
suspend fun SongDao.getByMediaId(id: MediaId): SongEntity? =
    getByMediaId(id.idType, id.entityPluginId, id.sourceId)

fun SongDao.getByMediaIdFlow(id: MediaId): Flow<SongEntity?> =
    getByMediaIdFlow(id.idType, id.entityPluginId, id.sourceId)

suspend fun SongDao.updateLiked(id: MediaId, isLiked: Boolean) =
    updateLiked(id.idType, id.entityPluginId, id.sourceId, isLiked)

suspend fun SongDao.updateSaved(id: MediaId, isSaved: Boolean) =
    updateSaved(id.idType, id.entityPluginId, id.sourceId, isSaved)

suspend fun SongDao.updateDownloaded(id: MediaId, isDownloaded: Boolean, downloadPath: String? = null) =
    updateDownloaded(id.idType, id.entityPluginId, id.sourceId, isDownloaded, downloadPath)

suspend fun SongDao.updateLocalArtworkPath(id: MediaId, localArtworkPath: String?) =
    updateLocalArtworkPath(id.idType, id.entityPluginId, id.sourceId, localArtworkPath)

suspend fun SongDao.incrementPlayCount(id: MediaId) =
    incrementPlayCount(id.idType, id.entityPluginId, id.sourceId)

suspend fun SongDao.setEmbedding(
    id: MediaId,
    embedding: ByteArray?,
    modelVersion: String?,
    computedAtMs: Long?
) = setEmbedding(id.idType, id.entityPluginId, id.sourceId, embedding, modelVersion, computedAtMs)

suspend fun SongDao.getEmbedding(id: MediaId): ByteArray? =
    getEmbedding(id.idType, id.entityPluginId, id.sourceId)

suspend fun SongDao.delete(id: MediaId) =
    delete(id.idType, id.entityPluginId, id.sourceId)

// Albums
suspend fun AlbumDao.getByMediaId(id: MediaId): AlbumEntity? =
    getByMediaId(id.idType, id.entityPluginId, id.sourceId)

fun AlbumDao.getByMediaIdFlow(id: MediaId): Flow<AlbumEntity?> =
    getByMediaIdFlow(id.idType, id.entityPluginId, id.sourceId)

suspend fun AlbumDao.updateLiked(id: MediaId, isLiked: Boolean) =
    updateLiked(id.idType, id.entityPluginId, id.sourceId, isLiked)

suspend fun AlbumDao.updateSaved(id: MediaId, isSaved: Boolean) =
    updateSaved(id.idType, id.entityPluginId, id.sourceId, isSaved)

suspend fun AlbumDao.updateDownloaded(id: MediaId, isDownloaded: Boolean) =
    updateDownloaded(id.idType, id.entityPluginId, id.sourceId, isDownloaded)

suspend fun AlbumDao.delete(id: MediaId) =
    delete(id.idType, id.entityPluginId, id.sourceId)

// Artists
suspend fun ArtistDao.getByMediaId(id: MediaId): ArtistEntity? =
    getByMediaId(id.idType, id.entityPluginId, id.sourceId)

fun ArtistDao.getByMediaIdFlow(id: MediaId): Flow<ArtistEntity?> =
    getByMediaIdFlow(id.idType, id.entityPluginId, id.sourceId)

suspend fun ArtistDao.getUnlinkedByName(id: MediaId, name: String): ArtistEntity? =
    getUnlinkedByName(id.idType, id.entityPluginId, name)

suspend fun ArtistDao.updateLiked(id: MediaId, isLiked: Boolean) =
    updateLiked(id.idType, id.entityPluginId, id.sourceId, isLiked)

suspend fun ArtistDao.updateSaved(id: MediaId, isSaved: Boolean) =
    updateSaved(id.idType, id.entityPluginId, id.sourceId, isSaved)

suspend fun ArtistDao.delete(id: MediaId) =
    delete(id.idType, id.entityPluginId, id.sourceId)

// Playlists
suspend fun PlaylistDao.getByMediaId(id: MediaId): PlaylistEntity? =
    getByMediaId(id.idType, id.entityPluginId, id.sourceId)

fun PlaylistDao.getByMediaIdFlow(id: MediaId): Flow<PlaylistEntity?> =
    getByMediaIdFlow(id.idType, id.entityPluginId, id.sourceId)

suspend fun PlaylistDao.updateName(id: MediaId, name: String) =
    updateName(id.idType, id.entityPluginId, id.sourceId, name)

suspend fun PlaylistDao.updateLiked(id: MediaId, isLiked: Boolean) =
    updateLiked(id.idType, id.entityPluginId, id.sourceId, isLiked)

suspend fun PlaylistDao.updateSaved(id: MediaId, isSaved: Boolean) =
    updateSaved(id.idType, id.entityPluginId, id.sourceId, isSaved)

suspend fun PlaylistDao.updateDownloaded(id: MediaId, isDownloaded: Boolean) =
    updateDownloaded(id.idType, id.entityPluginId, id.sourceId, isDownloaded)

suspend fun PlaylistDao.delete(id: MediaId) =
    delete(id.idType, id.entityPluginId, id.sourceId)
