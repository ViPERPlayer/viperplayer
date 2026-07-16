package com.viperplayer.domain.stats

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * Pure, Android-free aggregation of listening [PlayRecord]s into [ListeningStats] and a
 * [WrappedSummary]. This is the unit-test target: every method is deterministic and side-effect
 * free, taking the raw events (+ a range / "now" / zone) and returning summarized results.
 *
 * Design notes:
 * - **Range windows are [start, end)** — inclusive start, exclusive end — so an event exactly at the
 *   window start counts and one exactly at `now` (the end) does not. This makes back-to-back windows
 *   partition time without double-counting a boundary event.
 * - **Ranking ties are broken deterministically**: higher play count first, then higher listened
 *   time, then the grouping key ascending. Given the same input the output order never varies.
 * - **Time bucketing** (day-of-week / hour-of-day) is resolved in an explicit [ZoneId] so a caller in
 *   any timezone gets consistent buckets; day-of-week is Monday-first (0=Monday … 6=Sunday).
 */
object ListeningStatsAggregator {

    private const val TOP_LIMIT = 25

    /**
     * Resolves a [StatsRange] against [nowMs] into an inclusive-start / exclusive-end millis window.
     * For [StatsRange.ALL_TIME] the start is 0 (epoch). The end is always [nowMs] (exclusive).
     */
    fun rangeWindow(range: StatsRange, nowMs: Long): LongRange {
        val startExclusiveEnd = nowMs
        val start = when (range) {
            StatsRange.LAST_7_DAYS -> nowMs - days(7)
            StatsRange.LAST_4_WEEKS -> nowMs - days(28)
            StatsRange.LAST_6_MONTHS -> nowMs - days(182)
            StatsRange.LAST_YEAR -> nowMs - days(365)
            StatsRange.ALL_TIME -> 0L
        }.coerceAtLeast(0L)
        // Represent the [start, end) window; callers use inWindow() rather than LongRange.contains,
        // because LongRange is inclusive on both ends and we need an exclusive end.
        return start until startExclusiveEnd
    }

    /** True when [timestampMs] falls in the half-open window [start, endExclusive). */
    private fun inWindow(timestampMs: Long, window: LongRange): Boolean =
        timestampMs >= window.first && timestampMs < (window.last + 1)

    /** Filters [records] to those whose timestamp is inside the [range] window resolved at [nowMs]. */
    fun filterToRange(records: List<PlayRecord>, range: StatsRange, nowMs: Long): List<PlayRecord> {
        val window = rangeWindow(range, nowMs)
        return records.filter { inWindow(it.timestampMs, window) }
    }

    /**
     * Full aggregation for the stats screen: filters to [range] (resolved at [nowMs]) then computes
     * totals, unique counts, the three top-N rankings, and the activity breakdowns in [zone].
     */
    fun aggregate(
        records: List<PlayRecord>,
        range: StatsRange,
        nowMs: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): ListeningStats {
        val inRange = filterToRange(records, range, nowMs)
        if (inRange.isEmpty()) return ListeningStats.empty(range)

        return ListeningStats(
            range = range,
            totalPlays = inRange.size,
            totalListenedMs = inRange.sumOf { it.listenedMs },
            uniqueSongs = inRange.mapTo(HashSet()) { it.mediaId }.size,
            uniqueArtists = inRange.filter { it.artist.isNotBlank() }
                .mapTo(HashSet()) { it.artist }.size,
            uniqueAlbums = inRange.mapNotNull { it.album?.takeIf(String::isNotBlank) }
                .toHashSet().size,
            topSongs = topSongs(inRange),
            topArtists = topArtists(inRange),
            topAlbums = topAlbums(inRange),
            byDayOfWeek = bucketByDayOfWeek(inRange, zone),
            byHourOfDay = bucketByHourOfDay(inRange, zone),
        )
    }

    /** Top songs grouped by [PlayRecord.mediaId]. */
    fun topSongs(records: List<PlayRecord>, limit: Int = TOP_LIMIT): List<RankedItem> =
        rank(records, limit, keyOf = { it.mediaId }) { key, group ->
            val first = group.first()
            RankedItem(
                key = key,
                title = first.title,
                subtitle = first.artist.takeIf { it.isNotBlank() },
                playCount = group.size,
                listenedMs = group.sumOf { it.listenedMs },
                artworkUrl = group.firstNotNullOfOrNull { it.artworkUrl },
            )
        }

    /** Top artists grouped by [PlayRecord.artist] (blank artists excluded). */
    fun topArtists(records: List<PlayRecord>, limit: Int = TOP_LIMIT): List<RankedItem> =
        rank(records.filter { it.artist.isNotBlank() }, limit, keyOf = { it.artist }) { key, group ->
            RankedItem(
                key = key,
                title = key,
                subtitle = null,
                playCount = group.size,
                listenedMs = group.sumOf { it.listenedMs },
                artworkUrl = group.firstNotNullOfOrNull { it.artworkUrl },
            )
        }

    /** Top albums grouped by [PlayRecord.album] (null/blank albums excluded). */
    fun topAlbums(records: List<PlayRecord>, limit: Int = TOP_LIMIT): List<RankedItem> =
        rank(
            records.filter { !it.album.isNullOrBlank() },
            limit,
            keyOf = { it.album!! },
        ) { key, group ->
            RankedItem(
                key = key,
                title = key,
                subtitle = group.firstOrNull { it.artist.isNotBlank() }?.artist,
                playCount = group.size,
                listenedMs = group.sumOf { it.listenedMs },
                artworkUrl = group.firstNotNullOfOrNull { it.artworkUrl },
            )
        }

