package com.viperplayer.presentation.listeningstats

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Small, pure display helpers for the listening-stats screens. Kept Android-free so they can be used
 * from previews and tests without a Context.
 */
object StatsFormat {

    /**
     * Formats a listening duration into a compact human string, e.g. "3d 4h", "2h 15m", "45m".
     * Returns "0m" for non-positive durations.
     *
     * The d/h/m unit abbreviations are intentionally untranslated (technical unit abbreviations,
     * consistent with dB/Hz/kHz elsewhere); this object stays Context-free and cannot use string
     * resources.
     */
    fun listeningTime(ms: Long): String {
        if (ms <= 0L) return "0m"
        var minutes = ms / 60_000L
        val days = minutes / (60L * 24)
        minutes %= (60L * 24)
        val hours = minutes / 60L
        minutes %= 60L
        val parts = buildList {
            if (days > 0L) add("${days}d")
            if (hours > 0L) add("${hours}h")
            if (minutes > 0L || isEmpty()) add("${minutes}m")
        }
        return parts.joinToString(" ")
    }

    /**
     * Day-of-week short labels, Monday-first, matching the aggregator's 0-based indexing.
     * Locale-aware and recomputed per access so a runtime locale change is reflected.
     */
    val dayLabels: List<String>
        get() = (1..7).map {
            DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, Locale.getDefault())
        }

    /**
     * Full day-of-week names, Monday-first.
     * Locale-aware and recomputed per access so a runtime locale change is reflected.
     */
    val dayNames: List<String>
        get() = (1..7).map {
            DayOfWeek.of(it).getDisplayName(TextStyle.FULL, Locale.getDefault())
        }

    /** Formats an hour-of-day (0..23) as a 12-hour clock label, e.g. "9 AM", "11 PM", "12 AM". */
    fun hourLabel(hour: Int): String {
        val h = ((hour % 24) + 24) % 24
        return LocalTime.of(h, 0)
            .format(DateTimeFormatter.ofPattern("h a", Locale.getDefault()))
    }
}
