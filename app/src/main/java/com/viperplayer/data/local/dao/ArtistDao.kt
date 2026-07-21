package com.viperplayer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.viperplayer.data.local.entity.ArtistEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Artist operations.
 */
@Dao
interface ArtistDao {

    @Query("SELECT * FROM artists WHERE idType = :idType AND pluginId = :pluginId AND sourceId = :sourceId LIMIT 1")
    suspend fun getByMediaId(idType: String, pluginId: String, sourceId: String): ArtistEntity?

    /** Find an unlinked (null-sourceId) byline artist by name, for dedup on insert. */
    @Query("SELECT * FROM artists WHERE idType = :idType AND pluginId = :pluginId AND sourceId IS NULL AND name = :name LIMIT 1")
    suspend fun getUnlinkedByName(idType: String, pluginId: String, name: String): ArtistEntity?

    @Query("SELECT * FROM artists WHERE id = :id")
    fun getById(id: Long): Flow<ArtistEntity?>

    @Query("SELECT * FROM artists WHERE id = :id LIMIT 1")
    suspend fun getByIdSync(id: Long): ArtistEntity?

    @Query("SELECT * FROM artists WHERE idType = :idType AND pluginId = :pluginId AND sourceId = :sourceId")
    fun getByMediaIdFlow(idType: String, pluginId: String, sourceId: String): Flow<ArtistEntity?>

    @Query("SELECT * FROM artists WHERE isLiked = 1 ORDER BY name ASC")
    fun getAllLiked(): Flow<List<ArtistEntity>>

    @Query("SELECT * FROM artists WHERE isSaved = 1 ORDER BY name ASC")
    fun getAllSaved(): Flow<List<ArtistEntity>>

    @Query("SELECT * FROM artists ORDER BY name ASC")
    fun getAll(): Flow<List<ArtistEntity>>

    @Query("SELECT * FROM artists WHERE name LIKE :query ORDER BY name ASC")
    fun search(query: String): Flow<List<ArtistEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(artist: ArtistEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(artists: List<ArtistEntity>)

    @Update
    suspend fun update(artist: ArtistEntity)

    @Query("UPDATE artists SET isLiked = :isLiked WHERE idType = :idType AND pluginId = :pluginId AND sourceId = :sourceId")
    suspend fun updateLiked(idType: String, pluginId: String, sourceId: String, isLiked: Boolean)

    @Query("UPDATE artists SET isSaved = :isSaved WHERE idType = :idType AND pluginId = :pluginId AND sourceId = :sourceId")
    suspend fun updateSaved(idType: String, pluginId: String, sourceId: String, isSaved: Boolean)

    @Query("DELETE FROM artists WHERE idType = :idType AND pluginId = :pluginId AND sourceId = :sourceId")
    suspend fun delete(idType: String, pluginId: String, sourceId: String)

    @Query("DELETE FROM artists")
    suspend fun deleteAll()
}

