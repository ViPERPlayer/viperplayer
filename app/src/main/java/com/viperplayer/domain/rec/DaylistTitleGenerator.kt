package com.viperplayer.domain.rec

import java.time.DayOfWeek

/**
 * The generated naming for one daylist slot: a lowercase a streaming service-style [title] and a short [description].
 *
 * Both are produced purely from the slot's dominant genres, time-of-day bucket, weekday and a stable
 * per-slot seed — no wall-clock or randomness is read inside the generator (see [DaylistTitleGenerator]),
 * so a slot's naming is stable within its window and reproducible in tests.
 */
data class DaylistNaming(
    val title: String,
    val description: String,
)

/**
 * The pure, deterministic, on-device generator for a daylist's TITLE and DESCRIPTION.
 *
 * A daylist title reads like `"{energy adjective} {genre/feel phrase} {weekday} {time-of-day}"` — 2–4
 * lowercase descriptors, e.g. `"groovy four-on-the-floor wednesday morning"` or
 * `"dreamy lo-fi beats saturday night"`. Descriptors are picked from curated in-code vocabulary banks
 * keyed by the [TimeBucket] and by the slot's DOMINANT genres, with a caller-supplied `daySeed`
 * (typically `epochDay * 4 + bucket.ordinal`) driving deterministic variety so the same slot always
 * yields the same title but different slots/days rotate through the banks.
 *
 * Everything here is framework-free and side-effect-free: no `Random()`, no `System.currentTimeMillis()`,
 * no Android types. The only inputs are the arguments. That makes it directly unit-testable and keeps a
 * slot's naming perfectly stable within its window.
 *
 * i18n note: a combinatorially-generated title cannot be meaningfully localized, so the vocabulary banks
 * below are intentionally an **English-only** in-code bank (documented as such). The host app localizes
 * only the FIXED surrounding labels ("Daylist", the play-all label, the description format), not these
 * generated phrases.
 */
object DaylistTitleGenerator {

    /**
     * Generates the [DaylistNaming] for a slot from its [dominantGenres] (most-frequent first), the
     * time-of-day [bucket], the [dayOfWeek], and a stable [daySeed] that drives deterministic variety.
     *
     * The title is assembled as `{energy adjective} {genre phrase} {weekday} {time-of-day}` with the
     * energy adjective and, when genres are unknown, a mood phrase both seed-rotated within their bank.
     * Empty/unknown genres degrade gracefully to a mood-only or "your {weekday} {time} mix" title rather
     * than emitting a broken phrase. The whole title is lowercased. Never throws.
     */
    fun generate(
        dominantGenres: List<String>,
        bucket: TimeBucket,
        dayOfWeek: DayOfWeek,
        daySeed: Long,
    ): DaylistNaming {
        val weekday = weekdayWord(dayOfWeek)
        val timeOfDay = TIME_OF_DAY.getValue(bucket)
        val energyBank = ENERGY_ADJ.getValue(bucket)
        val energy = energyBank.pick(daySeed)

        // Map the dominant genres to feel phrases, de-duping (several genres can share a phrase, e.g.
        // house + techno + disco all → "four-on-the-floor"), keeping the dominant-first order.
        val genrePhrases = dominantGenres
            .asSequence()
            .map { genrePhrase(it) }
            .filterNotNull()
            .distinct()
            .toList()

        val title: String = if (genrePhrases.isEmpty()) {
            // No usable genre signal: fall back to a mood-only phrase, or a plain "your … mix".
            val mood = MOOD_PHRASE.pick(daySeed)
            if (mood.isBlank()) {
                "your $weekday $timeOfDay mix"
            } else {
                "$energy $mood $weekday $timeOfDay"
            }
        } else {
            // Pick ONE dominant genre phrase. The index is a deterministic function of the seed, so it is
            // stable within a slot (the title never changes mid-window) but rotates across the top few
            // phrases from slot to slot / day to day when the songs span several genres.
            val phrase = genrePhrases[(seedIndex(daySeed) % genrePhrases.size)]
            "$energy $phrase $weekday $timeOfDay"
        }

        val description = description(dominantGenres.firstOrNull(), bucket, daySeed)
        return DaylistNaming(title.lowercase().collapseSpaces(), description)
    }

