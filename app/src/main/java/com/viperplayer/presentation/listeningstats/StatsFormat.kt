package com.viperplayer.presentation.listeningstats

/**
 * Small, pure display helpers for the listening-stats screens. Kept Android-free so they can be used
 * from previews and tests without a Context.
 */
object StatsFormat {

    /**
     * Formats a listening duration into a compact human string, e.g. "3d 4h", "2h 15m", "45m".
     * Returns "0m" for non-positive durations.
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

    /** Day-of-week short labels, Monday-first, matching the aggregator's 0-based indexing. */
    val dayLabels: List<String> = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    /** Full day-of-week names, Monday-first. */
    val dayNames: List<String> =
        listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    /** Formats an hour-of-day (0..23) as a 12-hour clock label, e.g. "9 AM", "11 PM", "12 AM". */
    fun hourLabel(hour: Int): String {
        val h = ((hour % 24) + 24) % 24
        val period = if (h < 12) "AM" else "PM"
        val display = when (h % 12) {
            0 -> 12
            else -> h % 12
        }
        return "$display $period"
    }
}
