package com.viperplayer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.viperplayer.data.local.entity.OutboxEntity
import kotlinx.coroutines.flow.Flow

/** Room access to the library-push outbox. Ordered by [OutboxEntity.id] so drains are FIFO. */
@Dao
interface OutboxDao {

    @Insert
    suspend fun insert(entry: OutboxEntity): Long

    /** All still-pending entries for a plugin, oldest first — the drain input. */
    @Query("SELECT * FROM library_push_outbox WHERE pluginId = :pluginId AND status = 'pending' ORDER BY id ASC")
    suspend fun pendingFor(pluginId: String): List<OutboxEntity>

    /** All pending entries across every plugin, oldest first. */
    @Query("SELECT * FROM library_push_outbox WHERE status = 'pending' ORDER BY id ASC")
    suspend fun allPending(): List<OutboxEntity>

    /** Distinct plugin ids that currently have pending entries. */
    @Query("SELECT DISTINCT pluginId FROM library_push_outbox WHERE status = 'pending'")
    suspend fun pluginsWithPending(): List<String>

    /** Live count of pending entries (for a "N changes queued" UI). */
    @Query("SELECT COUNT(*) FROM library_push_outbox WHERE status = 'pending'")
    fun observePendingCount(): Flow<Int>

    @Query("DELETE FROM library_push_outbox WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM library_push_outbox WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("UPDATE library_push_outbox SET attempts = :attempts WHERE id = :id")
    suspend fun updateAttempts(id: Long, attempts: Int)

    @Query("UPDATE library_push_outbox SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    /** Re-arm skipped entries for a plugin (e.g. after it updates to support writes). */
    @Query("UPDATE library_push_outbox SET status = 'pending', attempts = 0 WHERE pluginId = :pluginId AND status = 'skipped'")
    suspend fun rearmSkipped(pluginId: String)

    @Query("DELETE FROM library_push_outbox WHERE pluginId = :pluginId")
    suspend fun deleteForPlugin(pluginId: String)
}
