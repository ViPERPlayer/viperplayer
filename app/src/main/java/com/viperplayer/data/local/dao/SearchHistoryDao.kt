package com.viperplayer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.viperplayer.data.local.entity.SearchHistoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Search History operations.
 *
 * Dedupe and cap enforcement are done here (SQLite `NOCASE`), so "Beatles" and "beatles" collapse
 * to a single most-recent entry and the table never grows past [com.viperplayer.domain.search.SearchHistoryLogic.MAX_HISTORY].
 */
@Dao
interface SearchHistoryDao {

    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentHistory(limit: Int = 10): Flow<List<SearchHistoryEntity>>

    @Query("SELECT * FROM search_history WHERE query LIKE '%' || :query || '%' ORDER BY timestamp DESC LIMIT :limit")
    fun getHistoryContaining(query: String, limit: Int = 10): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: SearchHistoryEntity): Long

    /**
     * Removes any row that matches [query] case-insensitively. Used both to dedupe before a
     * re-search (via [upsertCapped]) and to delete a single entry the user swiped away.
     */
    @Query("DELETE FROM search_history WHERE query = :query COLLATE NOCASE")
    suspend fun deleteMatching(query: String)

    /**
     * Trims the table down to the [cap] most recent entries, dropping the oldest beyond it.
     */
    @Query(
        """
        DELETE FROM search_history WHERE id NOT IN (
            SELECT id FROM search_history ORDER BY timestamp DESC LIMIT :cap
        )
        """
    )
    suspend fun trimToCap(cap: Int)

    /**
     * Upserts [entity] with case-insensitive dedupe and cap enforcement: an existing entry (any
     * case) is removed first so the new spelling + timestamp wins and moves to the front, then the
     * table is trimmed to [cap].
     */
    @Transaction
    suspend fun upsertCapped(entity: SearchHistoryEntity, cap: Int) {
        deleteMatching(entity.query)
        insert(entity)
        trimToCap(cap)
    }

    @Query("DELETE FROM search_history")
    suspend fun clearAll()
}

