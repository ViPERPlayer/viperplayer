package com.viperplayer.data.rec

import com.viperplayer.data.local.dao.SongDao
import com.viperplayer.data.local.entity.SongEntity
import com.viperplayer.data.local.entity.relation.SongEmbeddingRow
import kotlinx.coroutines.flow.Flow

/**
 * Test-only [SongDao] fake for the embedder round-trip tests: records [setEmbedding] writes in-memory
 * and serves them back via [getEmbedding]. Every other DAO method is unused by these tests and throws
 * if touched (a loud signal that a test reached for something it should have faked). Lives in `test/`.
 */
class RecordingSongDao : SongDao {

    data class StoredEmbedding(
        val idType: String,
        val pluginId: String,
        val sourceId: String,
        val embedding: ByteArray?,
        val modelVersion: String?,
        val computedAtMs: Long?,
    )

    val stored = mutableListOf<StoredEmbedding>()

    override suspend fun setEmbedding(
        idType: String,
        pluginId: String,
        sourceId: String,
        embedding: ByteArray?,
        modelVersion: String?,
        computedAtMs: Long?,
    ) {
        stored.add(StoredEmbedding(idType, pluginId, sourceId, embedding, modelVersion, computedAtMs))
    }

    override suspend fun getEmbedding(idType: String, pluginId: String, sourceId: String): ByteArray? =
        stored.lastOrNull { it.idType == idType && it.pluginId == pluginId && it.sourceId == sourceId }?.embedding

    // ---- Unused by these tests ----
    private fun nope(): Nothing = throw NotImplementedError("RecordingSongDao: method not faked")

    override suspend fun getByMediaId(idType: String, pluginId: String, sourceId: String): SongEntity? = nope()
    override fun getById(id: Long): Flow<SongEntity?> = nope()
    override suspend fun getByIdSync(id: Long): SongEntity? = nope()
    override fun getByMediaIdFlow(idType: String, pluginId: String, sourceId: String): Flow<SongEntity?> = nope()
    override fun getByAlbum(albumId: Long): Flow<List<SongEntity>> = nope()
    override fun getAllLiked(): Flow<List<SongEntity>> = nope()
    override fun getAllSaved(): Flow<List<SongEntity>> = nope()
    override fun getAllSavedByDateAddedDesc(): Flow<List<SongEntity>> = nope()
    override fun getAllDownloaded(): Flow<List<SongEntity>> = nope()
    override fun observeLikedCount(): Flow<Int> = nope()
    override fun observeDownloadedCount(): Flow<Int> = nope()
    override fun getAll(): Flow<List<SongEntity>> = nope()
    override fun search(query: String): Flow<List<SongEntity>> = nope()
    override fun getMostPlayed(limit: Int): Flow<List<SongEntity>> = nope()
    override fun getRecentlyPlayed(limit: Int): Flow<List<SongEntity>> = nope()
    override suspend fun insert(song: SongEntity): Long = nope()
    override suspend fun insertAll(songs: List<SongEntity>) = nope()
    override suspend fun update(song: SongEntity) = nope()
    override suspend fun updateLiked(idType: String, pluginId: String, sourceId: String, isLiked: Boolean) = nope()
    override suspend fun updateSaved(idType: String, pluginId: String, sourceId: String, isSaved: Boolean) = nope()
    override suspend fun updateDownloaded(idType: String, pluginId: String, sourceId: String, isDownloaded: Boolean, downloadPath: String?) = nope()
    override suspend fun updateLocalArtworkPath(idType: String, pluginId: String, sourceId: String, localArtworkPath: String?) = nope()
    override suspend fun incrementPlayCount(idType: String, pluginId: String, sourceId: String, timestamp: Long) = nope()
    override suspend fun getSongsMissingEmbedding(currentModelVersion: String, limit: Int): List<SongEntity> = nope()
    override suspend fun getSongsMissingEmbeddingPaged(currentModelVersion: String, limit: Int, offset: Int): List<SongEntity> = nope()
    override fun countSongsMissingEmbedding(currentModelVersion: String): Flow<Int> = nope()
    override suspend fun getAllEmbeddings(currentModelVersion: String): List<SongEmbeddingRow> = nope()
    override suspend fun delete(idType: String, pluginId: String, sourceId: String) = nope()
    override suspend fun deleteAll() = nope()
    override suspend fun deleteOrphanedSongs(): Int = nope()
}
