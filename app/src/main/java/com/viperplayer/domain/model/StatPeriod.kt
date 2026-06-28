package com.viperplayer.domain.model

/**
 * Time window for listening statistics. Each period maps to a lower-bound
 * timestamp computed as `now - period`; [ALL_TIME] uses 0 so it includes everything.
 */
enum class StatPeriod(val days: Long) {
    PAST_WEEK(7),
    PAST_MONTH(30),
    PAST_3_MONTHS(90),
    PAST_6_MONTHS(180),
    PAST_YEAR(365),
    ALL_TIME(-1);

    /** Inclusive lower bound (epoch millis) for events that fall inside this period. */
    fun fromTimestamp(now: Long = System.currentTimeMillis()): Long =
        if (this == ALL_TIME) 0L else now - days * DAY_MS

    companion object {
        private const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
