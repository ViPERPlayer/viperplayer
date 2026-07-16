package com.viperplayer.data.lyrics

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * Instrumentation test for the real ICU-backed [IcuTransliterator] (`android.icu.text.Transliterator`),
 * which only exists on-device. Asserts that representative non-Latin scripts romanize to something
 * recognizable, and that pure-Latin text is left alone.
 */
@RunWith(AndroidJUnit4::class)
class IcuTransliteratorTest {

    private val transliterator = IcuTransliterator()

    private fun romanize(text: String) =
        transliterator.transliterate(text).lowercase(Locale.ROOT)

    @Test
    fun japaneseHiragana_romanizesToKonnichiwa() {
        // ICU may render as "konnichiha"/"kon'nichiwa"; assert the stable prefix.
        assertTrue(romanize("こんにちは").startsWith("kon"))
    }

    @Test
    fun cyrillic_romanizesToPrivet() {
        assertEquals("privet", romanize("Привет"))
    }

    @Test
    fun greek_romanizesToLatin() {
        // "καλημέρα" (good morning) -> latin, contains "kal".
        assertTrue(romanize("καλημέρα").contains("kal"))
    }

    @Test
    fun chineseHan_romanizesToPinyinLike() {
        // 你好 -> nihao/ni hao (Latin-ASCII strips tone marks). Assert it starts Latin "ni".
        assertTrue(romanize("你好").replace(" ", "").startsWith("ni"))
    }

    @Test
    fun korean_romanizesToLatin() {
        val out = romanize("안녕")
        // Should now be plain ASCII Latin letters, no Hangul left.
        assertTrue(out.isNotBlank())
        assertTrue(out.all { it.code < 128 })
    }

    @Test
    fun plainLatin_isUnchanged() {
        assertEquals("hello world", romanize("Hello World"))
    }
}
