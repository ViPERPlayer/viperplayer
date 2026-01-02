package com.viperplayer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.viperplayer.data.local.entity.PlaylistEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Playlist operations.
 */
@Dao
interface PlaylistDao {
    
    @Query("SELECT * FROM playlists WHERE pluginId = :pluginId AND sourceId = :sourceId LIMIT 1")
    suspend fun getByMediaId(pluginId: String, sourceId: String): PlaylistEntity?
    
    @Query("SELECT * FROM playlists WHERE id = :id")
    fun getById(id: Long): Flow<PlaylistEntity?>
    
    @Query("SELECT * FROM playlists WHERE pluginId = :pluginId AND sourceId = :sourceId")
    fun getByMediaIdFlow(pluginId: String, sourceId: String): Flow<PlaylistEntity?>
    
    @Query("SELECT * FROM playlists WHERE isLiked = 1 ORDER BY name ASC")
    fun getAllLiked(): Flow<List<PlaylistEntity>>
    
    @Query("SELECT * FROM playlists WHERE isSaved = 1 ORDER BY name ASC")
    fun getAllSaved(): Flow<List<PlaylistEntity>>
    
    @Query("SELECT * FROM playlists WHERE isDownloaded = 1 ORDER BY name ASC")
    fun getAllDownloaded(): Flow<List<PlaylistEntity>>
    
    @Query("SELECT * FROM playlists ORDER BY name ASC")
    fun getAll(): Flow<List<PlaylistEntity>>
    
    @Query("SELECT * FROM playlists WHERE name LIKE :query ORDER BY name ASC")
    fun search(query: String): Flow<List<PlaylistEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(playlist: PlaylistEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(playlists: List<PlaylistEntity>)
    
    @Update
    suspend fun update(playlist: PlaylistEntity)
    
    @Query("UPDATE playlists SET isLiked = :isLiked WHERE pluginId = :pluginId AND sourceId = :sourceId")
    suspend fun updateLiked(pluginId: String, sourceId: String, isLiked: Boolean)
    
    @Query("UPDATE playlists SET isSaved = :isSaved WHERE pluginId = :pluginId AND sourceId = :sourceId")
    suspend fun updateSaved(pluginId: String, sourceId: String, isSaved: Boolean)
    
    @Query("UPDATE playlists SET isDownloaded = :isDownloaded WHERE pluginId = :pluginId AND sourceId = :sourceId")
    suspend fun updateDownloaded(pluginId: String, sourceId: String, isDownloaded: Boolean)
    
    @Query("DELETE FROM playlists WHERE pluginId = :pluginId AND sourceId = :sourceId")
    suspend fun delete(pluginId: String, sourceId: String)
    
    @Query("DELETE FROM playlists")
    suspend fun deleteAll()
}

