package com.viperplayer.data.repository

import com.viperplayer.data.local.entity.GenreEntity
import com.viperplayer.data.local.entity.SongEntity
import com.viperplayer.data.local.entity.relation.SongEmbeddingRow
import com.viperplayer.data.rec.ClapModel
import com.viperplayer.data.rec.embeddingToBytes
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.rec.TasteState
import com.viperplayer.domain.rec.TimeBucket
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Unit tests for [DaylistRepositoryImpl] over the in-memory fakes in [DaylistTestFakes]:
 *  - cold / disabled taste ⇒ `null` (no daylist);
 *  - a warm bucket taste over an indexed library ⇒ a titled, non-empty daylist;
 *  - caching returns the SAME daylist within a slot, and a bucket rollover regenerates it.
 */
class DaylistRepositoryImplTest {

    private val dim get() = ClapModel.EMBEDDING_DIM

    /** A 512-d unit vector along axis [k]. */
    private fun axis(k: Int): FloatArray = FloatArray(dim).also { it[k] = 1f }

    /** A fixed UTC zone so the time-of-day bucket + epoch-day are deterministic across hosts. */
    private val utc: ZoneId = ZoneId.of("UTC")

    /** Epoch millis for [hour]:00 UTC on [date]. */
    private fun at(hour: Int, date: LocalDate = LocalDate.of(2023, 11, 15)): Long =
        date.atTime(hour, 0).atZone(utc).toInstant().toEpochMilli()

    /** A local [Song] with the given source id + optional genres. */
    private fun song(id: String, genres: List<String> = emptyList()): Song =
        Song(id = MediaId.Local(id), title = "Song $id", genres = genres, artworkUrl = "art-$id")

    /** A local [SongEntity] for row [rowId] resolving to `MediaId.Local(sourceId)`. */
    private fun entity(rowId: Long, sourceId: String): SongEntity =
        SongEntity(id = rowId, idType = "local", pluginId = "", sourceId = sourceId, title = "Song $sourceId")

    /** N candidate rows on axis 0 (near a taste on axis 0), rows 1..N. */
    private fun candidateRows(n: Int): List<SongEmbeddingRow> =
        (1..n).map { SongEmbeddingRow(songId = it.toLong(), embedding = embeddingToBytes(axis(0))) }

    private fun entities(n: Int): List<SongEntity> = (1..n).map { entity(it.toLong(), "s$it") }

    private fun songMap(n: Int, genres: List<String> = emptyList()): Map<MediaId, Song> =
        (1..n).associate { MediaId.Local("s$it") to song("s$it", genres) }

    private fun repo(
        songDao: FakeDaylistSongDao,
        settingsOn: Boolean = true,
        settings: FakeDaylistSettingsRepository = FakeDaylistSettingsRepository(settingsOn),
        taste: TasteState = TasteState(vector = axis(0), interactions = 5),
        songs: Map<MediaId, Song> = emptyMap(),
        genreIdsBySong: Map<Long, List<Long>> = emptyMap(),
        genres: List<GenreEntity> = emptyList(),
        clock: () -> Long,
    ) = DaylistRepositoryImpl(
        songDao = songDao,
        genreDao = FakeDaylistGenreDao(genres),
        crossRefDao = FakeDaylistCrossRefDao(genreIdsBySong),
        mediaLibraryRepository = FakeDaylistMediaLibraryRepository(songs),
        settingsRepository = settings,
        tasteRepository = FakeDaylistTasteRepository(taste),
        clock = clock,
        zoneId = utc,
    )

    @Test
    fun `disabled recommendations yields no daylist`() = runTest {
        val n = 10
        val repo = repo(
            songDao = FakeDaylistSongDao(embeddings = candidateRows(n), rows = entities(n)),
            settingsOn = false,
            songs = songMap(n),
            clock = { at(9) },
        )
        assertNull(repo.currentDaylist())
    }

