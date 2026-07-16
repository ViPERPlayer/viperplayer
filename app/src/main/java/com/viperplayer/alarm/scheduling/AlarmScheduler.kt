package com.viperplayer.alarm.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.viperplayer.alarm.domain.Alarm
import com.viperplayer.alarm.domain.AlarmSchedule
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps [AlarmManager] to arm the NEXT occurrence of an [Alarm] via a [PendingIntent] to
 * [AlarmReceiver]. Repeating alarms are re-armed by the receiver after each fire (and by
 * [BootReceiver] on boot); this class only ever schedules a single next instant, computed by the pure
 * [AlarmSchedule.nextTriggerTime].
 *
 * All logic lives here (a manager), never in the UI. On Android 12+ we honour the exact-alarm
 * permission: if it isn't granted we fall back to an inexact wake (see [canScheduleExact]).
 */
@Singleton
class AlarmScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Arm [alarm] for its next occurrence. No-op (and cancels any existing schedule) when the alarm is
     * disabled or has no valid next time. Returns the epoch-millis it was armed for, or null.
     */
    fun schedule(alarm: Alarm): Long? {
        if (!alarm.enabled) {
            cancel(alarm.id)
            return null
        }
        val triggerAt = AlarmSchedule.nextTriggerTime(
            hour = alarm.hour,
            minute = alarm.minute,
            daysOfWeek = alarm.daysOfWeek,
            enabled = alarm.enabled,
            fromEpochMillis = System.currentTimeMillis(),
            zoneId = ZoneId.systemDefault(),
        ) ?: run {
            cancel(alarm.id)
            return null
        }

        val operation = firePendingIntent(alarm.id)
        val showIntent = showPendingIntent(alarm.id)

        try {
            if (canScheduleExact()) {
                // setAlarmClock survives Doze and shows the system "next alarm" affordance.
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerAt, showIntent),
                    operation,
                )
                Timber.d("Armed exact alarm #${alarm.id} for $triggerAt")
            } else {
                // Exact-alarm permission not granted: best-effort inexact wake (may be delayed by Doze).
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
                Timber.w("Armed INEXACT alarm #${alarm.id} for $triggerAt (no exact-alarm permission)")
            }
        } catch (e: SecurityException) {
            // Race: permission revoked between the check and the call. Degrade gracefully.
            Timber.w(e, "Exact alarm denied; falling back to inexact for #${alarm.id}")
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
        }
        return triggerAt
    }

    /** Cancel any pending schedule for the alarm with [alarmId]. */
    fun cancel(alarmId: Long) {
        alarmManager.cancel(firePendingIntent(alarmId))
        Timber.d("Cancelled alarm #$alarmId")
    }

    /** (Re)arm every enabled alarm. Used after boot and after app upgrades. */
    fun rescheduleAll(alarms: List<Alarm>) {
        alarms.forEach { if (it.enabled) schedule(it) else cancel(it.id) }
    }

    /**
     * Whether we can schedule exact alarms. Below API 31 this is always allowed; on 31+ it depends on
     * the user granting SCHEDULE_EXACT_ALARM (or the app holding USE_EXACT_ALARM).
     */
    fun canScheduleExact(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

    private fun firePendingIntent(alarmId: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_FIRE
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
            // A distinct data URI so requestCode + filterEquals both stay unique per alarm.
            data = alarmUri(alarmId)
        }
        return PendingIntent.getBroadcast(
            context,
            alarmId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Tapping the system alarm affordance opens the app (MainActivity) rather than firing playback. */
    private fun showPendingIntent(alarmId: Long): PendingIntent {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent()
        return PendingIntent.getActivity(
            context,
            SHOW_REQUEST_OFFSET + alarmId.toInt(),
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun alarmUri(alarmId: Long): Uri =
        Uri.parse("viperplayer://alarm/$alarmId")

    private companion object {
        // Keeps "show" PendingIntent request codes from colliding with the "fire" ones.
        const val SHOW_REQUEST_OFFSET = 1_000_000
    }
}
