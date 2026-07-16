package com.viperplayer.domain.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * JVM unit tests for the pure [ListeningStatsAggregator]. Covers ranking (by count and by time),
 * range/window filtering + boundaries, totals/unique counts, deterministic tie-breaking,
 * day-of-week / hour-of-day bucketing, empty history, Wrapped, and the play-threshold decision.
 */
class ListeningStatsAggregatorTest {

    private val utc = ZoneId.of("UTC")

    /** Fixed reference "now": 2024-06-15 12:00:00 UTC. */
    private val now = LocalDateTime.of(2024, 6, 15, 12, 0, 0)
        .toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun at(y: Int, mo: Int, d: Int, h: Int = 12, mi: Int = 0): Long =
        LocalDateTime.of(y, mo, d, h, mi, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun rec(
        ts: Long,
        mediaId: String = "pluginId=local&sourceId=$ts",
        title: String = "Song",
        artist: String = "Artist",
        album: String? = "Album",
        pluginId: String = "local",
        listenedMs: Long = 180_000L,
        durationMs: Long = 180_000L,
    ) = PlayRecord(
        timestampMs = ts,
        mediaId = mediaId,
        title = title,
        artist = artist,
        album = album,
        pluginId = pluginId,
        listenedMs = listenedMs,
        durationMs = durationMs,
    )

    // ---- Range windows / filtering -------------------------------------------------------------

    @Test
    fun rangeWindow_last7Days_isStartInclusiveEndExclusive() {
        val window = ListeningStatsAggregator.rangeWindow(StatsRange.LAST_7_DAYS, now)
        // Start is now - 7 days; end (exclusive) is now.
        val expectedStart = now - 7L * 24 * 60 * 60 * 1000
        assertEquals(expectedStart, window.first)
        assertEquals(now - 1, window.last) // half-open [start, now) => last inclusive index is now-1
    }

    @Test
    fun rangeWindow_allTime_startsAtEpoch() {
        val window = ListeningStatsAggregator.rangeWindow(StatsRange.ALL_TIME, now)
        assertEquals(0L, window.first)
    }

    @Test
    fun filterToRange_startBoundaryInclusive_endBoundaryExclusive() {
        val start = now - 7L * 24 * 60 * 60 * 1000
        val records = listOf(
            rec(start),          // exactly at start -> included
            rec(now - 1),        // just before now -> included
            rec(now),            // exactly at now (end, exclusive) -> excluded
            rec(start - 1),      // before start -> excluded
        )
        val filtered = ListeningStatsAggregator.filterToRange(records, StatsRange.LAST_7_DAYS, now)
        assertEquals(2, filtered.size)
        assertTrue(filtered.any { it.timestampMs == start })
        assertTrue(filtered.any { it.timestampMs == now - 1 })
        assertFalse(filtered.any { it.timestampMs == now })
        assertFalse(filtered.any { it.timestampMs == start - 1 })
    }

    @Test
    fun aggregate_excludesEventsOutsideRange() {
        val records = listOf(
            rec(at(2024, 6, 14), artist = "Recent"),      // in last 7 days
            rec(at(2024, 1, 1), artist = "Old"),          // ~5 months ago, out of 7-day range
        )
        val stats = ListeningStatsAggregator.aggregate(records, StatsRange.LAST_7_DAYS, now, utc)
        assertEquals(1, stats.totalPlays)
        assertEquals(1, stats.topArtists.size)
        assertEquals("Recent", stats.topArtists.first().title)
    }

    // ---- Totals / unique counts ----------------------------------------------------------------

    @Test
    fun aggregate_totalsAndUniqueCounts() {
        val records = listOf(
            rec(at(2024, 6, 14), mediaId = "a", artist = "X", album = "A1", listenedMs = 100_000L),
            rec(at(2024, 6, 14), mediaId = "a", artist = "X", album = "A1", listenedMs = 100_000L),
            rec(at(2024, 6, 14), mediaId = "b", artist = "Y", album = "A2", listenedMs = 50_000L),
        )
        val stats = ListeningStatsAggregator.aggregate(records, StatsRange.LAST_7_DAYS, now, utc)
        assertEquals(3, stats.totalPlays)
        assertEquals(250_000L, stats.totalListenedMs)
        assertEquals(2, stats.uniqueSongs)   // a, b
        assertEquals(2, stats.uniqueArtists) // X, Y
        assertEquals(2, stats.uniqueAlbums)  // A1, A2
    }

    @Test
    fun aggregate_blankArtistAndAlbum_excludedFromUniqueCountsAndRankings() {
        val records = listOf(
            rec(at(2024, 6, 14), mediaId = "a", artist = "", album = null),
            rec(at(2024, 6, 14), mediaId = "b", artist = " ", album = ""),
            rec(at(2024, 6, 14), mediaId = "c", artist = "Real", album = "RealAlbum"),
        )
        val stats = ListeningStatsAggregator.aggregate(records, StatsRange.LAST_7_DAYS, now, utc)
        assertEquals(3, stats.totalPlays)
        assertEquals(1, stats.uniqueArtists)
        assertEquals(1, stats.uniqueAlbums)
        assertEquals(1, stats.topArtists.size)
        assertEquals(1, stats.topAlbums.size)
    }

    // ---- Ranking: by count and by time ---------------------------------------------------------

    @Test
    fun topSongs_rankedByPlayCountDescending() {
        val records = listOf(
            rec(at(2024, 6, 14), mediaId = "a", title = "A"),
            rec(at(2024, 6, 14), mediaId = "a", title = "A"),
            rec(at(2024, 6, 14), mediaId = "a", title = "A"),
            rec(at(2024, 6, 14), mediaId = "b", title = "B"),
            rec(at(2024, 6, 14), mediaId = "b", title = "B"),
            rec(at(2024, 6, 14), mediaId = "c", title = "C"),
        )
        val top = ListeningStatsAggregator.topSongs(records)
        assertEquals(listOf("a", "b", "c"), top.map { it.key })
        assertEquals(listOf(3, 2, 1), top.map { it.playCount })
    }

    @Test
    fun topArtists_aggregatePlayCountAndListenedTime() {
        val records = listOf(
            rec(at(2024, 6, 14), mediaId = "1", artist = "Alpha", listenedMs = 60_000L),
            rec(at(2024, 6, 14), mediaId = "2", artist = "Alpha", listenedMs = 90_000L),
            rec(at(2024, 6, 14), mediaId = "3", artist = "Beta", listenedMs = 200_000L),
        )
        val top = ListeningStatsAggregator.topArtists(records)
        // Alpha has more plays (2 vs 1) so ranks first despite less total time.
        assertEquals("Alpha", top[0].key)
        assertEquals(2, top[0].playCount)
        assertEquals(150_000L, top[0].listenedMs)
        assertEquals("Beta", top[1].key)
        assertEquals(200_000L, top[1].listenedMs)
    }

    @Test
    fun ranking_tieOnCount_brokenByListenedTimeDescending() {
        val records = listOf(
            rec(at(2024, 6, 14), mediaId = "a", title = "A", listenedMs = 100_000L),
            rec(at(2024, 6, 14), mediaId = "b", title = "B", listenedMs = 200_000L),
        )
        val top = ListeningStatsAggregator.topSongs(records)
        // Both have 1 play; higher listenedMs wins.
        assertEquals("b", top[0].key)
        assertEquals("a", top[1].key)
    }

    @Test
    fun ranking_tieOnCountAndTime_brokenByKeyAscending_deterministic() {
        val records = listOf(
            rec(at(2024, 6, 14), mediaId = "zeta", title = "Z", listenedMs = 100_000L),
            rec(at(2024, 6, 14), mediaId = "alpha", title = "A", listenedMs = 100_000L),
        )
        // Run twice; order must be identical and key-ascending.
        val first = ListeningStatsAggregator.topSongs(records).map { it.key }
        val second = ListeningStatsAggregator.topSongs(records.reversed()).map { it.key }
        assertEquals(listOf("alpha", "zeta"), first)
        assertEquals(first, second)
    }

    @Test
    fun topSongs_respectsLimit() {
        val records = (1..30).map { rec(at(2024, 6, 14), mediaId = "id$it", title = "T$it") }
        val top = ListeningStatsAggregator.topSongs(records, limit = 10)
        assertEquals(10, top.size)
    }

    // ---- Bucketing -----------------------------------------------------------------------------

    @Test
    fun bucketByDayOfWeek_mondayFirst_countsCorrectly() {
        // 2024-06-17 is a Monday; 2024-06-23 is a Sunday.
        val records = listOf(
            rec(at(2024, 6, 17)), // Monday -> idx 0
            rec(at(2024, 6, 17)), // Monday -> idx 0
            rec(at(2024, 6, 23)), // Sunday -> idx 6
        )
        val buckets = ListeningStatsAggregator.bucketByDayOfWeek(records, utc)
        assertEquals(7, buckets.size)
        assertEquals(2, buckets[0].playCount) // Monday
        assertEquals(1, buckets[6].playCount) // Sunday
        assertEquals(0, buckets[3].playCount) // Thursday
    }

    @Test
    fun bucketByHourOfDay_alwaysFullDomainOf24() {
        val records = listOf(
            rec(at(2024, 6, 14, h = 9)),
            rec(at(2024, 6, 14, h = 9)),
            rec(at(2024, 6, 14, h = 23)),
        )
        val buckets = ListeningStatsAggregator.bucketByHourOfDay(records, utc)
        assertEquals(24, buckets.size)
        assertEquals(2, buckets[9].playCount)
        assertEquals(1, buckets[23].playCount)
        assertEquals(0, buckets[0].playCount)
    }

    @Test
    fun bucketByHourOfDay_respectsTimeZone() {
        // 2024-06-14 23:00 UTC == 2024-06-15 01:00 in UTC+2.
        val records = listOf(rec(at(2024, 6, 14, h = 23)))
        val utcBuckets = ListeningStatsAggregator.bucketByHourOfDay(records, ZoneOffset.UTC)
        val plus2Buckets = ListeningStatsAggregator.bucketByHourOfDay(records, ZoneOffset.ofHours(2))
        assertEquals(1, utcBuckets[23].playCount)
        assertEquals(1, plus2Buckets[1].playCount)
    }

    // ---- Empty history -------------------------------------------------------------------------

    @Test
    fun aggregate_emptyHistory_returnsZerosAndFixedSizeBuckets() {
        val stats = ListeningStatsAggregator.aggregate(emptyList(), StatsRange.LAST_YEAR, now, utc)
        assertTrue(stats.isEmpty)
        assertEquals(0, stats.totalPlays)
        assertEquals(0L, stats.totalListenedMs)
        assertEquals(0, stats.uniqueSongs)
        assertEquals(0, stats.uniqueArtists)
        assertEquals(0, stats.uniqueAlbums)
        assertTrue(stats.topSongs.isEmpty())
        assertEquals(7, stats.byDayOfWeek.size)
        assertEquals(24, stats.byHourOfDay.size)
        assertTrue(stats.byHourOfDay.all { it.playCount == 0 })
    }

    @Test
    fun aggregate_allEventsOutOfRange_returnsEmpty() {
        val records = listOf(rec(at(2020, 1, 1)))
        val stats = ListeningStatsAggregator.aggregate(records, StatsRange.LAST_7_DAYS, now, utc)
        assertTrue(stats.isEmpty)
    }

    // ---- Play threshold decision (scrubbing doesn't count) -------------------------------------

    @Test
    fun countsAsPlay_pastAbsoluteThreshold_counts() {
        assertTrue(ListeningStatsAggregator.countsAsPlay(listenedMs = 30_000L, durationMs = 300_000L))
        assertTrue(ListeningStatsAggregator.countsAsPlay(listenedMs = 45_000L, durationMs = 300_000L))
    }

    @Test
    fun countsAsPlay_pastHalfOfShortTrack_counts() {
        // 20s of a 30s track = 66% >= 50% -> counts even though under 30s absolute.
        assertTrue(ListeningStatsAggregator.countsAsPlay(listenedMs = 20_000L, durationMs = 30_000L))
    }

    @Test
    fun countsAsPlay_briefScrub_doesNotCount() {
        // 5s of a 300s track: under 30s and under 50% -> scrubbing, not a play.
        assertFalse(ListeningStatsAggregator.countsAsPlay(listenedMs = 5_000L, durationMs = 300_000L))
    }

    @Test
    fun countsAsPlay_zeroOrNegative_doesNotCount() {
        assertFalse(ListeningStatsAggregator.countsAsPlay(listenedMs = 0L, durationMs = 300_000L))
        assertFalse(ListeningStatsAggregator.countsAsPlay(listenedMs = -1L, durationMs = 300_000L))
    }

    @Test
    fun countsAsPlay_unknownDuration_onlyAbsoluteThresholdApplies() {
        assertTrue(ListeningStatsAggregator.countsAsPlay(listenedMs = 31_000L, durationMs = 0L))
        assertFalse(ListeningStatsAggregator.countsAsPlay(listenedMs = 10_000L, durationMs = 0L))
    }

    // ---- Wrapped -------------------------------------------------------------------------------

    @Test
    fun wrapped_summarizesYearAndPicksTops() {
        val records = listOf(
            rec(at(2024, 3, 1), mediaId = "s1", title = "Hit", artist = "Star", album = "Debut", listenedMs = 200_000L),
            rec(at(2024, 3, 2), mediaId = "s1", title = "Hit", artist = "Star", album = "Debut", listenedMs = 200_000L),
            rec(at(2024, 7, 4), mediaId = "s2", title = "Other", artist = "Nova", album = "Two", listenedMs = 100_000L),
            rec(at(2023, 12, 31), mediaId = "s3", title = "LastYear", artist = "Old", listenedMs = 999_000L), // different year
        )
        val wrapped = ListeningStatsAggregator.wrapped(records, 2024, utc)
        assertTrue(wrapped.hasData)
        assertEquals(2024, wrapped.year)
        assertEquals(3, wrapped.totalPlays) // 2023 event excluded
        assertEquals(2, wrapped.differentSongs)
        assertEquals(2, wrapped.differentArtists)
        assertEquals("Hit", wrapped.topSong?.title)
        assertEquals("Star", wrapped.topArtist?.key)
        assertEquals("Debut", wrapped.topAlbum?.key)
        // (200_000 * 2 + 100_000) / 60_000 = 500_000 / 60_000 = 8 minutes.
        assertEquals(8L, wrapped.totalMinutes)
    }

    @Test
    fun wrapped_noPlaysInYear_hasNoData() {
        val records = listOf(rec(at(2022, 5, 5)))
        val wrapped = ListeningStatsAggregator.wrapped(records, 2024, utc)
        assertFalse(wrapped.hasData)
        assertEquals(0, wrapped.totalPlays)
        assertNull(wrapped.topSong)
        assertNull(wrapped.busiestHour)
    }

    @Test
    fun wrapped_busiestHourAndTopDay_computed() {
        val records = listOf(
            rec(at(2024, 6, 17, h = 8)),  // Monday 08:00
            rec(at(2024, 6, 17, h = 8)),  // Monday 08:00
            rec(at(2024, 6, 18, h = 20)), // Tuesday 20:00
        )
        val wrapped = ListeningStatsAggregator.wrapped(records, 2024, utc)
        assertEquals(8, wrapped.busiestHour)
        assertEquals(0, wrapped.topDayLabelIndex) // Monday
    }

    @Test
    fun yearsWithData_descendingDistinct() {
        val records = listOf(
            rec(at(2022, 1, 1)),
            rec(at(2024, 6, 1)),
            rec(at(2024, 12, 1)),
            rec(at(2023, 3, 1)),
        )
        assertEquals(listOf(2024, 2023, 2022), ListeningStatsAggregator.yearsWithData(records, utc))
    }
}
