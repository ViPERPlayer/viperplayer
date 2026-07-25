package com.viperplayer.data.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the pure sleep-timer math (remaining time, fade curve, fade-window detection). */
class SleepTimerPlanTest {

    private val eps = 1e-4f

    // --- remainingMs ---

    @Test
    fun remainingIsEndMinusPosition() {
        assertEquals(30_000L, SleepTimerPlan.remainingMs(positionMs = 10_000, endMs = 40_000))
    }

    @Test
    fun remainingClampsToZeroPastBoundary() {
        assertEquals(0L, SleepTimerPlan.remainingMs(positionMs = 50_000, endMs = 40_000))
    }

    @Test
    fun remainingAtBoundaryIsZero() {
        assertEquals(0L, SleepTimerPlan.remainingMs(positionMs = 40_000, endMs = 40_000))
    }

    // --- fadeVolumeAt ---

    @Test
    fun fullVolumeOutsideFadeWindow() {
        // 20s remaining, 8s fade -> still full volume.
        assertEquals(1f, SleepTimerPlan.fadeVolumeAt(remainingMs = 20_000, fadeMs = 8_000), eps)
    }

    @Test
    fun silentAtBoundary() {
        assertEquals(0f, SleepTimerPlan.fadeVolumeAt(remainingMs = 0, fadeMs = 8_000), eps)
        assertEquals(0f, SleepTimerPlan.fadeVolumeAt(remainingMs = -100, fadeMs = 8_000), eps)
    }

    @Test
    fun linearRampInsideWindow() {
        // Halfway through an 8s fade -> half volume.
        assertEquals(0.5f, SleepTimerPlan.fadeVolumeAt(remainingMs = 4_000, fadeMs = 8_000), eps)
        assertEquals(0.25f, SleepTimerPlan.fadeVolumeAt(remainingMs = 2_000, fadeMs = 8_000), eps)
    }

    @Test
    fun fadeVolumeMonotonicallyDecreasesTowardBoundary() {
        var prev = 2f
        for (remaining in 8_000 downTo 0 step 500) {
            val v = SleepTimerPlan.fadeVolumeAt(remaining.toLong(), fadeMs = 8_000)
            assertTrue("volume must not increase as remaining shrinks (got $v after $prev)", v <= prev)
            prev = v
        }
    }

    @Test
    fun noFadeMeansFullVolumeUntilBoundary() {
        // fadeMs <= 0 -> full volume while any time remains, silent once gone.
        assertEquals(1f, SleepTimerPlan.fadeVolumeAt(remainingMs = 1, fadeMs = 0), eps)
        assertEquals(0f, SleepTimerPlan.fadeVolumeAt(remainingMs = 0, fadeMs = 0), eps)
    }

    // --- isInFadeWindow ---

    @Test
    fun inFadeWindowOnlyWithinLastFadeMs() {
        assertFalse(SleepTimerPlan.isInFadeWindow(remainingMs = 20_000, fadeMs = 8_000))
        assertTrue(SleepTimerPlan.isInFadeWindow(remainingMs = 5_000, fadeMs = 8_000))
        assertTrue(SleepTimerPlan.isInFadeWindow(remainingMs = 8_000, fadeMs = 8_000))
    }

    @Test
    fun notInFadeWindowAtOrPastBoundary() {
        assertFalse(SleepTimerPlan.isInFadeWindow(remainingMs = 0, fadeMs = 8_000))
    }

    @Test
    fun neverInFadeWindowWhenFadeDisabled() {
        assertFalse(SleepTimerPlan.isInFadeWindow(remainingMs = 4_000, fadeMs = 0))
    }
}
