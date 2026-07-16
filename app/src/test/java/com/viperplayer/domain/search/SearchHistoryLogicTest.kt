package com.viperplayer.domain.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [SearchHistoryLogic]: the recent-search ordering, case-insensitive + whitespace
 * dedupe, cap enforcement, blank handling, remove, clear, and query normalization.
 */
class SearchHistoryLogicTest {

    @Test
    fun add_prependsNewQueryMostRecentFirst() {
        val result = SearchHistoryLogic.add(listOf("beatles", "queen"), "pink floyd")
        assertEquals(listOf("pink floyd", "beatles", "queen"), result)
    }

    @Test
    fun add_movesExistingEntryToFront() {
        val result = SearchHistoryLogic.add(listOf("beatles", "queen", "abba"), "queen")
        assertEquals(listOf("queen", "beatles", "abba"), result)
    }

    @Test
    fun add_dedupesCaseInsensitively_keepingNewestSpelling() {
        val result = SearchHistoryLogic.add(listOf("Beatles", "queen"), "BEATLES")
        assertEquals(listOf("BEATLES", "queen"), result)
        // Only one entry for the beatles regardless of case.
        assertEquals(1, result.count { it.equals("beatles", ignoreCase = true) })
    }

    @Test
    fun add_dedupesAcrossWhitespaceDifferences() {
        val result = SearchHistoryLogic.add(listOf("pink floyd"), "  pink   floyd  ")
        assertEquals(listOf("pink floyd"), result)
    }

    @Test
    fun add_blankIsIgnored() {
        val current = listOf("beatles")
        assertEquals(current, SearchHistoryLogic.add(current, ""))
        assertEquals(current, SearchHistoryLogic.add(current, "   "))
        assertEquals(current, SearchHistoryLogic.add(current, "\t\n "))
    }

    @Test
    fun add_normalizesStoredSpelling() {
        val result = SearchHistoryLogic.add(emptyList(), "  the   who  ")
        assertEquals(listOf("the who"), result)
    }

    @Test
    fun add_enforcesCap_droppingOldest() {
        var history = emptyList<String>()
        for (i in 1..5) {
            history = SearchHistoryLogic.add(history, "q$i", cap = 3)
        }
        // Only the 3 most recent survive, newest first.
        assertEquals(listOf("q5", "q4", "q3"), history)
    }

    @Test
    fun add_capCountsAfterDedupe() {
        val history = SearchHistoryLogic.add(listOf("a", "b", "c"), "b", cap = 3)
        // Re-adding an existing entry must not grow past the cap.
        assertEquals(listOf("b", "a", "c"), history)
    }

    @Test
    fun remove_dropsEntryCaseInsensitively() {
        val result = SearchHistoryLogic.remove(listOf("Beatles", "queen"), "beatles")
        assertEquals(listOf("queen"), result)
    }

    @Test
    fun remove_missingEntryLeavesListUnchanged() {
        val current = listOf("beatles", "queen")
        assertEquals(current, SearchHistoryLogic.remove(current, "abba"))
    }

    @Test
    fun remove_blankLeavesListUnchanged() {
        val current = listOf("beatles")
        assertEquals(current, SearchHistoryLogic.remove(current, "   "))
    }

    @Test
    fun clear_returnsEmpty() {
        assertTrue(SearchHistoryLogic.clear().isEmpty())
    }

    @Test
    fun normalize_trimsAndCollapsesWhitespace() {
        assertEquals("pink floyd", SearchHistoryLogic.normalize("  pink   floyd  "))
        assertEquals("a b c", SearchHistoryLogic.normalize("a\t b\n  c"))
        assertEquals("", SearchHistoryLogic.normalize("   "))
    }

    @Test
    fun dedupeKey_isNormalizedAndLowercased() {
        assertEquals("pink floyd", SearchHistoryLogic.dedupeKey("  Pink   FLOYD "))
        assertEquals(
            SearchHistoryLogic.dedupeKey("Queen"),
            SearchHistoryLogic.dedupeKey("  queen  ")
        )
    }

    @Test
    fun isBlank_reflectsNormalizedEmptiness() {
        assertTrue(SearchHistoryLogic.isBlank("   "))
        assertTrue(SearchHistoryLogic.isBlank("\t\n"))
        assertFalse(SearchHistoryLogic.isBlank(" a "))
    }
}
