package com.viperplayer.alarm.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Pure, Android-free scheduling core for the alarm clock. Everything here is deterministic given its
 * inputs (a clock is passed in as `fromEpochMillis` + `zoneId`) so it can be exercised entirely from
 * JVM unit tests. No `AlarmManager`, no `Context`, no side effects.
 */
object AlarmSchedule {

    /**
     * The next instant (epoch millis) at which an alarm at [hour]:[minute] repeating on [daysOfWeek]
     * should fire, computed relative to [fromEpochMillis] in [zoneId]. Returns `null` when the alarm
     * is disabled ([enabled] == false) — a disabled alarm never fires.
     *
     * Semantics:
     *  - [daysOfWeek] empty  -> a ONE-SHOT alarm: fires at the next [hour]:[minute] that is strictly
     *    after "now" (today if the time is still ahead, otherwise tomorrow).
     *  - [daysOfWeek] set    -> a REPEATING alarm: fires at the next selected weekday whose
     *    [hour]:[minute] is strictly after "now". If the time today has already passed (or today is
     *    not a selected day) it advances day by day, wrapping across the week boundary, up to 7 days.
     *
     * "Strictly after" means an alarm whose time is exactly now rolls to the next occurrence rather
     * than firing immediately — this avoids double-firing when we reschedule right after a fire.
     *
     * Uses [java.time.ZonedDateTime] so DST gaps/overlaps resolve to a valid instant (java.time picks
     * the correct offset; a wall-clock time that doesn't exist is shifted forward by the gap).
     */
    fun nextTriggerTime(
        hour: Int,
        minute: Int,
        daysOfWeek: Set<DayOfWeek>,
        enabled: Boolean,
        fromEpochMillis: Long,
        zoneId: ZoneId,
    ): Long? {
        if (!enabled) return null
        require(hour in 0..23) { "hour must be in 0..23, was $hour" }
        require(minute in 0..59) { "minute must be in 0..59, was $minute" }

        val time = LocalTime.of(hour, minute)
        val now = Instant.ofEpochMilli(fromEpochMillis).atZone(zoneId)
        val today = now.toLocalDate()

        // Candidate instant for firing [time] on [date], resolved through the zone (DST-safe).
        fun instantOn(date: LocalDate): Instant = date.atTime(time).atZone(zoneId).toInstant()

        if (daysOfWeek.isEmpty()) {
            // One-shot: today if still ahead, otherwise tomorrow.
            val todayInstant = instantOn(today)
            val target = if (todayInstant.isAfter(now.toInstant())) today else today.plusDays(1)
            return instantOn(target).toEpochMilli()
        }

        // Repeating: scan up to 7 days forward for the next selected weekday whose time is ahead.
        for (offset in 0..7) {
            val date = today.plusDays(offset.toLong())
            if (date.dayOfWeek !in daysOfWeek) continue
            val candidate = instantOn(date)
            if (candidate.isAfter(now.toInstant())) {
                return candidate.toEpochMilli()
            }
        }
        // Unreachable for a non-empty day set (a matching day always exists within 7 days), but be
        // defensive rather than throw.
        return null
    }
}
