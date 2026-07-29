package com.viperplayer.domain.rec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

/**
 * Unit tests for the pure [DaylistTitleGenerator]: determinism for a fixed seed, variety across
 * seeds/buckets/genres, the genre→phrase mapping, the empty-genre graceful fallback, and the title's
 * shape (lowercase, contains the weekday + time-of-day word).
 */
class DaylistTitleGeneratorTest {

    private fun gen(
        genres: List<String>,
        bucket: TimeBucket = TimeBucket.MORNING,
        day: DayOfWeek = DayOfWeek.WEDNESDAY,
        seed: Long = 100L,
    ) = DaylistTitleGenerator.generate(genres, bucket, day, seed)

    @Test
    fun `title is deterministic for a fixed seed`() {
        val a = gen(listOf("house"), seed = 42L)
        val b = gen(listOf("house"), seed = 42L)
        assertEquals(a, b)
        assertEquals(a.title, b.title)
        assertEquals(a.description, b.description)
    }

    @Test
    fun `title is lowercase and contains the weekday and time-of-day`() {
        val naming = gen(listOf("house"), bucket = TimeBucket.MORNING, day = DayOfWeek.WEDNESDAY)
        assertEquals(naming.title, naming.title.lowercase())
        assertTrue("expected weekday in '${naming.title}'", naming.title.contains("wednesday"))
        assertTrue("expected time-of-day in '${naming.title}'", naming.title.contains("morning"))
    }

    @Test
    fun `night bucket uses the 'late night' time-of-day words`() {
        val naming = gen(listOf("lo-fi"), bucket = TimeBucket.NIGHT, day = DayOfWeek.SATURDAY)
        assertTrue("expected 'late night' in '${naming.title}'", naming.title.contains("late night"))
        assertTrue("expected weekday in '${naming.title}'", naming.title.contains("saturday"))
    }

    @Test
    fun `each weekday renders its own word`() {
        val expected = mapOf(
            DayOfWeek.MONDAY to "monday",
            DayOfWeek.TUESDAY to "tuesday",
            DayOfWeek.WEDNESDAY to "wednesday",
            DayOfWeek.THURSDAY to "thursday",
            DayOfWeek.FRIDAY to "friday",
            DayOfWeek.SATURDAY to "saturday",
            DayOfWeek.SUNDAY to "sunday",
        )
        for ((day, word) in expected) {
            assertTrue(gen(listOf("pop"), day = day).title.contains(word))
        }
    }

    @Test
    fun `genre phrase mapping resolves common families`() {
        // house/techno/disco → four-on-the-floor
        assertTrue(gen(listOf("house")).title.contains("four-on-the-floor"))
        assertTrue(gen(listOf("techno")).title.contains("four-on-the-floor"))
        assertTrue(gen(listOf("disco")).title.contains("four-on-the-floor"))
        // lo-fi / chillhop → lo-fi beats
        assertTrue(gen(listOf("lo-fi")).title.contains("lo-fi beats"))
        assertTrue(gen(listOf("chillhop")).title.contains("lo-fi beats"))
        // hip hop / rap → boom bap
        assertTrue(gen(listOf("hip hop")).title.contains("boom bap"))
        assertTrue(gen(listOf("rap")).title.contains("boom bap"))
        // jazz stays jazz; rock → guitar-driven; ambient → ambient; metal → heavy
        assertTrue(gen(listOf("jazz")).title.contains("jazz"))
        assertTrue(gen(listOf("rock")).title.contains("guitar-driven"))
        assertTrue(gen(listOf("ambient")).title.contains("ambient"))
        assertTrue(gen(listOf("metal")).title.contains("heavy"))
        // r&b / soul → smooth soul
        assertTrue(gen(listOf("r&b")).title.contains("smooth soul"))
        assertTrue(gen(listOf("soul")).title.contains("smooth soul"))
    }

    @Test
    fun `genre matching is case-insensitive and fuzzy on substrings`() {
        assertTrue(gen(listOf("Deep House")).title.contains("four-on-the-floor"))
        assertTrue(gen(listOf("MELODIC TECHNO")).title.contains("four-on-the-floor"))
        assertTrue(gen(listOf("West Coast Hip Hop")).title.contains("boom bap"))
    }

    @Test
    fun `an unknown genre falls back to the raw genre as the feel phrase`() {
        val naming = gen(listOf("shoegaze"))
        assertTrue("expected the raw genre in '${naming.title}'", naming.title.contains("shoegaze"))
    }

    @Test
    fun `empty genres degrade to a graceful generic title, still lowercase with weekday + time`() {
        val naming = gen(emptyList(), bucket = TimeBucket.AFTERNOON, day = DayOfWeek.MONDAY)
        assertEquals(naming.title, naming.title.lowercase())
        assertTrue(naming.title.contains("monday"))
        assertTrue(naming.title.contains("afternoon"))
        assertTrue("expected a non-blank title", naming.title.isNotBlank())
        // No dangling placeholder in the description either.
        assertTrue("description should not carry the raw token", !naming.description.contains("{genre}"))
    }

    @Test
    fun `blank genre entries are treated as empty`() {
        val naming = gen(listOf("", "   "))
        assertTrue(naming.title.isNotBlank())
        assertEquals(naming.title, naming.title.lowercase())
    }

    @Test
    fun `title varies across time-of-day buckets for the same day and genres`() {
        val titles = TimeBucket.entries.map {
            gen(listOf("house"), bucket = it, day = DayOfWeek.FRIDAY, seed = it.ordinal.toLong()).title
        }.toSet()
        // Four distinct buckets should not all collapse to one title (time-of-day word differs at least).
        assertTrue("expected varied titles across buckets, got $titles", titles.size >= 2)
    }

    @Test
    fun `title varies across days via the seed`() {
        // The energy adjective is seed-rotated: sweeping the seed should surface more than one title.
        val titles = (0L until 20L).map { gen(listOf("house"), seed = it).title }.toSet()
        assertTrue("expected the seed to rotate the adjective, got $titles", titles.size >= 2)
    }

    @Test
    fun `description names the top genre when a template slot is present`() {
        // Sweep seeds to hit a genre-naming template; at least one should embed the genre.
        val namedSomewhere = (0L until 12L).any { seed ->
            gen(listOf("jazz"), bucket = TimeBucket.EVENING, seed = seed).description.contains("jazz")
        }
        assertTrue("expected some evening template to name the top genre", namedSomewhere)
    }

    @Test
    fun `description is stable for a fixed seed and non-empty`() {
        val a = gen(listOf("pop"), seed = 7L).description
        val b = gen(listOf("pop"), seed = 7L).description
        assertEquals(a, b)
        assertTrue(a.isNotBlank())
    }

    @Test
    fun `distinct genres mapping to the same phrase do not duplicate it in the title`() {
        // house + techno + disco all map to four-on-the-floor; the phrase must appear once.
        val title = gen(listOf("house", "techno", "disco")).title
        assertEquals(
            "four-on-the-floor should appear exactly once",
            1,
            Regex("four-on-the-floor").findAll(title).count(),
        )
    }

    @Test
    fun `a negative seed does not crash and stays deterministic`() {
        val a = gen(listOf("house"), seed = Long.MIN_VALUE)
        val b = gen(listOf("house"), seed = Long.MIN_VALUE)
        assertEquals(a, b)
        assertTrue(a.title.isNotBlank())
    }

    @Test
    fun `different genres can produce different titles`() {
        assertNotEquals(gen(listOf("metal")).title, gen(listOf("ambient")).title)
    }
}
