package com.viperplayer.alarm.ui

import android.content.Context
import com.viperplayer.R
import com.viperplayer.alarm.domain.Alarm
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Small, pure-ish formatting helpers for the alarm UI. Kept out of the composables so the screen only
 * renders. These take a [Context] only to resolve localized day strings.
 */
object AlarmFormatting {

    private val ORDERED_DAYS = listOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
        DayOfWeek.SATURDAY,
        DayOfWeek.SUNDAY,
    )

    /** The seven weekdays in display order (Mon..Sun). */
    fun orderedDays(): List<DayOfWeek> = ORDERED_DAYS

    private val WEEKDAYS = setOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
    )

    fun formatTime(alarm: Alarm): String =
        LocalTime.of(alarm.hour, alarm.minute)
            .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

    fun dayShortLabel(context: Context, day: DayOfWeek): String {
        val res = when (day) {
            DayOfWeek.MONDAY -> R.string.day_short_mon
            DayOfWeek.TUESDAY -> R.string.day_short_tue
            DayOfWeek.WEDNESDAY -> R.string.day_short_wed
            DayOfWeek.THURSDAY -> R.string.day_short_thu
            DayOfWeek.FRIDAY -> R.string.day_short_fri
            DayOfWeek.SATURDAY -> R.string.day_short_sat
            DayOfWeek.SUNDAY -> R.string.day_short_sun
        }
        return context.getString(res)
    }

    /** A human summary of an alarm's repeat schedule (e.g. "One-time", "Every day", "Mon, Wed"). */
    fun repeatSummary(context: Context, days: Set<DayOfWeek>): String = when {
        days.isEmpty() -> context.getString(R.string.alarm_no_repeat)
        days.size == 7 -> context.getString(R.string.alarm_repeat_daily)
        days == WEEKDAYS -> context.getString(R.string.alarm_repeat_weekdays)
        else -> ORDERED_DAYS.filter { it in days }
            .joinToString(", ") { dayShortLabel(context, it) }
    }
}
