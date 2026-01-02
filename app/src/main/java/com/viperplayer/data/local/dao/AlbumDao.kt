package com.viperplayer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.viperplayer.data.local.entity.AlbumEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Album operations.
 */
@Dao
interface AlbumDao {
    
    @Query("SELECT * FROM albums WHERE pluginId = :pluginId AND sourceId = :sourceId LIMIT 1")
    suspend fun getByMediaId(pluginId: String, sourceId: String): AlbumEntity?
    
    @Query("SELECT * FROM albums WHERE id = :id")
    fun getById(id: Long): Flow<AlbumEntity?>
    
    @Query("SELECT * FROM albums WHERE pluginId = :pluginId AND sourceId = :sourceId")
    fun getByMediaIdFlow(pluginId: String, sourceId: String): Flow<AlbumEntity?>
    
    @Query("SELECT * FROM albums WHERE isLiked = 1 ORDER BY name ASC")
    fun getAllLiked(): Flow<List<AlbumEntity>>
    
    @Query("SELECT * FROM albums WHERE isSaved = 1 ORDER BY name ASC")
    fun getAllSaved(): Flow<List<AlbumEntity>>
    
    @Query("SELECT * FROM albums WHERE isDownloaded = 1 ORDER BY name ASC")
    fun getAllDownloaded(): Flow<List<AlbumEntity>>
    
    @Query("SELECT * FROM albums ORDER BY name ASC")
    fun getAll(): Flow<List<AlbumEntity>>
    
    @Query("SELECT * FROM albums WHERE name LIKE :query ORDER BY name ASC")
    fun search(query: String): Flow<List<AlbumEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(album: AlbumEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(albums: List<AlbumEntity>)
    
    @Update
    suspend fun update(album: AlbumEntity)
    
    @Query("UPDATE albums SET isLiked = :isLiked WHERE pluginId = :pluginId AND sourceId = :sourceId")
    suspend fun updateLiked(pluginId: String, sourceId: String, isLiked: Boolean)
    
    @Query("UPDATE albums SET isSaved = :isSaved WHERE pluginId = :pluginId AND sourceId = :sourceId")
    suspend fun updateSaved(pluginId: String, sourceId: String, isSaved: Boolean)
    
    @Query("UPDATE albums SET isDownloaded = :isDownloaded WHERE pluginId = :pluginId AND sourceId = :sourceId")
    suspend fun updateDownloaded(pluginId: String, sourceId: String, isDownloaded: Boolean)
    
    @Query("DELETE FROM albums WHERE pluginId = :pluginId AND sourceId = :sourceId")
    suspend fun delete(pluginId: String, sourceId: String)
    
    @Query("DELETE FROM albums")
    suspend fun deleteAll()
}

