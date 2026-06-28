package com.viperplayer.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import com.viperplayer.data.local.entity.PlayEventEntity
import com.viperplayer.data.local.entity.SongEntity
import kotlinx.coroutines.flow.Flow

/**
 * A song row joined with its aggregated play statistics over a time window.
 *
 * [eventCount] and [timeListenedMs] are extra projected columns; the rest of [SongEntity] comes
 * from `songs.*`. Aliases deliberately avoid the names of existing [SongEntity] columns
 * (note `playCount`/`durationMs` already exist) so Room's @Embedded mapping doesn't collide.
 */
data class SongWithPlayStats(
    @Embedded val song: SongEntity,
    /** Number of plays of this song inside the queried window. */
    val eventCount: Long,
    /** Total listening time inside the window: sum of measured per-play durations (full-duration fallback). */
    val timeListenedMs: Long
)

/** A song row joined with the timestamp of one specific play (one row per play event). */
data class PlayEventWithSong(
    @Embedded val song: SongEntity,
    val eventTimestamp: Long
)

/** Aggregate totals for a time window, used by the Stats summary header. */
data class PlayStatsSummary(
    val totalPlays: Int,
    val distinctSongs: Int,
    val totalTimeMs: Long
)

/**
 * DAO for [PlayEventEntity] — powers History (chronological timeline) and Stats (time-windowed
 * "most played"). Time windows are passed as epoch-millis bounds; pass `fromTimestamp = 0` and a
 * far-future `toTimestamp` for "all time".
 */
@Dao
interface PlayEventDao {

    @Insert
    suspend fun insert(event: PlayEventEntity): Long

    /**
     * Most played songs within [fromTimestamp]..[toTimestamp] (inclusive), ranked by play count
     * then by listening time. Listening time sums each play's actual measured listened duration
     * ([PlayEventEntity.durationListenedMs], reported by the player), falling back to the song's full
     * duration for plays not yet measured (e.g. in-flight, or interrupted before the player reported).
     * Each returned row carries the full song plus its per-window play count and time.
     */
    @Query(
        """
        SELECT songs.*,
               COUNT(play_events.id) AS eventCount,
               SUM(COALESCE(play_events.durationListenedMs, songs.durationMs, 0)) AS timeListenedMs
        FROM play_events
        INNER JOIN songs ON songs.id = play_events.songId
        WHERE play_events.timestamp BETWEEN :fromTimestamp AND :toTimestamp
        GROUP BY songs.id
        ORDER BY eventCount DESC, timeListenedMs DESC
        LIMIT :limit
        """
    )
    fun mostPlayedSongs(
        fromTimestamp: Long,
        toTimestamp: Long,
        limit: Int
    ): Flow<List<SongWithPlayStats>>

    /**
     * Aggregate totals for the window: total plays, distinct songs, and total listening time
     * (sum of each play's actual measured listened duration, falling back to the song's full
     * duration for plays not yet measured). Always returns exactly one row (zeros when empty).
     */
    @Query(
        """
        SELECT COUNT(play_events.id) AS totalPlays,
               COUNT(DISTINCT play_events.songId) AS distinctSongs,
               COALESCE(SUM(COALESCE(play_events.durationListenedMs, songs.durationMs, 0)), 0) AS totalTimeMs
        FROM play_events
        INNER JOIN songs ON songs.id = play_events.songId
        WHERE play_events.timestamp BETWEEN :fromTimestamp AND :toTimestamp
        """
    )
    fun statsSummary(fromTimestamp: Long, toTimestamp: Long): Flow<PlayStatsSummary>

    /**
     * Chronological listening history (one row per play), newest first, capped to [limit].
     */
    @Query(
        """
        SELECT songs.*, play_events.timestamp AS eventTimestamp
        FROM play_events
        INNER JOIN songs ON songs.id = play_events.songId
        ORDER BY play_events.timestamp DESC
        LIMIT :limit
        """
    )
    fun recentHistory(limit: Int): Flow<List<PlayEventWithSong>>

    /**
     * Backfill the actual listened duration onto the most recent not-yet-measured play of [songId].
     * Called when the player reports a finished playback session: a play is inserted at track start
     * with a null duration, and this fills in the real time once it's known. Targeting the latest
     * null row correctly matches the session that just ended (sessions complete in order).
     */
    @Query(
        """
        UPDATE play_events
        SET durationListenedMs = :durationListenedMs
        WHERE id = (
            SELECT id FROM play_events
            WHERE songId = :songId AND durationListenedMs IS NULL
            ORDER BY timestamp DESC
            LIMIT 1
        )
        """
    )
    suspend fun recordListenedTimeForLatest(songId: Long, durationListenedMs: Long)

    /** Epoch millis of the very first recorded play, or null if there is no history yet. */
    @Query("SELECT MIN(timestamp) FROM play_events")
    suspend fun firstEventTimestamp(): Long?

    /** Wipe the entire listening history. */
    @Query("DELETE FROM play_events")
    suspend fun clearAll()

    /** Delete history at or after [fromTimestamp] (e.g. "clear last week"). */
    @Query("DELETE FROM play_events WHERE timestamp >= :fromTimestamp")
    suspend fun clearSince(fromTimestamp: Long)
}