    /**
     * A short one-line description for the slot, chosen from a few per-bucket templates (seed-rotated),
     * optionally naming the [topGenre]. Kept as a plain English sentence — the host app may wrap it in a
     * localized FORMAT string, but the generated genre/mood wording stays English (see the class note).
     */
    private fun description(topGenre: String?, bucket: TimeBucket, daySeed: Long): String {
        val bank = DESCRIPTION.getValue(bucket)
        val template = bank.pick(daySeed)
        val genreLabel = topGenre?.trim()?.takeIf { it.isNotEmpty() }?.lowercase()
        return if (genreLabel != null && template.contains(GENRE_TOKEN)) {
            template.replace(GENRE_TOKEN, genreLabel)
        } else {
            // Template without a genre slot, or no genre to name → strip any leftover token gracefully.
            template.replace(GENRE_TOKEN, "").collapseSpaces()
        }
    }

    /** Maps a raw genre name (case-insensitive, fuzzy substring) to a curated feel phrase, or null. */
    private fun genrePhrase(genre: String): String? {
        val g = genre.trim().lowercase()
        if (g.isEmpty()) return null
        // Exact-key hit first, then a substring scan so "deep house" / "chicago house" still map.
        GENRE_PHRASE[g]?.let { return it }
        for ((key, phrase) in GENRE_PHRASE) {
            if (g.contains(key)) return phrase
        }
        // Unknown genre: use the raw (lowercased) genre itself as the feel phrase — never a broken title.
        return g
    }

    /** Deterministically picks an element of this bank from [seed] (empty bank → empty string). */
    private fun List<String>.pick(seed: Long): String =
        if (isEmpty()) "" else this[(seedIndex(seed) % size)]

    /** A non-negative index derived from [seed] (guards against a negative modulo on `Long.MIN`). */
    private fun seedIndex(seed: Long): Int {
        val m = seed % Int.MAX_VALUE
        return ((m + Int.MAX_VALUE) % Int.MAX_VALUE).toInt()
    }

    /** Collapses runs of whitespace and trims — keeps a token-stripped template tidy. */
    private fun String.collapseSpaces(): String = trim().replace(WHITESPACE_RUN, " ")

    private fun weekdayWord(day: DayOfWeek): String = when (day) {
        DayOfWeek.MONDAY -> "monday"
        DayOfWeek.TUESDAY -> "tuesday"
        DayOfWeek.WEDNESDAY -> "wednesday"
        DayOfWeek.THURSDAY -> "thursday"
        DayOfWeek.FRIDAY -> "friday"
        DayOfWeek.SATURDAY -> "saturday"
        DayOfWeek.SUNDAY -> "sunday"
    }

    // --- vocabulary banks (English-only; see class KDoc) -------------------------------------------

    /** Placeholder in a description template that is replaced with the top genre (or stripped). */
    private const val GENRE_TOKEN = "{genre}"

    private val WHITESPACE_RUN = Regex("\\s+")

    /** Energy/mood adjectives keyed by time-of-day bucket — the leading descriptor of the title. */
    private val ENERGY_ADJ: Map<TimeBucket, List<String>> = mapOf(
        TimeBucket.MORNING to listOf("fresh", "bright", "mellow", "easy", "sunny"),
        TimeBucket.AFTERNOON to listOf("upbeat", "breezy", "feel-good", "bouncy", "steady"),
        TimeBucket.EVENING to listOf("groovy", "warm", "golden", "smooth", "unwinding"),
        TimeBucket.NIGHT to listOf("late-night", "hazy", "dreamy", "moody", "after-hours"),
    )

