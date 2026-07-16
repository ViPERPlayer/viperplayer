package com.viperplayer.alarm.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.viperplayer.alarm.domain.Alarm
import com.viperplayer.alarm.domain.AlarmContentSpec
import java.time.DayOfWeek

/**
 * Room row for an [Alarm]. Self-contained in the alarm feature's own database so the shared
 * [com.viperplayer.data.local.ViperPlayerDatabase] is never touched.
 *
 * [daysMask] is a 7-bit bitmask: bit (DayOfWeek.value - 1) set means that weekday is selected
 * (Monday == bit 0 .. Sunday == bit 6). [contentSpec] is the encoded [AlarmContentSpec].
 */
@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val hour: Int,
    val minute: Int,
    val daysMask: Int,
    val enabled: Boolean,
    val label: String,
    val contentSpec: String,
    val volume: Float,
    val fadeInDurationSec: Int,
    val vibrate: Boolean,
) {
    fun toDomain(): Alarm = Alarm(
        id = id,
        hour = hour,
        minute = minute,
        daysOfWeek = maskToDays(daysMask),
        enabled = enabled,
        label = label,
        content = AlarmContentSpec.decode(contentSpec),
        volume = volume,
        fadeInDurationSec = fadeInDurationSec,
        vibrate = vibrate,
    )

    companion object {
        fun fromDomain(alarm: Alarm): AlarmEntity = AlarmEntity(
            id = alarm.id,
            hour = alarm.hour,
            minute = alarm.minute,
            daysMask = daysToMask(alarm.daysOfWeek),
            enabled = alarm.enabled,
            label = alarm.label,
            contentSpec = alarm.content.encode(),
            volume = alarm.volume,
            fadeInDurationSec = alarm.fadeInDurationSec,
            vibrate = alarm.vibrate,
        )

        fun daysToMask(days: Set<DayOfWeek>): Int =
            days.fold(0) { acc, day -> acc or (1 shl (day.value - 1)) }

        fun maskToDays(mask: Int): Set<DayOfWeek> =
            DayOfWeek.entries.filter { (mask shr (it.value - 1)) and 1 == 1 }.toSet()
    }
}
