package com.viperplayer.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the pure per-song lyrics timing offset logic ([LyricsOffset]): the effective-position
 * computation, clamping, +/- nudging, and — crucially — that shifting the position by the offset
 * selects the correct active synced line via [Lyrics.currentLineIndex]. Framework-free (no Compose,
 * Android or coroutines).
 */
class LyricsOffsetTest {

    private val lyrics = Lyrics(
        synced = true,
        lines = listOf(
            LyricsLine(startMs = 0, text = "line0"),
            LyricsLine(startMs = 1000, text = "line1"),
            LyricsLine(startMs = 2000, text = "line2"),
            LyricsLine(startMs = 3000, text = "line3"),
        ),
        plainText = null,
    )

    // --- effectivePosition ---

    @Test
    fun effectivePosition_addsOffset() {
        assertEquals(1500L, LyricsOffset.effectivePosition(positionMs = 1000, offsetMs = 500))
    }

    @Test
    fun effectivePosition_negativeOffset_subtracts() {
        assertEquals(700L, LyricsOffset.effectivePosition(positionMs = 1000, offsetMs = -300))
    }

    @Test
    fun effectivePosition_zeroOffset_isIdentity() {
        assertEquals(1000L, LyricsOffset.effectivePosition(positionMs = 1000, offsetMs = 0))
    }

    // --- offset selects the right active line ---

    @Test
    fun noOffset_picksLineAtRawPosition() {
        // At 1200ms with no offset, line1 (starts at 1000) is active.
        val effective = LyricsOffset.effectivePosition(positionMs = 1200, offsetMs = 0)
        assertEquals(1, lyrics.currentLineIndex(effective))
    }

    @Test
    fun positiveOffset_advancesActiveLineEarlier() {
        // Audio at 1200ms; a +900ms offset makes lyrics lead, so effective 2100ms -> line2 active
        // (the lyrics highlight jumps ahead of the audio, the fix for lyrics that lag the track).
        val effective = LyricsOffset.effectivePosition(positionMs = 1200, offsetMs = 900)
        assertEquals(2100L, effective)
        assertEquals(2, lyrics.currentLineIndex(effective))
    }

    @Test
    fun negativeOffset_holdsActiveLineLater() {
        // Audio at 2100ms (line2 by default); a -900ms offset delays lyrics, effective 1200ms -> line1
        // stays active (the fix for lyrics that run ahead of the track).
        val effective = LyricsOffset.effectivePosition(positionMs = 2100, offsetMs = -900)
        assertEquals(1200L, effective)
        assertEquals(1, lyrics.currentLineIndex(effective))
    }

    @Test
    fun largePositiveOffsetNearStart_mayPrecedeFirstLine() {
        // A big positive offset early on can push effective position below the first line's start;
        // currentLineIndex tolerates it (returns -1 before line0).
        val effective = LyricsOffset.effectivePosition(positionMs = 100, offsetMs = -500)
        assertEquals(-400L, effective)
        assertEquals(-1, lyrics.currentLineIndex(effective))
    }

    // --- clamp ---

    @Test
    fun clamp_keepsInRangeValueUnchanged() {
        assertEquals(2500L, LyricsOffset.clamp(2500))
        assertEquals(-2500L, LyricsOffset.clamp(-2500))
    }

    @Test
    fun clamp_boundsRunawayValues() {
        assertEquals(LyricsOffset.MAX_ABS_MS, LyricsOffset.clamp(LyricsOffset.MAX_ABS_MS + 5000))
        assertEquals(-LyricsOffset.MAX_ABS_MS, LyricsOffset.clamp(-LyricsOffset.MAX_ABS_MS - 5000))
    }

    // --- nudged ---

    @Test
    fun nudged_stepsByStepMs() {
        assertEquals(LyricsOffset.STEP_MS, LyricsOffset.nudged(current = 0, steps = 1))
        assertEquals(-LyricsOffset.STEP_MS, LyricsOffset.nudged(current = 0, steps = -1))
        assertEquals(3 * LyricsOffset.STEP_MS, LyricsOffset.nudged(current = LyricsOffset.STEP_MS, steps = 2))
    }

    @Test
    fun nudged_isClampedToRange() {
        // Nudging beyond MAX_ABS_MS is capped rather than overrunning.
        val steps = (LyricsOffset.MAX_ABS_MS / LyricsOffset.STEP_MS).toInt() + 100
        assertEquals(LyricsOffset.MAX_ABS_MS, LyricsOffset.nudged(current = 0, steps = steps))
    }
}
