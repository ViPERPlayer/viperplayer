package com.viperplayer.domain.stats

/**
 * A single, self-contained listening event — the pure input to [ListeningStatsAggregator].
 *
 * Deliberately Android-free and denormalized: everything the aggregator needs to rank and summarize
 * is carried on the record itself (no DB joins, no domain-model lookups). This is what makes the
 * aggregator trivially unit-testable — a test builds a `List<PlayRecord>` by hand and asserts on the
 * result.
 *
 * @param timestampMs epoch millis when the play was recorded (session end).
 * @param mediaId opaque unique id of the track (`pluginId=…&sourceId=…`). Groups "top songs".
 * @param title track title (display + song grouping key fallback).
 * @param artist primary/joined artist name(s). Groups "top artists". Blank ⇒ unknown.
 * @param album album name. Groups "top albums". Blank/null ⇒ excluded from album ranking.
 * @param pluginId source plugin id (e.g. "local", "testsource") — for a per-source breakdown.
 * @param listenedMs how long the track was ACTUALLY playing (excludes pauses/buffering). This is the
 *   "listening time" the summary sums. Falls back to [durationMs] only if a caller wants to.
 * @param durationMs full track duration in ms (0/null if unknown) — used for the play threshold.
 */
data class PlayRecord(
    val timestampMs: Long,
    val mediaId: String,
    val title: String,
    val artist: String,
    val album: String?,
    val pluginId: String,
    val listenedMs: Long,
    val durationMs: Long,
    val artworkUrl: String? = null,
)

/**
 * Selectable time window for the stats screen. Each range is resolved against a "now" timestamp into
 * an inclusive-start / exclusive-end millis window (see [ListeningStatsAggregator.rangeWindow]).
 */
enum class StatsRange {
    LAST_7_DAYS,
    LAST_4_WEEKS,
    LAST_6_MONTHS,
    LAST_YEAR,
    ALL_TIME,
}

/**
 * One entry in a "top X" ranking (top songs / artists / albums), already sorted and counted.
 *
 * @param key the grouping key (mediaId for songs, artist name for artists, album name for albums).
 * @param title primary display label.
 * @param subtitle secondary label (e.g. artist under a song title); null when not applicable.
 * @param playCount number of plays contributing to this entry within the range.
 * @param listenedMs total actual listening time (ms) contributing to this entry within the range.
 * @param artworkUrl representative artwork, if any.
 */
data class RankedItem(
    val key: String,
    val title: String,
    val subtitle: String?,
    val playCount: Int,
    val listenedMs: Long,
    val artworkUrl: String? = null,
)

/**
 * How plays distribute across a bucketing dimension (day-of-week or hour-of-day). The list is always
 * the full, fixed-size domain (7 for day-of-week, 24 for hour-of-day) so the UI can render a
 * complete bar chart with zero-height bars where there was no listening.
 *
 * @param labelIndex 0-based bucket index (day-of-week: 0=Monday … 6=Sunday; hour: 0..23).
 * @param playCount number of plays that fell into this bucket.
 */
data class ActivityBucket(
    val labelIndex: Int,
    val playCount: Int,
)

/**
 * The fully-aggregated result rendered by the stats screen for a chosen [StatsRange].
 */
data class ListeningStats(
    val range: StatsRange,
    val totalPlays: Int,
    val totalListenedMs: Long,
    val uniqueSongs: Int,
    val uniqueArtists: Int,
    val uniqueAlbums: Int,
    val topSongs: List<RankedItem>,
    val topArtists: List<RankedItem>,
    val topAlbums: List<RankedItem>,
    /** Always 7 buckets, Monday-first. */
    val byDayOfWeek: List<ActivityBucket>,
    /** Always 24 buckets, 0..23. */
    val byHourOfDay: List<ActivityBucket>,
) {
    val isEmpty: Boolean get() = totalPlays == 0

    companion object {
        /** A zeroed result (empty history / no plays in range) with the fixed-size bucket domains. */
        fun empty(range: StatsRange): ListeningStats = ListeningStats(
            range = range,
            totalPlays = 0,
            totalListenedMs = 0L,
            uniqueSongs = 0,
            uniqueArtists = 0,
            uniqueAlbums = 0,
            topSongs = emptyList(),
            topArtists = emptyList(),
            topAlbums = emptyList(),
            byDayOfWeek = (0 until 7).map { ActivityBucket(it, 0) },
            byHourOfDay = (0 until 24).map { ActivityBucket(it, 0) },
        )
    }
}

/**
 * The "Wrapped" (year in review) summary — the headline facts shown as a story/card sequence.
 *
 * @param year the calendar year the wrap covers.
 * @param hasData false when there were no plays in the year (the UI shows a friendly empty story).
 * @param totalMinutes total minutes listened in the year (rounded).
 * @param totalPlays number of plays in the year.
 * @param differentArtists number of distinct artists listened to in the year.
 * @param differentSongs number of distinct songs listened to in the year.
 * @param topSong / topArtist / topAlbum the #1 of the year (null when no data for that dimension).
 * @param topArtists the year's top artists (a short leaderboard for one of the cards).
 * @param topSongs the year's top songs (a short leaderboard for one of the cards).
 * @param busiestHour hour-of-day (0..23) with the most plays, or null when no data.
 * @param topDayLabelIndex day-of-week (0=Monday) with the most plays, or null when no data.
 */
data class WrappedSummary(
    val year: Int,
    val hasData: Boolean,
    val totalMinutes: Long,
    val totalPlays: Int,
    val differentArtists: Int,
    val differentSongs: Int,
    val topSong: RankedItem?,
    val topArtist: RankedItem?,
    val topAlbum: RankedItem?,
    val topArtists: List<RankedItem>,
    val topSongs: List<RankedItem>,
    val busiestHour: Int?,
    val topDayLabelIndex: Int?,
)
