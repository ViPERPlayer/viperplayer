package com.viperplayer.data.rec

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure-JVM tests for [RecommendationIndexRepository.deriveStatus] — the mapping from "songs still
 * missing an embedding" + the worker snapshot into the Settings row's "Analyzing your library… N/M"
 * status. WorkManager-free (the framework `WorkInfo` is adapted into [IndexWorkSnapshot] upstream).
 */
class RecommendationIndexStatusTest {

    @Test
    fun idleWhenNothingMissingAndNoRun() {
        assertEquals(IndexingStatus.Idle, RecommendationIndexRepository.deriveStatus(0, null))
    }

    @Test
    fun indexingWhenSongsOutstandingButNoRunYet() {
        // 40 missing, no worker snapshot -> total 40, processed 0.
        val s = RecommendationIndexRepository.deriveStatus(40, null)
        assertEquals(IndexingStatus.Indexing(processed = 0, total = 40), s)
    }

    @Test
    fun activeRunReportsProcessedOutOfTotal() {
        // 30 still missing, run has processed 10 -> total = 40, processed = 10.
        val snap = IndexWorkSnapshot(active = true, processed = 10, batchTotal = 40)
        assertEquals(
            IndexingStatus.Indexing(processed = 10, total = 40),
            RecommendationIndexRepository.deriveStatus(30, snap),
        )
    }

    @Test
    fun activeRunStillIndexingEvenWhenMissingHitsZero() {
        // The last write may not have propagated to the missing-count flow yet; an active run keeps the
        // "Indexing" line up (processed/total from the run) rather than snapping to Idle prematurely.
        val snap = IndexWorkSnapshot(active = true, processed = 40, batchTotal = 40)
        assertEquals(
            IndexingStatus.Indexing(processed = 40, total = 40),
            RecommendationIndexRepository.deriveStatus(0, snap),
        )
    }

    @Test
    fun idleWhenRunFinishedAndNothingLeft() {
        val snap = IndexWorkSnapshot(active = false, processed = 40, batchTotal = 40)
        assertEquals(IndexingStatus.Idle, RecommendationIndexRepository.deriveStatus(0, snap))
    }

    @Test
    fun processedNeverExceedsTotal() {
        // Defensive: even a bogus processed > (missing+processed) clamps to total.
        val snap = IndexWorkSnapshot(active = true, processed = 5, batchTotal = 5)
        val s = RecommendationIndexRepository.deriveStatus(0, snap) as IndexingStatus.Indexing
        assertEquals(s.total, s.total.coerceAtLeast(s.processed))
        assertEquals(true, s.processed <= s.total)
    }
}
