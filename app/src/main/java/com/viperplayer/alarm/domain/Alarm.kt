package com.viperplayer.alarm.domain

import java.time.DayOfWeek

/**
 * A user-configured alarm. Domain model (no Room/Android types) — the data layer maps it to/from
 * [com.viperplayer.alarm.data.AlarmEntity].
 *
 * @param id stable row id (0 == not yet persisted).
 * @param hour 0..23, [minute] 0..59 wall-clock trigger time.
 * @param daysOfWeek days this alarm repeats on; EMPTY means a one-shot alarm (fires once, then
 *   auto-disables). See [AlarmSchedule.nextTriggerTime].
 * @param enabled whether the alarm is armed.
 * @param label optional user label shown in the list + notification.
 * @param content what to play when it fires.
 * @param volume target playback volume 0..1 the alarm ramps toward.
 * @param fadeInDurationSec length of the volume fade-in (0 == start at full volume immediately).
 * @param vibrate whether the alarm notification vibrates.
 */
data class Alarm(
    val id: Long = 0L,
    val hour: Int,
    val minute: Int,
    val daysOfWeek: Set<DayOfWeek> = emptySet(),
    val enabled: Boolean = true,
    val label: String = "",
    val content: AlarmContentSpec = AlarmContentSpec.Default,
    val volume: Float = 1f,
    val fadeInDurationSec: Int = 30,
    val vibrate: Boolean = true,
) {
    /** True for a non-repeating alarm (fires once then disables itself). */
    val isOneShot: Boolean get() = daysOfWeek.isEmpty()

    val fadeInDurationMs: Long get() = fadeInDurationSec.coerceAtLeast(0) * 1000L
}
