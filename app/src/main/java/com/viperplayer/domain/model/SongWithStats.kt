package com.viperplayer.domain.model

/**
 * A song together with its play statistics over a selected [StatPeriod].
 * Pairs a song with its play statistics.
 */
data class SongWithStats(
    val song: Song,
    /** Number of plays within the selected period. */
    val playCount: Int,
    /** Total listening time within the period in milliseconds (playCount × song duration). */
    val timeListenedMs: Long
)

/** Aggregate totals for a [StatPeriod], shown in the Stats summary header. */
data class StatsSummary(
    val totalPlays: Int,
    val distinctSongs: Int,
    val totalTimeMs: Long
)

/** A single entry in the chronological listening history: a song and when it was played. */
data class HistoryEntry(
    val song: Song,
    /** Epoch millis when this play occurred. */
    val playedAt: Long
)