    /** Time-of-day words keyed by bucket — the trailing descriptor of the title. */
    private val TIME_OF_DAY: Map<TimeBucket, String> = mapOf(
        TimeBucket.MORNING to "morning",
        TimeBucket.AFTERNOON to "afternoon",
        TimeBucket.EVENING to "evening",
        TimeBucket.NIGHT to "late night",
    )

    /**
     * Mood phrases used when there is no usable genre signal — a graceful, still-flavorful fallback so
     * the title doesn't collapse to a bare "your … mix" every time.
     */
    private val MOOD_PHRASE: List<String> = listOf(
        "vibes", "mix", "soundtrack", "rotation", "selects",
    )

    /**
     * Common genre → curated feel-phrase mapping. Keys are lowercase; the lookup is exact-then-substring
     * (so "deep house", "melodic techno", "west coast hip hop" still resolve). Unknown genres fall back
     * to the raw genre name in [genrePhrase].
     */
    private val GENRE_PHRASE: Map<String, String> = linkedMapOf(
        // four-on-the-floor dance family
        "house" to "four-on-the-floor",
        "techno" to "four-on-the-floor",
        "disco" to "four-on-the-floor",
        "dance" to "four-on-the-floor",
        "edm" to "four-on-the-floor",
        "electronic" to "electronic",
        // lo-fi / chill
        "lo-fi" to "lo-fi beats",
        "lofi" to "lo-fi beats",
        "chillhop" to "lo-fi beats",
        "chill" to "chill",
        // hip hop family
        "hip hop" to "boom bap",
        "hip-hop" to "boom bap",
        "hiphop" to "boom bap",
        "rap" to "boom bap",
        "trap" to "trap",
        // guitar / band music
        "rock" to "guitar-driven",
        "punk" to "guitar-driven",
        "grunge" to "guitar-driven",
        "indie" to "indie",
        "alternative" to "alt",
        "metal" to "heavy",
        "hardcore" to "heavy",
        // roots / acoustic
        "folk" to "acoustic",
        "acoustic" to "acoustic",
        "country" to "country",
        "singer-songwriter" to "songwriter",
        // jazz / soul
        "jazz" to "jazz",
        "blues" to "blues",
        "soul" to "smooth soul",
        "r&b" to "smooth soul",
        "rnb" to "smooth soul",
        "funk" to "funky",
        // atmospheric
        "ambient" to "ambient",
        "classical" to "classical",
        "instrumental" to "instrumental",
        // pop / other
        "pop" to "pop",
        "k-pop" to "pop",
        "reggae" to "reggae",
        "latin" to "latin",
        "afrobeat" to "afrobeat",
        "afrobeats" to "afrobeat",
    )

    /**
     * Per-bucket description templates. A template may contain the [GENRE_TOKEN], which is replaced with
     * the top genre when there is one (else the token is stripped and spacing collapsed). Seed-rotated.
     */
    private val DESCRIPTION: Map<TimeBucket, List<String>> = mapOf(
        TimeBucket.MORNING to listOf(
            "An easy mix to ease into your morning.",
            "Bright {genre} to start the day.",
            "A gentle wake-up mix picked for your morning.",
        ),
        TimeBucket.AFTERNOON to listOf(
            "An upbeat mix to power through your afternoon.",
            "Feel-good {genre} for the middle of the day.",
            "Your afternoon pick-me-up, tuned to your taste.",
        ),
        TimeBucket.EVENING to listOf(
            "A warm mix to unwind into your evening.",
            "Groovy {genre} for the golden hour.",
            "Settle into the evening with songs picked for you.",
        ),
        TimeBucket.NIGHT to listOf(
            "A late-night mix to end the day on.",
            "Hazy {genre} for the small hours.",
            "Wind down into the night with your taste.",
        ),
    )
}
