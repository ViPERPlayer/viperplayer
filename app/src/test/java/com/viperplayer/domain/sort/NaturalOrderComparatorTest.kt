package com.viperplayer.domain.sort

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Unit tests for [NaturalOrderComparator]: numeric-aware chunks, case/accent insensitivity, leading
 * articles, nulls, and the ordering invariants the sort framework relies on.
 */
class NaturalOrderComparatorTest {

    private val comparator = NaturalOrderComparator(Locale.ENGLISH)

    private fun sorted(vararg values: String?): List<String?> =
        values.toList().sortedWith(comparator)

    // --- numeric chunks ---

    @Test
    fun numericChunk_track2_sortsBeforeTrack10() {
        assertTrue(comparator.compare("Track 2", "Track 10") < 0)
        assertTrue(comparator.compare("Track 10", "Track 2") > 0)
    }

    @Test
    fun numericChunk_noSpace_track2_sortsBeforeTrack10() {
        // "Track2" vs "Track10" — the digit run is compared by value even without a separator.
        assertTrue(comparator.compare("Track2", "Track10") < 0)
    }

    @Test
    fun numericChunk_fullNaturalOrdering() {
        assertEquals(
            listOf("Track 1", "Track 2", "Track 9", "Track 10", "Track 100"),
            sorted("Track 10", "Track 1", "Track 100", "Track 9", "Track 2"),
        )
    }

    @Test
    fun numericChunk_leadingZerosCompareByValue() {
        // "01" and "1" are equal in value, so neither sorts before "Agent 2" (value 2). A naive string
        // compare would wrongly wedge "Agent 1"/"Agent 01" on opposite sides. The zero-padded and bare
        // forms stay adjacent (value-equal), just before value 2.
        val result = sorted("Agent 2", "Agent 01", "Agent 1")
        assertEquals("Agent 2", result[2])
        // The two value-1 entries occupy the first two slots in some deterministic order.
        assertEquals(setOf("Agent 1", "Agent 01"), setOf(result[0], result[1]))
    }

    @Test
    fun numericChunk_pureNumbers() {
        assertEquals(listOf("2", "10", "100"), sorted("100", "2", "10"))
    }

    @Test
    fun numericChunk_veryLongNumbersDoNotOverflow() {
        val big = "9".repeat(40)
        val bigger = "9".repeat(41)
        assertTrue(comparator.compare(big, bigger) < 0)
    }

    // --- case / mixed case ---

    @Test
    fun mixedCase_isCaseInsensitiveForPrimaryOrder() {
        // Case differences fold at the primary level, so "apple"/"APPLE" stay together ahead of
        // "banana" and "Cherry" (a case-sensitive sort would scatter the uppercase forms).
        val result = sorted("banana", "APPLE", "apple", "Cherry")
        assertEquals(setOf("apple", "APPLE"), setOf(result[0], result[1]))
        assertEquals("banana", result[2])
        assertEquals("Cherry", result[3])
    }

    @Test
    fun mixedCase_lowerAndUpperAreAdjacentNotInterleaved() {
        // "aXP" must not wedge between "apple"/"APPLE"; case differences fold, so those two stay together.
        val result = sorted("apple", "aXP", "APPLE")
        assertEquals("aXP", result[2])
    }

    // --- locale / accents ---

    @Test
    fun accents_areFoldedForPrimaryOrder() {
        // Accents fold at the primary level, so "résumé" sorts with "resume" (between "quilt" and
        // "sun") rather than being pushed out of alphabetical position by its diacritics.
        val result = sorted("sun", "résumé", "quilt")
        assertEquals(listOf("quilt", "résumé", "sun"), result)
    }

    @Test
    fun locale_swedish_ordersAaAfterZ() {
        // In Swedish, 'ä' collates after 'z'; in English it would fold near 'a'.
        val swedish = NaturalOrderComparator(Locale.forLanguageTag("sv"))
        assertTrue(swedish.compare("z", "ä") < 0)
    }

    @Test
    fun locale_isHonoredPerInstance_swedishVsUsForAWithRing() {
        // Issue #2: locale must be resolved per comparator instance, not frozen process-wide. In Swedish
        // 'å' collates AFTER 'z'; in US English it folds near 'a' (before 'z'). Two instances built for
        // different locales must therefore disagree on the same pair.
        val swedish = NaturalOrderComparator(Locale.forLanguageTag("sv"))
        val us = NaturalOrderComparator(Locale.US)
        // Swedish: 'å' after 'z' → "z" < "å".
        assertTrue(swedish.compare("z", "å") < 0)
        // US: 'å' near 'a' → "z" > "å" (i.e. 'å' does NOT sort after 'z').
        assertTrue(us.compare("z", "å") > 0)
    }

    // --- whole-run collation: contractions & astral code points (issue #1) ---

    @Test
    fun wholeRunCollation_germanEszettContractsWithSs_tiesOnPrimaryThenRawFallback() {
        // "ß" ↔ "ss" is a locale contraction: as WHOLE strings the collator treats "straße" and
        // "strasse" as equal at the primary level (a per-char compare would wrongly split them). The
        // comparator must therefore tie on the primary key and defer to the deterministic raw-string
        // fallback — never report them as differing on collation alone.
        val german = NaturalOrderComparator(Locale.GERMAN)
        // The primary key ties (contraction), so compare() must equal the raw-string fallback exactly.
        // ('ß' U+00DF > 's' U+0073 at the 5th char, so the raw compare is positive — but we assert
        // against the computed value so the test states the tie-break behaviour, not a magic sign.)
        val raw = "straße".compareTo("strasse")
        assertEquals(raw, german.compare("straße", "strasse"))
        // And it is stable/total: the reverse call yields the negated result.
        assertEquals(-raw, german.compare("strasse", "straße"))
        // Both orderings are consistent — sorting the pair is deterministic regardless of input order.
        assertEquals(
            listOf("straße", "strasse").sortedWith(german),
            listOf("strasse", "straße").sortedWith(german),
        )
    }

