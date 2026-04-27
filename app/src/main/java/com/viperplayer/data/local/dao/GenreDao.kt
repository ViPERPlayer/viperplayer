package com.viperplayer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.viperplayer.data.local.entity.GenreEntity

/**
 * DAO for Genre operations.
 */
@Dao
interface GenreDao {

    @Query("SELECT * FROM genres WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): GenreEntity?

    @Query("SELECT * FROM genres WHERE id = :id")
    suspend fun getById(id: Long): GenreEntity?

    @Query("SELECT * FROM genres WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<GenreEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(genre: GenreEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(genres: List<GenreEntity>): List<Long>

    @Query("SELECT id FROM genres WHERE name = :name")
    suspend fun getIdByName(name: String): Long?
}

