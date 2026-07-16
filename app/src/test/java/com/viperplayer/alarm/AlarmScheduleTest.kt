package com.viperplayer.alarm

import com.viperplayer.alarm.domain.AlarmSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Unit tests for the pure [AlarmSchedule.nextTriggerTime] core — no Android, fully deterministic (the
 * clock is passed in). Covers one-shot vs repeating, wrap across the week, exactly-now, disabled, and
 * a DST-transition day.
 */
class AlarmScheduleTest {

    private val utc = ZoneOffset.UTC

    /** Epoch millis for a wall-clock time in [zone]. */
    private fun millis(
        year: Int, month: Int, day: Int, hour: Int, minute: Int,
        zone: ZoneId = utc,
    ): Long = LocalDateTime.of(year, month, day, hour, minute)
        .atZone(zone).toInstant().toEpochMilli()

    private fun triggerZoned(
        hour: Int, minute: Int, days: Set<DayOfWeek>, fromMillis: Long, zone: ZoneId = utc,
    ): LocalDateTime? =
        AlarmSchedule.nextTriggerTime(hour, minute, days, enabled = true, fromMillis, zone)
            ?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDateTime() }

    // --- one-shot ---

    @Test
    fun oneShot_laterToday_firesToday() {
        // 2024-01-01 is a Monday. Now 07:00, alarm at 08:00 -> today 08:00.
        val now = millis(2024, 1, 1, 7, 0)
        val trigger = triggerZoned(8, 0, emptySet(), now)
        assertEquals(LocalDateTime.of(2024, 1, 1, 8, 0), trigger)
    }

    @Test
    fun oneShot_alreadyPassed_firesTomorrow() {
        // Now 09:00, alarm at 08:00 -> tomorrow 08:00.
        val now = millis(2024, 1, 1, 9, 0)
        val trigger = triggerZoned(8, 0, emptySet(), now)
        assertEquals(LocalDateTime.of(2024, 1, 2, 8, 0), trigger)
    }

    // --- exactly now ---

    @Test
    fun exactlyNow_rollsToNextOccurrence_notImmediate() {
        // Alarm time == now: must NOT fire immediately; one-shot -> tomorrow.
        val now = millis(2024, 1, 1, 8, 0)
        val trigger = triggerZoned(8, 0, emptySet(), now)
        assertEquals(LocalDateTime.of(2024, 1, 2, 8, 0), trigger)
    }

    // --- repeating ---

    @Test
    fun repeating_picksNextEnabledWeekday() {
        // Monday 09:00 (alarm 08:00 already passed today). Repeats Mon+Wed+Fri -> next is Wed.
        val now = millis(2024, 1, 1, 9, 0) // Monday
        val days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        val trigger = triggerZoned(8, 0, days, now)
        assertEquals(LocalDateTime.of(2024, 1, 3, 8, 0), trigger) // Wednesday
        assertEquals(DayOfWeek.WEDNESDAY, trigger?.dayOfWeek)
    }

    @Test
    fun repeating_todayStillAhead_firesToday() {
        // Monday 07:00, alarm 08:00, repeats Monday -> today.
        val now = millis(2024, 1, 1, 7, 0) // Monday
        val trigger = triggerZoned(8, 0, setOf(DayOfWeek.MONDAY), now)
        assertEquals(LocalDateTime.of(2024, 1, 1, 8, 0), trigger)
    }

    @Test
    fun repeating_wrapsAcrossWeekBoundary() {
        // Saturday 09:00, alarm only repeats Monday -> wrap to next Monday.
        val now = millis(2024, 1, 6, 9, 0) // Saturday
        val trigger = triggerZoned(8, 0, setOf(DayOfWeek.MONDAY), now)
        assertEquals(LocalDateTime.of(2024, 1, 8, 8, 0), trigger) // next Monday
        assertEquals(DayOfWeek.MONDAY, trigger?.dayOfWeek)
    }

    @Test
    fun repeating_sameDayNextWeek_whenOnlyDayAndTimePassed() {
        // Monday 09:00, only repeats Monday, alarm 08:00 passed -> next Monday (7 days later).
        val now = millis(2024, 1, 1, 9, 0) // Monday
        val trigger = triggerZoned(8, 0, setOf(DayOfWeek.MONDAY), now)
        assertEquals(LocalDateTime.of(2024, 1, 8, 8, 0), trigger)
    }

    // --- disabled ---

    @Test
    fun disabled_returnsNull() {
        val now = millis(2024, 1, 1, 7, 0)
        val result = AlarmSchedule.nextTriggerTime(
            8, 0, setOf(DayOfWeek.MONDAY), enabled = false, now, utc,
        )
        assertNull(result)
    }

    // --- DST transition ---

    @Test
    fun dstSpringForward_producesValidFutureInstant() {
        // US spring-forward 2024: 2024-03-10 02:00 -> 03:00 in America/New_York. An alarm at 02:30
        // that day doesn't exist as wall-clock; java.time must still yield a valid future instant.
        val ny = ZoneId.of("America/New_York")
        val now = millis(2024, 3, 10, 1, 0, ny) // 01:00 local, before the gap
        val result = AlarmSchedule.nextTriggerTime(2, 30, emptySet(), enabled = true, now, ny)
        assertTrue("must produce a next instant", result != null)
        // The resulting instant is strictly after 'now' and doesn't crash.
        assertTrue(result!! > now)
    }

    @Test
    fun dstFallBack_producesValidFutureInstant() {
        // US fall-back 2024: 2024-11-03 clocks go 02:00 -> 01:00 (01:00-02:00 happens twice).
        val ny = ZoneId.of("America/New_York")
        val now = millis(2024, 11, 3, 0, 30, ny)
        val result = AlarmSchedule.nextTriggerTime(1, 30, emptySet(), enabled = true, now, ny)
        assertTrue(result != null && result > now)
    }
}
