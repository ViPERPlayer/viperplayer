package com.viperplayer.presentation.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure lyrics motion math: centering-offset computation, per-word progress
 * fraction (before/within/after a word plus zero-length guards), and the auto-scroll pause/resume
 * decision. These lock down the framework-free logic backing the animated lyrics renderer.
 */
class LyricsScrollMathTest {

    // --- centeredScrollOffsetPx ---

    @Test
    fun centeredScrollOffset_centersItemInViewport() {
        // 1000px viewport, 200px line -> top should sit at (1000-200)/2 = 400.
        assertEquals(400, LyricsScrollMath.centeredScrollOffsetPx(viewportHeightPx = 1000, itemHeightPx = 200))
    }

    @Test
    fun centeredScrollOffset_zeroHeightItem_halvesViewport() {
        assertEquals(500, LyricsScrollMath.centeredScrollOffsetPx(viewportHeightPx = 1000, itemHeightPx = 0))
    }

    @Test
    fun centeredScrollOffset_itemTallerThanViewport_pinsToTop() {
        // A line taller than the viewport pins its top to the viewport top (offset 0).
        assertEquals(0, LyricsScrollMath.centeredScrollOffsetPx(viewportHeightPx = 300, itemHeightPx = 500))
    }

    @Test
    fun centeredScrollOffset_itemEqualsViewport_pinsToTop() {
        assertEquals(0, LyricsScrollMath.centeredScrollOffsetPx(viewportHeightPx = 400, itemHeightPx = 400))
    }

    @Test
    fun centeredScrollOffset_nonPositiveViewport_isZero() {
        assertEquals(0, LyricsScrollMath.centeredScrollOffsetPx(viewportHeightPx = 0, itemHeightPx = 100))
        assertEquals(0, LyricsScrollMath.centeredScrollOffsetPx(viewportHeightPx = -50, itemHeightPx = 100))
    }

    // --- wordProgressFraction ---

    @Test
    fun wordProgress_beforeWordStart_isZero() {
        assertEquals(0f, LyricsScrollMath.wordProgressFraction(positionMs = 500, wordStartMs = 1000, wordEndMs = 2000), 0.0001f)
    }

    @Test
    fun wordProgress_atWordStart_isZero() {
        assertEquals(0f, LyricsScrollMath.wordProgressFraction(positionMs = 1000, wordStartMs = 1000, wordEndMs = 2000), 0.0001f)
    }

    @Test
    fun wordProgress_midWord_isLinearFraction() {
        // Halfway between 1000 and 2000.
        assertEquals(0.5f, LyricsScrollMath.wordProgressFraction(positionMs = 1500, wordStartMs = 1000, wordEndMs = 2000), 0.0001f)
        // Quarter of the way.
        assertEquals(0.25f, LyricsScrollMath.wordProgressFraction(positionMs = 1250, wordStartMs = 1000, wordEndMs = 2000), 0.0001f)
    }

    @Test
    fun wordProgress_atWordEnd_isOne() {
        assertEquals(1f, LyricsScrollMath.wordProgressFraction(positionMs = 2000, wordStartMs = 1000, wordEndMs = 2000), 0.0001f)
    }

    @Test
    fun wordProgress_afterWordEnd_isOne() {
        assertEquals(1f, LyricsScrollMath.wordProgressFraction(positionMs = 5000, wordStartMs = 1000, wordEndMs = 2000), 0.0001f)
    }

    @Test
    fun wordProgress_zeroLengthWord_beforeStartIsZero_afterIsOne() {
        // start == end: instantaneous word, never divides by zero.
        assertEquals(0f, LyricsScrollMath.wordProgressFraction(positionMs = 900, wordStartMs = 1000, wordEndMs = 1000), 0.0001f)
        assertEquals(1f, LyricsScrollMath.wordProgressFraction(positionMs = 1001, wordStartMs = 1000, wordEndMs = 1000), 0.0001f)
    }

    @Test
    fun wordProgress_negativeLengthWord_isGuarded() {
        // end < start (malformed timing): treated as instantaneous, no crash, no fraction > 1.
        // Before the (later) start -> 0; at/after the start -> 1.
        assertEquals(0f, LyricsScrollMath.wordProgressFraction(positionMs = 1900, wordStartMs = 2000, wordEndMs = 1000), 0.0001f)
        assertEquals(1f, LyricsScrollMath.wordProgressFraction(positionMs = 2500, wordStartMs = 2000, wordEndMs = 1000), 0.0001f)
    }

    // --- shouldAutoScroll ---

    @Test
    fun shouldAutoScroll_false_whenDisabled() {
        assertFalse(
            LyricsScrollMath.shouldAutoScroll(
                autoScrollEnabled = false,
                lastUserInteractionMs = -1,
                nowMs = 10_000,
                resumeDelayMs = 2500,
            )
        )
    }

    @Test
    fun shouldAutoScroll_true_whenEnabledAndNeverInteracted() {
        assertTrue(
            LyricsScrollMath.shouldAutoScroll(
                autoScrollEnabled = true,
                lastUserInteractionMs = -1,
                nowMs = 10_000,
                resumeDelayMs = 2500,
            )
        )
    }

    @Test
    fun shouldAutoScroll_false_duringQuietPeriodAfterDrag() {
        // Dragged 1s ago, resume delay is 2.5s -> still paused.
        assertFalse(
            LyricsScrollMath.shouldAutoScroll(
                autoScrollEnabled = true,
                lastUserInteractionMs = 9_000,
                nowMs = 10_000,
                resumeDelayMs = 2500,
            )
        )
    }

    @Test
    fun shouldAutoScroll_true_afterQuietPeriodElapses() {
        // Dragged 3s ago, resume delay is 2.5s -> resumed.
        assertTrue(
            LyricsScrollMath.shouldAutoScroll(
                autoScrollEnabled = true,
                lastUserInteractionMs = 7_000,
                nowMs = 10_000,
                resumeDelayMs = 2500,
            )
        )
    }

    @Test
    fun shouldAutoScroll_true_exactlyAtResumeBoundary() {
        // now - last == delay -> resumes (>= boundary).
        assertTrue(
            LyricsScrollMath.shouldAutoScroll(
                autoScrollEnabled = true,
                lastUserInteractionMs = 7_500,
                nowMs = 10_000,
                resumeDelayMs = 2500,
            )
        )
    }
}
