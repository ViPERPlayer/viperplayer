package com.viperplayer.presentation.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.viperplayer.data.player.PlaybackService
import timber.log.Timber

/**
 * Home-screen widget for the media player: shows the current track (artwork, title, artist) and
 * offers previous / play-pause / next controls, staying in sync with playback.
 *
 * State is rendered by [PlayerWidgetRenderer] and kept live by [PlayerWidgetUpdater] (a long-lived
 * MediaController + Player.Listener). Transport buttons broadcast back here as custom actions; each
 * connects a short-lived MediaController to the session, issues the command, and releases it.
 */
class PlayerWidgetProvider : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        // First widget added — start observing playback so the widget updates while music plays.
        PlayerWidgetUpdater.ensureStarted(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Always-safe fallback: (re)start the updater, which also pushes a fresh render.
        PlayerWidgetUpdater.ensureStarted(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_PREV -> withController(context) { it.seekToPrevious() }
            ACTION_NEXT -> withController(context) { it.seekToNext() }
            ACTION_PLAY_PAUSE -> withController(context) {
                if (it.isPlaying) it.pause() else it.play()
            }
            ACTION_REFRESH -> PlayerWidgetUpdater.ensureStarted(context)
        }
    }

    /**
     * Connects a short-lived [MediaController] to the session, runs [command] once connected, then
     * releases the controller. Also nudges the updater so the widget reflects the change even if the
     * long-lived listener isn't connected yet.
     */
    private fun withController(context: Context, command: (MediaController) -> Unit) {
        val appContext = context.applicationContext
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val future: ListenableFuture<MediaController> =
            MediaController.Builder(appContext, token).buildAsync()
        future.addListener({
            val controller = try {
                future.get()
            } catch (e: Exception) {
                Timber.w(e, "Widget failed to connect MediaController for transport command")
                return@addListener
            }
            try {
                command(controller)
            } catch (e: Exception) {
                Timber.w(e, "Widget transport command failed")
            } finally {
                controller.release()
            }
            // Keep the persistent updater alive / refresh the widget.
            PlayerWidgetUpdater.ensureStarted(appContext)
        }, MoreExecutors.directExecutor())
    }

    companion object {
        const val ACTION_PREV = "com.viperplayer.widget.ACTION_PREV"
        const val ACTION_PLAY_PAUSE = "com.viperplayer.widget.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.viperplayer.widget.ACTION_NEXT"
        const val ACTION_REFRESH = "com.viperplayer.widget.ACTION_REFRESH"
    }
}
