package com.viperplayer.alarm

import com.viperplayer.alarm.domain.AlarmFade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the pure fade-in volume curve. */
class AlarmFadeTest {

    private val eps = 1e-4f

    @Test
    fun silentAtStart() {
        assertEquals(0f, AlarmFade.volumeAt(0, 30_000, 1f), eps)
    }

    @Test
    fun negativeElapsedIsSilent() {
        assertEquals(0f, AlarmFade.volumeAt(-100, 30_000, 1f), eps)
    }

    @Test
    fun reachesTargetAtDuration() {
        assertEquals(1f, AlarmFade.volumeAt(30_000, 30_000, 1f), eps)
    }

    @Test
    fun clampsPastDurationToTarget() {
        assertEquals(0.8f, AlarmFade.volumeAt(60_000, 30_000, 0.8f), eps)
    }

    @Test
    fun halfwayIsHalfTarget() {
        assertEquals(0.5f, AlarmFade.volumeAt(15_000, 30_000, 1f), eps)
        assertEquals(0.3f, AlarmFade.volumeAt(15_000, 30_000, 0.6f), eps)
    }

    @Test
    fun monotonicallyIncreasing() {
        var prev = -1f
        for (t in 0..30_000 step 1_000) {
            val v = AlarmFade.volumeAt(t.toLong(), 30_000, 1f)
            assertTrue("volume must not decrease at t=$t (got $v after $prev)", v >= prev)
            prev = v
        }
    }

    @Test
    fun zeroDurationJumpsToTargetAfterStart() {
        // No fade: silent only exactly at t=0, target immediately after.
        assertEquals(0f, AlarmFade.volumeAt(0, 0, 1f), eps)
        assertEquals(1f, AlarmFade.volumeAt(1, 0, 1f), eps)
    }

    @Test
    fun targetVolumeIsClamped() {
        assertEquals(1f, AlarmFade.volumeAt(30_000, 30_000, 5f), eps)
        assertEquals(0f, AlarmFade.volumeAt(30_000, 30_000, -1f), eps)
    }
}
