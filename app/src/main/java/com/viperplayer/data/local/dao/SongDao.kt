package com.viperplayer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.viperplayer.data.local.entity.SongEntity
import com.viperplayer.data.local.entity.relation.SongEmbeddingRow
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Song operations.
 */
@Dao
interface SongDao {

    @Query("SELECT * FROM songs WHERE idType = :idType AND pluginId = :pluginId AND sourceId = :sourceId LIMIT 1")
    suspend fun getByMediaId(idType: String, pluginId: String, sourceId: String): SongEntity?

    @Query("SELECT * FROM songs WHERE id = :id")
    fun getById(id: Long): Flow<SongEntity?>

    @Query("SELECT * FROM songs WHERE id = :id LIMIT 1")
    suspend fun getByIdSync(id: Long): SongEntity?

    /**
     * The song rows for the given autoincrement [ids], in an unspecified order (SQLite `IN` does not
     * preserve argument order — the caller re-orders by [ids] if ranking matters). Feeds the local kNN
     * recommender, which resolves a batch of ranked embedding-row ids back to full rows in one query.
     */
    @Query("SELECT * FROM songs WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<SongEntity>

    @Query("SELECT * FROM songs WHERE idType = :idType AND pluginId = :pluginId AND sourceId = :sourceId")
    fun getByMediaIdFlow(idType: String, pluginId: String, sourceId: String): Flow<SongEntity?>

    @Query("SELECT * FROM songs WHERE albumId = :albumId ORDER BY discNumber ASC, trackNumber ASC")
    fun getByAlbum(albumId: Long): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE isLiked = 1 ORDER BY title ASC")
    fun getAllLiked(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE isSaved = 1 ORDER BY title ASC")
    fun getAllSaved(): Flow<List<SongEntity>>

    /**
     * Saved songs newest-added first. The autoincrement primary key is monotonically increasing with
     * insertion, so `id DESC` is a stable "date added" ordering (there is no dedicated added-at
     * column). Feeds the "Recently Added" auto-playlist.
     */
    @Query("SELECT * FROM songs WHERE isSaved = 1 ORDER BY id DESC")
    fun getAllSavedByDateAddedDesc(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE isDownloaded = 1 ORDER BY title ASC")
    fun getAllDownloaded(): Flow<List<SongEntity>>

    /** Live count of liked songs, for the Library "Liked" shortcut tile. */
    @Query("SELECT COUNT(*) FROM songs WHERE isLiked = 1")
    fun observeLikedCount(): Flow<Int>

    /** Live count of downloaded songs, for the Library "Downloads" shortcut tile. */
    @Query("SELECT COUNT(*) FROM songs WHERE isDownloaded = 1")
    fun observeDownloadedCount(): Flow<Int>

    @Query("SELECT * FROM songs ORDER BY title ASC")
    fun getAll(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE title LIKE :query ORDER BY title ASC")
    fun search(query: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs ORDER BY playCount DESC, lastPlayed DESC LIMIT :limit")
    fun getMostPlayed(limit: Int = 50): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE lastPlayed IS NOT NULL ORDER BY lastPlayed DESC LIMIT :limit")
    fun getRecentlyPlayed(limit: Int = 50): Flow<List<SongEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(song: SongEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(songs: List<SongEntity>)

    @Update
    suspend fun update(song: SongEntity)

    @Query("UPDATE songs SET isLiked = :isLiked WHERE idType = :idType AND pluginId = :pluginId AND sourceId = :sourceId")
    suspend fun updateLiked(idType: String, pluginId: String, sourceId: String, isLiked: Boolean)

    @Query("UPDATE songs SET isSaved = :isSaved WHERE idType = :idType AND pluginId = :pluginId AND sourceId = :sourceId")
    suspend fun updateSaved(idType: String, pluginId: String, sourceId: String, isSaved: Boolean)

    @Query("UPDATE songs SET isDownloaded = :isDownloaded, downloadPath = :downloadPath WHERE idType = :idType AND pluginId = :pluginId AND sourceId = :sourceId")
    suspend fun updateDownloaded(
        idType: String,
        pluginId: String,
        sourceId: String,
        isDownloaded: Boolean,
        downloadPath: String? = null
    )

    @Query("UPDATE songs SET localArtworkPath = :localArtworkPath WHERE idType = :idType AND pluginId = :pluginId AND sourceId = :sourceId")
    suspend fun updateLocalArtworkPath(
        idType: String,
        pluginId: String,
        sourceId: String,
        localArtworkPath: String?
    )

    @Query("UPDATE songs SET playCount = playCount + 1, lastPlayed = :timestamp WHERE idType = :idType AND pluginId = :pluginId AND sourceId = :sourceId")
    suspend fun incrementPlayCount(
        idType: String,
        pluginId: String,
        sourceId: String,
        timestamp: Long = System.currentTimeMillis()
    )

    // -------------------------------------------------------------------------------------------
    // On-device CLAP audio embeddings (recommender, v6). audioEmbedding is the 512-float32 LE BLOB
    // (2048 bytes); embeddingModelVersion is ClapModel.MODEL_VERSION; embeddingComputedAtMs is the
    // wall-clock ms it was computed. All three are set together (or all NULL when absent).
    // -------------------------------------------------------------------------------------------

    /** Store (or replace) the audio embedding for the song identified by (idType, pluginId, sourceId). */
    @Query(
        """
        UPDATE songs
        SET audioEmbedding = :embedding,
            embeddingModelVersion = :modelVersion,
            embeddingComputedAtMs = :computedAtMs
        WHERE idType = :idType AND pluginId = :pluginId AND sourceId = :sourceId
        """
    )
    suspend fun setEmbedding(
        idType: String,
        pluginId: String,
        sourceId: String,
        embedding: ByteArray?,
        modelVersion: String?,
        computedAtMs: Long?
    )

    /** Read just the stored embedding BLOB for a song (NULL if none computed). */
    @Query("SELECT audioEmbedding FROM songs WHERE idType = :idType AND pluginId = :pluginId AND sourceId = :sourceId LIMIT 1")
    suspend fun getEmbedding(idType: String, pluginId: String, sourceId: String): ByteArray?

    /**
     * Songs that still need an embedding computed: either they have none, or theirs was produced by a
     * different (stale) model version than [currentModelVersion]. Feeds the background indexer (later
     * wave). Ordered by id so paging is stable.
     */
    @Query(
        """
        SELECT * FROM songs
        WHERE audioEmbedding IS NULL
           OR embeddingModelVersion IS NULL
           OR embeddingModelVersion != :currentModelVersion
        ORDER BY id ASC
        LIMIT :limit
        """
    )
    suspend fun getSongsMissingEmbedding(currentModelVersion: String, limit: Int = 200): List<SongEntity>

    /**
     * As [getSongsMissingEmbedding] but with an [offset], so the indexer can page PAST songs it can
     * never embed (streaming-only rows never gain an embedding and would otherwise re-appear at the
     * head of every un-offset page, starving later local songs). The indexer advances the offset by the
     * count of skipped (un-embeddable) rows; embedded rows drop out of the result set on their own.
     */
    @Query(
        """
        SELECT * FROM songs
        WHERE audioEmbedding IS NULL
           OR embeddingModelVersion IS NULL
           OR embeddingModelVersion != :currentModelVersion
        ORDER BY id ASC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getSongsMissingEmbeddingPaged(
        currentModelVersion: String,
        limit: Int,
        offset: Int,
    ): List<SongEntity>

    /** Count of songs still needing an embedding for [currentModelVersion] (indexer progress UI later). */
    @Query(
        """
        SELECT COUNT(*) FROM songs
        WHERE audioEmbedding IS NULL
           OR embeddingModelVersion IS NULL
           OR embeddingModelVersion != :currentModelVersion
        """
    )
    fun countSongsMissingEmbedding(currentModelVersion: String): Flow<Int>

    // -------------------------------------------------------------------------------------------
    // Streaming-only partition of the missing-embedding set. A STREAMING-only song is a plugin-served
    // track (idType != 'local') that is NOT downloaded (no local bytes on disk). The local
    // [LibraryIndexWorker] embeds the complement (downloaded + local); [StreamingIndexWorker] embeds
    // these from their live stream. The two predicates partition the missing set with no overlap, so
    // the workers never double-embed. Ordered by id so paging is stable.
    // -------------------------------------------------------------------------------------------

    /**
     * Streaming-only songs still missing a current-version embedding, paged. As
     * [getSongsMissingEmbeddingPaged] but restricted to the EXACT complement of the local worker's
     * `hasLocalBytes` over plugin rows — `idType != 'local' AND NOT (isDownloaded AND downloadPath
     * non-blank)` — so a genuinely-downloaded/local row is the local worker's job while a plugin row
     * flagged downloaded but lacking a real path (no local bytes) still falls here rather than to neither.
     */
    @Query(
        """
        SELECT * FROM songs
        WHERE idType != 'local'
          AND (isDownloaded = 0 OR downloadPath IS NULL OR downloadPath = '')
          AND (audioEmbedding IS NULL
               OR embeddingModelVersion IS NULL
               OR embeddingModelVersion != :currentModelVersion)
        ORDER BY id ASC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getStreamingSongsMissingEmbeddingPaged(
        currentModelVersion: String,
        limit: Int,
        offset: Int,
    ): List<SongEntity>

    /** Count of STREAMING-only songs still needing an embedding for [currentModelVersion]. */
    @Query(
        """
        SELECT COUNT(*) FROM songs
        WHERE idType != 'local'
          AND (isDownloaded = 0 OR downloadPath IS NULL OR downloadPath = '')
          AND (audioEmbedding IS NULL
               OR embeddingModelVersion IS NULL
               OR embeddingModelVersion != :currentModelVersion)
        """
    )
    fun countStreamingSongsMissingEmbedding(currentModelVersion: String): Flow<Int>

    /**
     * All (id, embedding) pairs with a current-version embedding, for building the local kNN index.
     * Rows without an embedding or with a stale version are excluded, so every returned BLOB is a
     * 512-float32 LE vector produced by [currentModelVersion].
     */
    @Query(
        """
        SELECT id AS songId, audioEmbedding AS embedding FROM songs
        WHERE audioEmbedding IS NOT NULL AND embeddingModelVersion = :currentModelVersion
        """
    )
    suspend fun getAllEmbeddings(currentModelVersion: String): List<SongEmbeddingRow>

    @Query("DELETE FROM songs WHERE idType = :idType AND pluginId = :pluginId AND sourceId = :sourceId")
    suspend fun delete(idType: String, pluginId: String, sourceId: String)

    @Query("DELETE FROM songs")
    suspend fun deleteAll()

    /**
     * Deletes songs that are not referenced anywhere (not liked, saved, downloaded, in playlists, or in queue).
     * This helps clean up orphaned songs.
     */
    @Query(
        """
        DELETE FROM songs 
        WHERE isLiked = 0 
        AND isSaved = 0 
        AND isDownloaded = 0
        AND id NOT IN (SELECT DISTINCT songId FROM playlist_songs)
        AND id NOT IN (SELECT DISTINCT songId FROM queue_songs)
    """
    )
    suspend fun deleteOrphanedSongs(): Int
}

