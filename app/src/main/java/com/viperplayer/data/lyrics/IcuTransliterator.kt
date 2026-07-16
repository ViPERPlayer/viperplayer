package com.viperplayer.data.lyrics

import android.icu.text.Transliterator
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ICU4J-backed [Transliterating] implementation (`android.icu.text.Transliterator`, available on the
 * app's minSdk 26). Uses a single chained "Any-Latin; Latin-ASCII" transform which auto-detects the
 * source script per run of text, so a mixed-script line (e.g. Japanese Kanji + Katakana) romanizes
 * correctly in one pass. `Latin-ASCII` folds the result to plain ASCII where possible (e.g. pinyin
 * tone marks) for easier read-along; any failure returns the original text unchanged.
 *
 * Handles Japanese (Hiragana/Katakana/Kanji -> romaji), Korean (Hangul -> romaja), Chinese (Han ->
 * pinyin), Cyrillic -> Latin, Greek -> Latin, and more via ICU's built-in script tables.
 */
@Singleton
class IcuTransliterator @Inject constructor() : Transliterating {

    // "Any-Latin" transliterates any script to Latin; "Latin-ASCII" then strips diacritics/tone marks.
    // Lazily built and reused; Transliterator instances are not documented thread-safe, so guard use.
    private val transliterator: Transliterator? by lazy {
        runCatching { Transliterator.getInstance("Any-Latin; Latin-ASCII") }.getOrNull()
    }

    private val lock = Any()

    override fun transliterate(text: String): String {
        val t = transliterator ?: return text
        return synchronized(lock) {
            runCatching { t.transliterate(text) }.getOrDefault(text)
        }
    }
}
