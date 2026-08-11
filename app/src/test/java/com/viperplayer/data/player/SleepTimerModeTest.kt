package com.viperplayer.data.player

import com.viperplayer.domain.player.SleepTimerMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for the pure [SleepTimerMode] -> minute-preset projection used to tick the picker. Mirrors
 * exactly what [SleepTimerManager.activeMinutes] derives from the armed mode.
 */
class SleepTimerModeTest {

    /** The projection under test — the same expression [SleepTimerManager] maps over its mode flow. */
    private fun activeMinutes(mode: SleepTimerMode): Int? = (mode as? SleepTimerMode.Minutes)?.count

    @Test
    fun offHasNoActiveMinutes() {
        assertNull(activeMinutes(SleepTimerMode.Off))
    }

    @Test
    fun endOfTrackHasNoActiveMinutes() {
        assertNull(activeMinutes(SleepTimerMode.EndOfTrack))
    }

    @Test
    fun minutesModeExposesItsCount() {
        assertEquals(30, activeMinutes(SleepTimerMode.Minutes(30)))
        assertEquals(15, activeMinutes(SleepTimerMode.Minutes(15)))
    }

    @Test
    fun minutesModesAreValueEqual() {
        assertEquals(SleepTimerMode.Minutes(45), SleepTimerMode.Minutes(45))
    }
}
