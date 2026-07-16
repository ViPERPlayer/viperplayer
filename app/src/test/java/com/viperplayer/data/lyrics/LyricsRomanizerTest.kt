package com.viperplayer.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for the pure romanization orchestration ([LyricsRomanizer]) and its script
 * detection ([LyricsRomanizer.needsRomanization]). Uses a fake [Transliterating] so the tests never
 * touch ICU/Android — they verify *which* lines get transliterated, caching, and skip rules.
 */
class LyricsRomanizerTest {

    /** Records every call and returns a marker so we can assert what was (and wasn't) transliterated. */
    private class FakeTransliterator(private val transform: (String) -> String = { "romaji:$it" }) :
        Transliterating {
        val calls = mutableListOf<String>()
        override fun transliterate(text: String): String {
            calls.add(text)
            return transform(text)
        }
    }

    // --- Script detection: non-Latin lines need romanization ---

    @Test
    fun needsRomanization_trueForJapanese() {
        assertTrue(LyricsRomanizer.needsRomanization("こんにちは"))     // Hiragana
        assertTrue(LyricsRomanizer.needsRomanization("カタカナ"))       // Katakana
        assertTrue(LyricsRomanizer.needsRomanization("東京"))          // Kanji
    }

    @Test
    fun needsRomanization_trueForKorean() {
        assertTrue(LyricsRomanizer.needsRomanization("안녕하세요"))
    }

    @Test
    fun needsRomanization_trueForChinese() {
        assertTrue(LyricsRomanizer.needsRomanization("你好世界"))
    }

    @Test
    fun needsRomanization_trueForCyrillic() {
        assertTrue(LyricsRomanizer.needsRomanization("Привет"))
    }

    @Test
    fun needsRomanization_trueForGreek() {
        assertTrue(LyricsRomanizer.needsRomanization("Γειά σου"))
    }

    @Test
    fun needsRomanization_trueForMixedScriptLine() {
        // Latin + Japanese in one line still needs romanization.
        assertTrue(LyricsRomanizer.needsRomanization("Hello 世界"))
    }

    // --- Script detection: Latin / non-letter lines are skipped ---

    @Test
    fun needsRomanization_falseForPlainAscii() {
        assertFalse(LyricsRomanizer.needsRomanization("Hello world"))
    }

    @Test
    fun needsRomanization_falseForAccentedLatin() {
        // Accented Latin (é, ñ, ü) is still Latin script — no romanization needed.
        assertFalse(LyricsRomanizer.needsRomanization("Está mañana über schön"))
    }

    @Test
    fun needsRomanization_falseForBlankOrEmpty() {
        assertFalse(LyricsRomanizer.needsRomanization(""))
        assertFalse(LyricsRomanizer.needsRomanization("   "))
    }

    @Test
    fun needsRomanization_falseForNumericAndPunctuation() {
        assertFalse(LyricsRomanizer.needsRomanization("12345"))
        assertFalse(LyricsRomanizer.needsRomanization("!?.,-—…()"))
        assertFalse(LyricsRomanizer.needsRomanization("(3:45)"))
    }

    // --- Orchestration: romanize() output alignment ---

    @Test
    fun romanize_transliteratesNonLatinAndSkipsLatin() {
        val fake = FakeTransliterator()
        val romanizer = LyricsRomanizer(fake)

        val result = romanizer.romanize(listOf("こんにちは", "Hello", "안녕"))

        assertEquals("romaji:こんにちは", result[0])
        assertNull(result[1])                       // pure Latin -> null (skipped)
        assertEquals("romaji:안녕", result[2])
        // Only the two non-Latin lines were sent to the engine.
        assertEquals(listOf("こんにちは", "안녕"), fake.calls)
    }

    @Test
    fun romanize_blankAndPunctuationLinesAreNull() {
        val fake = FakeTransliterator()
        val romanizer = LyricsRomanizer(fake)

        val result = romanizer.romanize(listOf("", "   ", "♪♪♪", "42"))

        assertEquals(listOf<String?>(null, null, null, null), result)
        assertTrue("engine must not be called for skipped lines", fake.calls.isEmpty())
    }

    @Test
    fun romanize_emptyListYieldsEmpty() {
        val fake = FakeTransliterator()
        assertTrue(LyricsRomanizer(fake).romanize(emptyList()).isEmpty())
    }

    // --- Caching ---

    @Test
    fun romanize_cachesPerUniqueLine_engineCalledOncePerLine() {
        val fake = FakeTransliterator()
        val romanizer = LyricsRomanizer(fake)

        // Same non-Latin line repeated (a chorus) plus a distinct one.
        romanizer.romanize(listOf("さくら", "さくら", "ゆき", "さくら"))

        // Engine hit once per *unique* line, not per occurrence.
        assertEquals(listOf("さくら", "ゆき"), fake.calls)
    }

    @Test
    fun romanize_cacheSurvivesAcrossCalls() {
        val fake = FakeTransliterator()
        val romanizer = LyricsRomanizer(fake)

        romanizer.romanize(listOf("さくら"))
        romanizer.romanize(listOf("さくら"))

        assertEquals(listOf("さくら"), fake.calls)
    }

    @Test
    fun clear_resetsCache() {
        val fake = FakeTransliterator()
        val romanizer = LyricsRomanizer(fake)

        romanizer.romanize(listOf("さくら"))
        romanizer.clear()
        romanizer.romanize(listOf("さくら"))

        // After clear, the line is transliterated again.
        assertEquals(listOf("さくら", "さくら"), fake.calls)
    }

    // --- Engine-returns-nothing-useful handling ---

    @Test
    fun romanize_nullWhenEngineReturnsBlank() {
        val fake = FakeTransliterator { "" }
        val result = LyricsRomanizer(fake).romanize(listOf("東京"))
        assertNull(result[0])
    }

    @Test
    fun romanize_nullWhenEngineReturnsUnchangedText() {
        // Engine echoes the input (couldn't transliterate) -> treated as "no useful romanization".
        val fake = FakeTransliterator { it }
        val result = LyricsRomanizer(fake).romanize(listOf("東京"))
        assertNull(result[0])
    }

    @Test
    fun romanizeLine_singleLineMatchesListBehavior() {
        val fake = FakeTransliterator()
        val romanizer = LyricsRomanizer(fake)

        assertEquals("romaji:모모", romanizer.romanizeLine("모모"))
        assertNull(romanizer.romanizeLine("plain latin"))
    }
}
