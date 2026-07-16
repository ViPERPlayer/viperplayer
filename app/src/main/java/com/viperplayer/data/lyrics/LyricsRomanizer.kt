package com.viperplayer.data.lyrics

/**
 * A thin seam over a text transliteration engine (e.g. ICU's `android.icu.text.Transliterator`).
 * Kept Android-free so the orchestration in [LyricsRomanizer] can be unit-tested with a fake.
 */
interface Transliterating {
    /** Transliterate [text] to Latin script, returning the romanized form (or [text] if it can't). */
    fun transliterate(text: String): String
}

/**
 * Pure, Android-free orchestration for lyrics romanization: decides which lines actually need
 * transliteration (they contain non-Latin script), delegates the heavy lifting to a
 * [Transliterating] engine, and caches per unique line so a repeated line is transliterated once.
 *
 * Lines that are already Latin/ASCII, blank, numeric, or pure punctuation are skipped and reported
 * as `null` in the output so the UI shows nothing beneath them; a line is also reported as `null`
 * when the engine produces nothing useful (blank or unchanged from the input).
 */
class LyricsRomanizer(private val engine: Transliterating) {

    // Cache keyed by the raw line so identical lines (very common in choruses) transliterate once.
    private val cache = HashMap<String, String?>()

    /**
     * Romanize [lines] in order. Each result is the romanized text for that line, or `null` when the
     * line needs no romanization (already Latin / blank / no letters), or when the engine yields
     * nothing useful. Results are cached across calls for the lifetime of this instance.
     */
    fun romanize(lines: List<String>): List<String?> = lines.map { romanizeLine(it) }

    /** Romanize a single [line], using and populating the cache. See [romanize]. */
    fun romanizeLine(line: String): String? {
        if (cache.containsKey(line)) return cache[line]

        val result = if (needsRomanization(line)) {
            engine.transliterate(line)
                .takeIf { it.isNotBlank() && it != line }
        } else {
            null
        }
        cache[line] = result
        return result
    }

    /** Clear the per-line cache (e.g. when moving to a new track). */
    fun clear() = cache.clear()

    companion object {
        /**
         * True when [line] contains at least one letter that is not part of the Latin script — i.e.
         * transliterating it to Latin would actually change something. Pure ASCII/Latin lines, blank
         * lines, and lines with only digits/punctuation return false and are skipped.
         */
        fun needsRomanization(line: String): Boolean {
            if (line.isBlank()) return false
            var i = 0
            while (i < line.length) {
                val cp = line.codePointAt(i)
                if (Character.isLetter(cp) && !isLatinLetter(cp)) return true
                i += Character.charCount(cp)
            }
            return false
        }

        /**
         * True for letters written in the Latin script (ASCII a–z/A–Z plus Latin-1/Extended accented
         * forms like é, ñ, ü). Used to decide a letter does *not* need romanization.
         */
        private fun isLatinLetter(codePoint: Int): Boolean {
            if (!Character.isLetter(codePoint)) return false
            return when (Character.UnicodeBlock.of(codePoint)) {
                Character.UnicodeBlock.BASIC_LATIN,
                Character.UnicodeBlock.LATIN_1_SUPPLEMENT,
                Character.UnicodeBlock.LATIN_EXTENDED_A,
                Character.UnicodeBlock.LATIN_EXTENDED_B,
                Character.UnicodeBlock.LATIN_EXTENDED_ADDITIONAL -> true
                else -> false
            }
        }
    }
}