    /**
     * Groups [records] by [keyOf], builds a [RankedItem] per group with [build], and sorts with the
     * deterministic tie-break (count desc, listenedMs desc, key asc), then takes [limit].
     */
    private inline fun rank(
        records: List<PlayRecord>,
        limit: Int,
        keyOf: (PlayRecord) -> String,
        build: (key: String, group: List<PlayRecord>) -> RankedItem,
    ): List<RankedItem> =
        records.groupBy(keyOf)
            .map { (key, group) -> build(key, group) }
            .sortedWith(
                compareByDescending<RankedItem> { it.playCount }
                    .thenByDescending { it.listenedMs }
                    .thenBy { it.key },
            )
            .take(limit)

    /** Plays per day-of-week in [zone], Monday-first (0=Monday … 6=Sunday). Always 7 buckets. */
    fun bucketByDayOfWeek(records: List<PlayRecord>, zone: ZoneId): List<ActivityBucket> {
        val counts = IntArray(7)
        for (r in records) {
            // DayOfWeek.value is 1=Monday … 7=Sunday; shift to 0-based Monday-first.
            val idx = zonedOf(r.timestampMs, zone).dayOfWeek.value - 1
            counts[idx]++
        }
        return counts.mapIndexed { i, c -> ActivityBucket(i, c) }
    }

    /** Plays per hour-of-day (0..23) in [zone]. Always 24 buckets. */
    fun bucketByHourOfDay(records: List<PlayRecord>, zone: ZoneId): List<ActivityBucket> {
        val counts = IntArray(24)
        for (r in records) {
            counts[zonedOf(r.timestampMs, zone).hour]++
        }
        return counts.mapIndexed { i, c -> ActivityBucket(i, c) }
    }

    /**
     * Builds the [WrappedSummary] for [year] from the full history. Uses a calendar-year window
     * [Jan 1 00:00, Jan 1 next-year 00:00) resolved in [zone]. Returns a `hasData = false` summary
     * when nothing was played that year.
     */
    fun wrapped(
        records: List<PlayRecord>,
        year: Int,
        zone: ZoneId = ZoneId.systemDefault(),
    ): WrappedSummary {
        val start = ZonedDateTime.of(year, 1, 1, 0, 0, 0, 0, zone).toInstant().toEpochMilli()
        val end = ZonedDateTime.of(year + 1, 1, 1, 0, 0, 0, 0, zone).toInstant().toEpochMilli()
        val inYear = records.filter { it.timestampMs in start until end }

        if (inYear.isEmpty()) {
            return WrappedSummary(
                year = year,
                hasData = false,
                totalMinutes = 0L,
                totalPlays = 0,
                differentArtists = 0,
                differentSongs = 0,
                topSong = null,
                topArtist = null,
                topAlbum = null,
                topArtists = emptyList(),
                topSongs = emptyList(),
                busiestHour = null,
                topDayLabelIndex = null,
            )
        }

        val songs = topSongs(inYear, limit = 5)
        val artists = topArtists(inYear, limit = 5)
        val albums = topAlbums(inYear, limit = 1)
        val hourBuckets = bucketByHourOfDay(inYear, zone)
        val dayBuckets = bucketByDayOfWeek(inYear, zone)

        return WrappedSummary(
            year = year,
            hasData = true,
            totalMinutes = inYear.sumOf { it.listenedMs } / 60_000L,
            totalPlays = inYear.size,
            differentArtists = inYear.filter { it.artist.isNotBlank() }
                .mapTo(HashSet()) { it.artist }.size,
            differentSongs = inYear.mapTo(HashSet()) { it.mediaId }.size,
            topSong = songs.firstOrNull(),
            topArtist = artists.firstOrNull(),
            topAlbum = albums.firstOrNull(),
            topArtists = artists,
            topSongs = songs,
            busiestHour = hourBuckets.maxWithOrNull(bucketWinner)?.takeIf { it.playCount > 0 }?.labelIndex,
            topDayLabelIndex = dayBuckets.maxWithOrNull(bucketWinner)?.takeIf { it.playCount > 0 }?.labelIndex,
        )
    }

    /** All calendar years (descending) that have at least one play, resolved in [zone]. */
    fun yearsWithData(records: List<PlayRecord>, zone: ZoneId = ZoneId.systemDefault()): List<Int> =
        records.mapTo(HashSet()) { zonedOf(it.timestampMs, zone).year }
            .sortedDescending()

    /**
     * The pure "does this count as a play?" decision, factored out for testing. A play is counted
     * when the actually-listened time reaches [thresholdMs] (default 30s) OR at least [halfFraction]
     * of the track's duration (default 50%). Scrubbing / brief skips fail both and are not counted.
     *
     * @param listenedMs actual play time in ms (excludes pauses/buffering/scrubbing).
     * @param durationMs full track duration in ms (0/unknown ⇒ only the absolute threshold applies).
     */
    fun countsAsPlay(
        listenedMs: Long,
        durationMs: Long,
        thresholdMs: Long = 30_000L,
        halfFraction: Double = 0.5,
    ): Boolean {
        if (listenedMs <= 0L) return false
        if (listenedMs >= thresholdMs) return true
        if (durationMs > 0L && listenedMs >= (durationMs * halfFraction)) return true
        return false
    }

    // Winner comparator for activity buckets: higher count wins, ties broken by the earlier index so
    // the result is deterministic (e.g. Monday over Tuesday, hour 9 over hour 21 on a tie).
    private val bucketWinner: Comparator<ActivityBucket> =
        compareBy<ActivityBucket> { it.playCount }.thenByDescending { it.labelIndex }

    private fun zonedOf(epochMs: Long, zone: ZoneId): ZonedDateTime =
        Instant.ofEpochMilli(epochMs).atZone(zone)

    private fun days(n: Long): Long = ChronoUnit.DAYS.duration.toMillis() * n
}
