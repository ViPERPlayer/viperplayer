package com.viperplayer.alarm.scheduling

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.viperplayer.R
import com.viperplayer.alarm.domain.Alarm
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts the "alarm is ringing" notification when an alarm fires, with a Dismiss action that stops
 * playback. High-importance, ongoing (until dismissed), tapping it opens the app.
 */
@Singleton
class AlarmNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val notificationManager = NotificationManagerCompat.from(context)

    fun notify(alarm: Alarm) {
        if (!notificationManager.areNotificationsEnabled()) return
        ensureChannel()

        val openIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val contentIntent = openIntent?.let {
            PendingIntent.getActivity(
                context,
                alarm.id.toInt(),
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        val dismissIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_DISMISS
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarm.id)
            data = Uri.parse("viperplayer://alarm-dismiss/${alarm.id}")
        }
        val dismissPending = PendingIntent.getBroadcast(
            context,
            DISMISS_REQUEST_OFFSET + alarm.id.toInt(),
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val title = alarm.label.ifBlank { context.getString(R.string.alarm_notification_title) }
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.alarm_notification_text))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(true)
            .addAction(
                0,
                context.getString(R.string.alarm_action_dismiss),
                dismissPending,
            )
        if (contentIntent != null) builder.setContentIntent(contentIntent)
        if (alarm.vibrate) builder.setDefaults(NotificationCompat.DEFAULT_VIBRATE)

        runCatching { notificationManager.notify(notificationId(alarm.id), builder.build()) }
            .onFailure { Timber.w(it, "Failed to post alarm notification") }
    }

    fun cancel(alarmId: Long) {
        notificationManager.cancel(notificationId(alarmId))
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.alarm_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.alarm_channel_desc)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun notificationId(alarmId: Long): Int = NOTIFICATION_ID_BASE + alarmId.toInt()

    private companion object {
        const val CHANNEL_ID = "alarms"
        const val NOTIFICATION_ID_BASE = 2_000_000
        const val DISMISS_REQUEST_OFFSET = 3_000_000
    }
}
