package com.viperplayer.data.lastfm

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * DAO for the offline scrobble queue. Kept "dumb": rows come out oldest-first and the
 * dedupe/batching decisions live in the pure [com.viperplayer.domain.lastfm.PendingScrobbleQueue].
 * [insert] uses `OR IGNORE` against the unique (artist, track, timestamp) index so a duplicate is
 * silently dropped.
 */
@Dao
interface PendingScrobbleDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: PendingScrobbleEntity): Long

    /** All queued scrobbles, oldest first — the order they must be submitted in. */
    @Query("SELECT * FROM pending_scrobbles ORDER BY timestampSeconds ASC")
    suspend fun getAll(): List<PendingScrobbleEntity>

    @Query("SELECT COUNT(*) FROM pending_scrobbles")
    suspend fun count(): Int

    @Delete
    suspend fun delete(entities: List<PendingScrobbleEntity>)

    @Query("DELETE FROM pending_scrobbles")
    suspend fun clearAll()
}