    @Test
    fun wholeRunCollation_astralEmojiOrdersDeterministicallyWithoutCrashing() {
        // Astral code points (emoji) are surrogate pairs; a per-char collate would slice them into lone
        // surrogates. Whole-run collation keeps them intact, so an emoji title pair orders deterministically.
        val a = "Party 😀" // "Party 😀" (U+1F600)
        val b = "Party 😁" // "Party 😁" (U+1F601)
        val forward = comparator.compare(a, b)
        val backward = comparator.compare(b, a)
        // Deterministic and antisymmetric, and never zero for two distinct emoji.
        assertTrue(forward != 0)
        assertEquals(forward < 0, backward > 0)
        // Sorting a list of astral-suffixed titles is stable regardless of input order.
        assertEquals(sorted(a, b), sorted(b, a))
    }

    @Test
    fun wholeRunCollation_reconfirmsNumericCasesStillHold() {
        // Re-confirm the numeric-alignment invariants survive the run-based rewrite.
        assertTrue(comparator.compare("Track 2", "Track 10") < 0)   // 2 < 10 by value
        assertEquals(0, compareNaturalKey("007", "7"))              // leading zeros: value-equal
        assertTrue(comparator.compare("2 Unlimited", "Two Door") < 0) // digits before letters
        assertEquals(
            listOf("Track 1", "Track 2", "Track 9", "Track 10", "Track 100"),
            sorted("Track 10", "Track 1", "Track 100", "Track 9", "Track 2"),
        )
    }

    /**
     * True primary-key equality helper: two values whose only difference is the raw-string tie-break
     * are "coll-equal". "007"/"7" are value-equal digit runs, so the comparator's non-zero result comes
     * purely from the raw fallback; strip that to observe the collation verdict.
     */
    private fun compareNaturalKey(a: String, b: String): Int {
        val full = comparator.compare(a, b)
        val raw = a.compareTo(b)
        // If the only signal is the raw fallback, the primary keys tied (return 0); else report full.
        return if (full == raw && a != b) 0 else full
    }

    // --- leading articles ---

    @Test
    fun leadingArticle_the_isIgnoredForPrimaryOrder() {
        // "The Beatles" files under B, so it sorts between "Beach House" and "Coldplay".
        assertEquals(
            listOf("Beach House", "The Beatles", "Coldplay"),
            sorted("Coldplay", "The Beatles", "Beach House"),
        )
    }

    @Test
    fun leadingArticle_aAndAn_areStripped() {
        assertEquals(
            listOf("An Ocean", "A Sky", "Zebra"),
            sorted("Zebra", "A Sky", "An Ocean"),
        )
    }

    @Test
    fun leadingArticle_strippedButBrokenByFullStringOnTie() {
        // "The Cure" strips to "Cure" and ties with the bare "Cure"; the fallback keeps a stable order.
        val result = sorted("The Cure", "Cure")
        assertEquals(2, result.size)
        // Bare "Cure" < "The Cure" via the raw-string fallback.
        assertEquals(listOf("Cure", "The Cure"), result)
    }

    @Test
    fun leadingArticle_wordStartingWithTheIsNotStripped() {
        // "Theremin" must not lose "The" — only the standalone article "The " (with a trailing space)
        // is stripped. So "Theremin" keeps its full 'The...' and sorts after "Sun" but NOT under 'R'.
        val result = sorted("Theremin", "The Zoo", "Sun")
        // "The Zoo" → "Zoo" (Z); "Theremin" stays 'T'; "Sun" is 'S'. Order: Sun, Theremin, Zoo.
        assertEquals(listOf("Sun", "Theremin", "The Zoo"), result)
    }

    @Test
    fun leadingArticle_canBeDisabled() {
        val keepArticles = NaturalOrderComparator(Locale.ENGLISH, stripLeadingArticles = false)
        // With articles kept, "The Beatles" (T) sorts after "Coldplay" (C).
        assertTrue(keepArticles.compare("The Beatles", "Coldplay") > 0)
    }

    // --- nulls / empty ---

    @Test
    fun nulls_sortFirstAndAreEqualToEachOther() {
        assertEquals(0, comparator.compare(null, null))
        assertTrue(comparator.compare(null, "anything") < 0)
        assertTrue(comparator.compare("anything", null) > 0)
    }

    @Test
    fun nulls_inAListSortToTheFront() {
        assertEquals(listOf(null, "Alpha", "Beta"), sorted("Beta", null, "Alpha"))
    }

    @Test
    fun empty_stringSortsBeforeNonEmpty() {
        assertTrue(comparator.compare("", "a") < 0)
        assertEquals(0, comparator.compare("", ""))
    }

    // --- digits before letters within a chunk ---

    @Test
    fun digitsSortBeforeLetters() {
        // "2 Unlimited" (starts with a digit) sorts before "Two Door Cinema Club".
        assertTrue(comparator.compare("2 Unlimited", "Two Door Cinema Club") < 0)
    }

    // --- stability / totality sanity ---

    @Test
    fun comparator_isReflexive() {
        assertEquals(0, comparator.compare("Anything 42", "Anything 42"))
    }
}
