package com.viperplayer.alarm

import com.viperplayer.alarm.data.AlarmEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek

/**
 * Unit tests for the pure days-of-week bitmask conversion used to persist an alarm's repeat set.
 * (No Android types involved — the [MediaId]-based content encoding is covered by an instrumented
 * test since it depends on android.net.Uri.)
 */
class AlarmDaysMaskTest {

    @Test
    fun emptySetIsZeroMask() {
        assertEquals(0, AlarmEntity.daysToMask(emptySet()))
        assertEquals(emptySet<DayOfWeek>(), AlarmEntity.maskToDays(0))
    }

    @Test
    fun mondayIsBitZero() {
        assertEquals(1, AlarmEntity.daysToMask(setOf(DayOfWeek.MONDAY)))
    }

    @Test
    fun sundayIsBitSix() {
        assertEquals(1 shl 6, AlarmEntity.daysToMask(setOf(DayOfWeek.SUNDAY)))
    }

    @Test
    fun allDaysIsFullMask() {
        val all = DayOfWeek.entries.toSet()
        assertEquals(0b1111111, AlarmEntity.daysToMask(all))
        assertEquals(all, AlarmEntity.maskToDays(0b1111111))
    }

    @Test
    fun roundTripsArbitrarySet() {
        val days = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY)
        val mask = AlarmEntity.daysToMask(days)
        assertEquals(days, AlarmEntity.maskToDays(mask))
    }
}
