package com.viperplayer.presentation.ktx

import java.util.concurrent.TimeUnit

/**
 * Formats a millisecond duration as `m:ss` / `mm:ss`. Single canonical helper (previously duplicated
 * in PlayerScreen and ListItem with diverging edge-case handling).
 *
 * @param padMinutes pad the minutes field to two digits (`03:45` vs `3:45`)
 * @param placeholder returned for null / non-positive input; pass `null` to format those as `0:00`
 */
fun formatDuration(
    millis: Long?,
    padMinutes: Boolean = false,
    placeholder: String? = "--:--",
): String {
    if ((millis == null || millis <= 0) && placeholder != null) return placeholder
    val safe = (millis ?: 0L).coerceAtLeast(0)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(safe)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(safe) % 60
    return if (padMinutes) "%02d:%02d".format(minutes, seconds) else "%d:%02d".format(minutes, seconds)
}
