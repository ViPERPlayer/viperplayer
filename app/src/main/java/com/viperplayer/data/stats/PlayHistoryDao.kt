package com.viperplayer.data.stats

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO for the stats database's [PlayHistoryEntity] table. Reads stream ALL events (newest first) as a
 * [Flow]; the ViewModel hands them to the pure [com.viperplayer.domain.stats.ListeningStatsAggregator]
 * which does the ranking / windowing. Keeping the SQL "dumb" (no aggregation) is deliberate — all the
 * testable logic lives in the aggregator, not in queries.
 */
@Dao
interface PlayHistoryDao {

    @Insert
    suspend fun insert(event: PlayHistoryEntity): Long

    /** Every recorded play, newest first. */
    @Query("SELECT * FROM play_history ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<PlayHistoryEntity>>

    /** Number of recorded plays (drives the "clear history" affordance visibility). */
    @Query("SELECT COUNT(*) FROM play_history")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM play_history")
    suspend fun clearAll()
}
