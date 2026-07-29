package com.viperplayer.data.repository

import com.viperplayer.data.local.dao.SongDao
import com.viperplayer.data.local.entity.SongEntity
import com.viperplayer.data.local.entity.relation.SongEmbeddingRow
import com.viperplayer.domain.model.MediaId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Test-only [SongDao] fake for [RecommendationRepositoryImplTest]: serves the embedding index, rows by
 * id, per-seed embeddings, and the liked/recent pools. Every other DAO method is unused by these tests
 * and throws if touched (a loud signal that a test reached for something it should have faked).
 */
class RecFakeSongDao(
    private val embeddings: List<SongEmbeddingRow>,
    private val entities: List<SongEntity>,
    /** Seed-embedding lookup by [MediaId] for [getEmbedding]. */
    private val seedEmbeddings: Map<MediaId, ByteArray>,
    private val liked: List<SongEntity> = emptyList(),
    private val recent: List<SongEntity> = emptyList(),
) : SongDao {

    override suspend fun getAllEmbeddings(currentModelVersion: String): List<SongEmbeddingRow> = embeddings

    override suspend fun getByIds(ids: List<Long>): List<SongEntity> =
        entities.filter { it.id in ids }

    override suspend fun getByMediaId(idType: String, pluginId: String, sourceId: String): SongEntity? =
        entities.firstOrNull { it.idType == idType && it.pluginId == pluginId && it.sourceId == sourceId }

    override suspend fun getEmbedding(idType: String, pluginId: String, sourceId: String): ByteArray? {
        val key = if (idType == "local") MediaId.Local(sourceId) else MediaId.Plugin(pluginId, sourceId)
        return seedEmbeddings[key]
    }

    override fun getAllLiked(): Flow<List<SongEntity>> = flowOf(liked)
    override fun getRecentlyPlayed(limit: Int): Flow<List<SongEntity>> = flowOf(recent)

    override fun countSongsMissingEmbedding(currentModelVersion: String): Flow<Int> = flowOf(0)

    // ---- Unused by these tests ----
    private fun nope(): Nothing = throw NotImplementedError("RecFakeSongDao: method not faked")

    override fun getById(id: Long): Flow<SongEntity?> = nope()
    override suspend fun getByIdSync(id: Long): SongEntity? = nope()
    override fun getByMediaIdFlow(idType: String, pluginId: String, sourceId: String): Flow<SongEntity?> = nope()
    override fun getByAlbum(albumId: Long): Flow<List<SongEntity>> = nope()
    override fun getAllSaved(): Flow<List<SongEntity>> = nope()
    override fun getAllSavedByDateAddedDesc(): Flow<List<SongEntity>> = nope()
    override fun getAllDownloaded(): Flow<List<SongEntity>> = nope()
    override fun observeLikedCount(): Flow<Int> = nope()
    override fun observeDownloadedCount(): Flow<Int> = nope()
    override fun getAll(): Flow<List<SongEntity>> = nope()
    override fun search(query: String): Flow<List<SongEntity>> = nope()
    override fun getMostPlayed(limit: Int): Flow<List<SongEntity>> = nope()
    override suspend fun insert(song: SongEntity): Long = nope()
    override suspend fun insertAll(songs: List<SongEntity>) = nope()
    override suspend fun update(song: SongEntity) = nope()
    override suspend fun updateLiked(idType: String, pluginId: String, sourceId: String, isLiked: Boolean) = nope()
    override suspend fun updateSaved(idType: String, pluginId: String, sourceId: String, isSaved: Boolean) = nope()
    override suspend fun updateDownloaded(idType: String, pluginId: String, sourceId: String, isDownloaded: Boolean, downloadPath: String?) = nope()
    override suspend fun updateLocalArtworkPath(idType: String, pluginId: String, sourceId: String, localArtworkPath: String?) = nope()
    override suspend fun incrementPlayCount(idType: String, pluginId: String, sourceId: String, timestamp: Long) = nope()
    override suspend fun setEmbedding(idType: String, pluginId: String, sourceId: String, embedding: ByteArray?, modelVersion: String?, computedAtMs: Long?) = nope()
    override suspend fun getSongsMissingEmbedding(currentModelVersion: String, limit: Int): List<SongEntity> = nope()
    override suspend fun getSongsMissingEmbeddingPaged(currentModelVersion: String, limit: Int, offset: Int): List<SongEntity> = nope()
    override suspend fun delete(idType: String, pluginId: String, sourceId: String) = nope()
    override suspend fun deleteAll() = nope()
    override suspend fun deleteOrphanedSongs(): Int = nope()
}
