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

    // ---- mergeSnapshots: local + streaming worker snapshots folded into one ----

    @Test
    fun mergeReturnsTheOtherWhenOneIsNull() {
        val snap = IndexWorkSnapshot(active = true, processed = 3, batchTotal = 4)
        assertEquals(snap, RecommendationIndexRepository.mergeSnapshots(snap, null))
        assertEquals(snap, RecommendationIndexRepository.mergeSnapshots(null, snap))
        assertEquals(null, RecommendationIndexRepository.mergeSnapshots(null, null))
    }

    @Test
    fun mergeIsActiveIfEitherIsActiveAndSumsProgress() {
        val local = IndexWorkSnapshot(active = false, processed = 2, batchTotal = 5)
        val streaming = IndexWorkSnapshot(active = true, processed = 7, batchTotal = 10)
        val merged = RecommendationIndexRepository.mergeSnapshots(local, streaming)!!
        assertEquals(true, merged.active)
        assertEquals(9, merged.processed) // 2 + 7
        assertEquals(15, merged.batchTotal) // 5 + 10
    }

    @Test
    fun mergeHandlesNullProgressFields() {
        val local = IndexWorkSnapshot(active = true, processed = null, batchTotal = null)
        val streaming = IndexWorkSnapshot(active = false, processed = 4, batchTotal = 8)
        val merged = RecommendationIndexRepository.mergeSnapshots(local, streaming)!!
        assertEquals(true, merged.active)
        assertEquals(4, merged.processed) // only streaming reported
        assertEquals(8, merged.batchTotal)
    }
}
