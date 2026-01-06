package com.viperplayer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.viperplayer.data.local.entity.SongEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Song operations.
 */
@Dao
interface SongDao {
    
    @Query("SELECT * FROM songs WHERE pluginId = :pluginId AND sourceId = :sourceId LIMIT 1")
    suspend fun getByMediaId(pluginId: String, sourceId: String): SongEntity?
    
    @Query("SELECT * FROM songs WHERE id = :id")
    fun getById(id: Long): Flow<SongEntity?>
    
    @Query("SELECT * FROM songs WHERE id = :id LIMIT 1")
    suspend fun getByIdSync(id: Long): SongEntity?
    
    @Query("SELECT * FROM songs WHERE pluginId = :pluginId AND sourceId = :sourceId")
    fun getByMediaIdFlow(pluginId: String, sourceId: String): Flow<SongEntity?>
    
    @Query("SELECT * FROM songs WHERE albumId = :albumId ORDER BY discNumber ASC, trackNumber ASC")
    fun getByAlbum(albumId: Long): Flow<List<SongEntity>>
    
    @Query("SELECT * FROM songs WHERE isLiked = 1 ORDER BY title ASC")
    fun getAllLiked(): Flow<List<SongEntity>>
    
    @Query("SELECT * FROM songs WHERE isSaved = 1 ORDER BY title ASC")
    fun getAllSaved(): Flow<List<SongEntity>>
    
    @Query("SELECT * FROM songs WHERE isDownloaded = 1 ORDER BY title ASC")
    fun getAllDownloaded(): Flow<List<SongEntity>>
    
    @Query("SELECT * FROM songs ORDER BY title ASC")
    fun getAll(): Flow<List<SongEntity>>
    
    @Query("SELECT * FROM songs WHERE title LIKE :query ORDER BY title ASC")
    fun search(query: String): Flow<List<SongEntity>>
    
    @Query("SELECT * FROM songs ORDER BY playCount DESC, lastPlayed DESC LIMIT :limit")
    fun getMostPlayed(limit: Int = 50): Flow<List<SongEntity>>
    
    @Query("SELECT * FROM songs WHERE lastPlayed IS NOT NULL ORDER BY lastPlayed DESC LIMIT :limit")
    fun getRecentlyPlayed(limit: Int = 50): Flow<List<SongEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(song: SongEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(songs: List<SongEntity>)
    
    @Update
    suspend fun update(song: SongEntity)
    
    @Query("UPDATE songs SET isLiked = :isLiked WHERE pluginId = :pluginId AND sourceId = :sourceId")
    suspend fun updateLiked(pluginId: String, sourceId: String, isLiked: Boolean)
    
    @Query("UPDATE songs SET isSaved = :isSaved WHERE pluginId = :pluginId AND sourceId = :sourceId")
    suspend fun updateSaved(pluginId: String, sourceId: String, isSaved: Boolean)
    
    @Query("UPDATE songs SET isDownloaded = :isDownloaded, downloadPath = :downloadPath WHERE pluginId = :pluginId AND sourceId = :sourceId")
    suspend fun updateDownloaded(pluginId: String, sourceId: String, isDownloaded: Boolean, downloadPath: String? = null)
    
    @Query("UPDATE songs SET localArtworkPath = :localArtworkPath WHERE pluginId = :pluginId AND sourceId = :sourceId")
    suspend fun updateLocalArtworkPath(pluginId: String, sourceId: String, localArtworkPath: String?)
    
    @Query("UPDATE songs SET playCount = playCount + 1, lastPlayed = :timestamp WHERE pluginId = :pluginId AND sourceId = :sourceId")
    suspend fun incrementPlayCount(pluginId: String, sourceId: String, timestamp: Long = System.currentTimeMillis())
    
    @Query("DELETE FROM songs WHERE pluginId = :pluginId AND sourceId = :sourceId")
    suspend fun delete(pluginId: String, sourceId: String)
    
    @Query("DELETE FROM songs")
    suspend fun deleteAll()
    
    /**
     * Deletes songs that are not referenced anywhere (not liked, saved, downloaded, in playlists, or in queue).
     * This helps clean up orphaned songs.
     */
    @Query("""
        DELETE FROM songs 
        WHERE isLiked = 0 
        AND isSaved = 0 
        AND isDownloaded = 0
        AND id NOT IN (SELECT DISTINCT songId FROM playlist_songs)
        AND id NOT IN (SELECT DISTINCT songId FROM queue_songs)
    """)
    suspend fun deleteOrphanedSongs(): Int
}

