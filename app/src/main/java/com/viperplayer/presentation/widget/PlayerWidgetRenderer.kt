package com.viperplayer.presentation.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.viperplayer.MainActivity
import com.viperplayer.R
import timber.log.Timber

/**
 * Builds the [RemoteViews] for the player widget from the current playback state and pushes it to
 * every widget instance. Kept separate from the provider/updater so both the periodic [onUpdate]
 * path and the long-lived [PlayerWidgetUpdater] can share the exact same rendering logic.
 *
 * A widget has no ViewModel; the rendering/command logic lives here and in the provider/updater by
 * design.
 */
internal object PlayerWidgetRenderer {

    /**
     * Renders the widget from a connected [controller]. When [controller] is null (no session /
     * nothing playing) an idle placeholder layout is shown. Artwork is loaded via the app's Coil
     * image loader on the caller's thread — callers pass a bitmap they already loaded, or null.
     */
    fun render(context: Context, controller: MediaController?, artwork: Bitmap?) {
        val appContext = context.applicationContext
        val views = RemoteViews(appContext.packageName, R.layout.widget_player)

        val metadata: MediaMetadata? = controller?.mediaMetadata
        val hasItem = controller?.currentMediaItem != null

        if (hasItem && metadata != null) {
            views.setTextViewText(
                R.id.widget_title,
                metadata.title?.toString()
                    ?: appContext.getString(R.string.widget_nothing_playing)
            )
            views.setTextViewText(
                R.id.widget_artist,
                metadata.artist?.toString()
                    ?: metadata.albumArtist?.toString()
                    ?: ""
            )
        } else {
            views.setTextViewText(
                R.id.widget_title,
                appContext.getString(R.string.widget_nothing_playing)
            )
            views.setTextViewText(
                R.id.widget_artist,
                appContext.getString(R.string.widget_tap_to_open)
            )
        }

        if (artwork != null) {
            views.setImageViewBitmap(R.id.widget_artwork, artwork)
        } else {
            views.setImageViewResource(R.id.widget_artwork, R.drawable.ic_widget_album_placeholder)
        }

        val isPlaying = controller?.isPlaying == true
        views.setImageViewResource(
            R.id.widget_play_pause,
            if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
        )

        // Transport controls -> broadcasts back to the provider.
        views.setOnClickPendingIntent(
            R.id.widget_prev,
            broadcast(appContext, PlayerWidgetProvider.ACTION_PREV, 1)
        )
        views.setOnClickPendingIntent(
            R.id.widget_play_pause,
            broadcast(appContext, PlayerWidgetProvider.ACTION_PLAY_PAUSE, 2)
        )
        views.setOnClickPendingIntent(
            R.id.widget_next,
            broadcast(appContext, PlayerWidgetProvider.ACTION_NEXT, 3)
        )

        // Tapping the body opens the app.
        views.setOnClickPendingIntent(R.id.widget_root, launchApp(appContext))

        pushToAllWidgets(appContext, views)
    }

    /** Loads a bitmap for the current artwork URI, or null. Must be called off the main thread. */
    suspend fun loadArtwork(context: Context, controller: MediaController?): Bitmap? {
        val uri = controller?.mediaMetadata?.artworkUri ?: return null
        return try {
            val request = ImageRequest.Builder(context.applicationContext)
                .data(uri)
                .allowHardware(false)
                .build()
            context.applicationContext.imageLoader.execute(request).image?.toBitmap()
        } catch (e: Exception) {
            Timber.w(e, "Widget artwork load failed for $uri")
            null
        }
    }

    private fun pushToAllWidgets(context: Context, views: RemoteViews) {
        val manager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, PlayerWidgetProvider::class.java)
        val ids = manager.getAppWidgetIds(component)
        if (ids.isEmpty()) return
        manager.updateAppWidget(ids, views)
    }

    private fun broadcast(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, PlayerWidgetProvider::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun launchApp(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
