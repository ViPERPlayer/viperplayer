package com.viperplayer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.viperplayer.data.local.entity.SearchHistoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Search History operations.
 */
@Dao
interface SearchHistoryDao {
    
    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentHistory(limit: Int = 10): Flow<List<SearchHistoryEntity>>
    
    @Query("SELECT * FROM search_history WHERE query LIKE '%' || :query || '%' ORDER BY timestamp DESC LIMIT :limit")
    fun getHistoryContaining(query: String, limit: Int = 10): Flow<List<SearchHistoryEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: SearchHistoryEntity): Long
    
    @Query("DELETE FROM search_history WHERE query = :query")
    suspend fun deleteByQuery(query: String)
    
    @Query("DELETE FROM search_history WHERE id = :id")
    suspend fun deleteById(id: Long)
}