    @Test
    fun `cold taste yields no daylist`() = runTest {
        val n = 10
        val repo = repo(
            songDao = FakeDaylistSongDao(embeddings = candidateRows(n), rows = entities(n)),
            taste = TasteState.COLD,
            songs = songMap(n),
            clock = { at(9) },
        )
        assertNull(repo.currentDaylist())
    }

    @Test
    fun `an unindexed library yields no daylist`() = runTest {
        val repo = repo(
            songDao = FakeDaylistSongDao(embeddings = emptyList(), rows = emptyList()),
            songs = emptyMap(),
            clock = { at(9) },
        )
        assertNull(repo.currentDaylist())
    }

    @Test
    fun `too few resolvable songs yields no daylist`() = runTest {
        // Two candidates only (below MIN_SONGS = 5).
        val repo = repo(
            songDao = FakeDaylistSongDao(embeddings = candidateRows(2), rows = entities(2)),
            songs = songMap(2),
            clock = { at(9) },
        )
        assertNull(repo.currentDaylist())
    }

    @Test
    fun `warm taste over an indexed library yields a titled non-empty daylist`() = runTest {
        val n = 10
        val repo = repo(
            songDao = FakeDaylistSongDao(embeddings = candidateRows(n), rows = entities(n)),
            songs = songMap(n, genres = listOf("house")),
            clock = { at(9) }, // 09:00 UTC ⇒ MORNING
        )
        val daylist = repo.currentDaylist()
        requireNotNull(daylist)
        assertTrue("expected a non-empty daylist", daylist.songs.isNotEmpty())
        assertTrue("expected a lowercase generated title", daylist.title.isNotBlank())
        assertTrue("morning slot title should say 'morning'", daylist.title.contains("morning"))
        assertTrue("house genre should map to four-on-the-floor", daylist.title.contains("four-on-the-floor"))
    }

    @Test
    fun `cross-ref genres drive the title when songs carry no tags`() = runTest {
        val n = 10
        // Genre id 7 = "techno"; every candidate row maps to it via the cross-ref.
        val genreIdsBySong = (1..n).associate { it.toLong() to listOf(7L) }
        val repo = repo(
            songDao = FakeDaylistSongDao(embeddings = candidateRows(n), rows = entities(n)),
            songs = songMap(n), // no carried tags
            genreIdsBySong = genreIdsBySong,
            genres = listOf(GenreEntity(id = 7L, name = "techno")),
            clock = { at(9) },
        )
        val daylist = repo.currentDaylist()
        requireNotNull(daylist)
        assertTrue("techno should map to four-on-the-floor", daylist.title.contains("four-on-the-floor"))
    }

    @Test
    fun `cache returns the same daylist within a slot`() = runTest {
        val n = 10
        var nowMs = at(9)
        val repo = repo(
            songDao = FakeDaylistSongDao(embeddings = candidateRows(n), rows = entities(n)),
            songs = songMap(n, genres = listOf("jazz")),
            clock = { nowMs },
        )
        val first = repo.currentDaylist()
        nowMs = at(10) // still the MORNING bucket, same day
        val second = repo.currentDaylist()
        requireNotNull(first)
        requireNotNull(second)
        // Same slot ⇒ the exact same cached instance (identity), not a regenerated equal one.
        assertSame(first, second)
    }

    @Test
    fun `a bucket rollover regenerates the daylist`() = runTest {
        val n = 10
        var nowMs = at(9) // MORNING
        val repo = repo(
            songDao = FakeDaylistSongDao(embeddings = candidateRows(n), rows = entities(n)),
            songs = songMap(n, genres = listOf("house")),
            clock = { nowMs },
        )
        val morning = repo.currentDaylist()
        nowMs = at(14) // 14:00 UTC ⇒ AFTERNOON
        val afternoon = repo.currentDaylist()
        requireNotNull(morning)
        requireNotNull(afternoon)
        assertEquals(TimeBucket.MORNING, morning.bucket)
        assertEquals(TimeBucket.AFTERNOON, afternoon.bucket)
        // The time-of-day word must have changed with the bucket.
        assertTrue(morning.title.contains("morning"))
        assertTrue(afternoon.title.contains("afternoon"))
        assertNotEquals(morning.title, afternoon.title)
    }

