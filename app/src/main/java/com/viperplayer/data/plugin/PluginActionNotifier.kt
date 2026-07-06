package com.viperplayer.data.plugin

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.viperplayer.R
import com.viperplayer.plugin.model.RequiredAction
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Host-owned notification for plugin actions that interrupt playback (e.g. a stream can't resolve
 * until the user completes a verification). Tapping it opens the action's resolving activity, or
 * the app when the action has none. One notification per plugin, replaced on update.
 */
@Singleton
class PluginActionNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val notificationManager = NotificationManagerCompat.from(context)

    /** Post (or update) the action notification for [pluginId]. Silently no-ops if not permitted. */
    fun notify(pluginId: String, action: RequiredAction) {
        if (!notificationManager.areNotificationsEnabled()) return
        ensureChannel()

        val target = action.activity
        val contentIntent = if (target != null) {
            Intent().apply {
                component = ComponentName(pluginId, target)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            context.packageManager.getLaunchIntentForPackage(context.packageName)
        } ?: return

        val pendingIntent = PendingIntent.getActivity(
            context,
            pluginId.hashCode(),
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(action.title)
            .setContentText(action.description ?: context.getString(R.string.plugin_action_needed))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .build()

        runCatching { notificationManager.notify(pluginId.hashCode(), notification) }
            .onFailure { Timber.w(it, "Failed to post plugin action notification") }
    }

    /** Clear the action notification for [pluginId] (its actions were resolved). */
    fun cancel(pluginId: String) {
        notificationManager.cancel(pluginId.hashCode())
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.plugin_actions_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.plugin_actions_channel_desc)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "plugin_actions"
    }
}