    @Test
    fun `a new day regenerates even in the same bucket`() = runTest {
        val n = 10
        var nowMs = at(9, LocalDate.of(2023, 11, 15)) // Wednesday MORNING
        val repo = repo(
            songDao = FakeDaylistSongDao(embeddings = candidateRows(n), rows = entities(n)),
            songs = songMap(n, genres = listOf("house")),
            clock = { nowMs },
        )
        val wed = repo.currentDaylist()
        nowMs = at(9, LocalDate.of(2023, 11, 16)) // Thursday MORNING
        val thu = repo.currentDaylist()
        requireNotNull(wed)
        requireNotNull(thu)
        assertNotSameInstance(wed, thu)
        assertTrue(wed.title.contains("wednesday"))
        assertTrue(thu.title.contains("thursday"))
    }

    @Test
    fun `disabling recommendations mid-slot drops the cached daylist`() = runTest {
        val n = 10
        val settings = FakeDaylistSettingsRepository(recommendationsOn = true)
        val repo = repo(
            songDao = FakeDaylistSongDao(embeddings = candidateRows(n), rows = entities(n)),
            settings = settings,
            songs = songMap(n, genres = listOf("house")),
            clock = { at(9) }, // fixed slot the whole test
        )
        requireNotNull(repo.currentDaylist()) // builds + caches

        // User turns smart recommendations OFF mid-slot; the daylist must vanish immediately, not
        // survive on the stale (bucket, epochDay) cache until the bucket rolls over.
        settings.setEnabled(false)
        assertNull(repo.currentDaylist())
    }

    @Test
    fun `a row's carried tags are used only when it has no cross-ref genres`() = runTest {
        val n = 10
        // Rows 1..5 have a cross-ref genre "jazz"; rows 6..10 have none but carry a "metal" tag.
        val genreIdsBySong = (1..5).associate { it.toLong() to listOf(9L) }
        val songs: Map<MediaId, Song> = (1..n).associate { i ->
            val tags = if (i > 5) listOf("metal") else emptyList()
            MediaId.Local("s$i") to song("s$i", tags)
        }
        val repo = repo(
            songDao = FakeDaylistSongDao(embeddings = candidateRows(n), rows = entities(n)),
            songs = songs,
            genreIdsBySong = genreIdsBySong,
            genres = listOf(GenreEntity(id = 9L, name = "jazz")),
            clock = { at(9) },
        )
        val daylist = repo.currentDaylist()
        requireNotNull(daylist)
        // 5 jazz (cross-ref) vs 5 metal (carried) — jazz wins the tie by first appearance (rows 1..5),
        // and crucially the cross-ref rows are NOT double-counted from any carried tag.
        assertTrue("expected jazz to lead the title, got '${daylist.title}'", daylist.title.contains("jazz"))
    }

    @Test
    fun `liked and recently played songs are excluded from the daylist`() = runTest {
        val n = 6
        // Row 1 is liked, row 2 is recently played ⇒ both excluded; only rows 3..6 remain (4 < MIN_SONGS).
        val songDao = FakeDaylistSongDao(
            embeddings = candidateRows(n),
            rows = entities(n),
            liked = listOf(entity(1L, "s1")),
            recent = listOf(entity(2L, "s2")),
        )
        val repo = repo(
            songDao = songDao,
            songs = songMap(n),
            clock = { at(9) },
        )
        // With only 4 non-excluded songs (< MIN_SONGS = 5) the daylist is suppressed, proving exclusion.
        assertNull(repo.currentDaylist())
    }

    private fun assertNotSameInstance(a: Any, b: Any) {
        assertTrue("expected regenerated (distinct) instances", a !== b)
    }
}
