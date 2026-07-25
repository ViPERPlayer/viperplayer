package com.viperplayer.presentation.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.viperplayer.MainActivity
import com.viperplayer.R
import com.viperplayer.di.WidgetEntryPoint
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.PlaybackContext
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Home-screen playlist / quick-launch widget: lists the user's playlists (Liked Songs first, then
 * local playlists) as tappable rows that start playback of that playlist. The list is populated by
 * [PlaylistWidgetService] (a collection widget); tapping a row broadcasts [ACTION_PLAY_PLAYLIST]
 * back here, which resolves the playlist's songs and plays them.
 *
 * A widget has no ViewModel; the data/playback logic lives here and in the service, reading the
 * app's singleton repositories via [WidgetEntryPoint].
 */
class PlaylistWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { id -> renderWidget(context, appWidgetManager, id) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_PLAY_PLAYLIST -> {
                val encoded = intent.getStringExtra(EXTRA_PLAYLIST_ID) ?: return
                val mediaId = MediaId.decode(encoded) ?: return
                playPlaylist(context.applicationContext, mediaId)
            }
            ACTION_REFRESH -> notifyDataChanged(context)
        }
    }

    /** Builds the list container [RemoteViews] and binds it to [PlaylistWidgetService]. */
    private fun renderWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_playlist)

        val serviceIntent = Intent(context, PlaylistWidgetService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            // A unique data URI per widget id so the framework treats each adapter as distinct.
            data = Uri.parse("viper-playlist-widget://$widgetId")
        }
        views.setRemoteAdapter(R.id.widget_playlist_list, serviceIntent)
        views.setEmptyView(R.id.widget_playlist_list, R.id.widget_playlist_empty)

        // Template pending intent: each row's fill-in intent supplies the encoded playlist id.
        val clickTemplate = Intent(context, PlaylistWidgetProvider::class.java)
            .setAction(ACTION_PLAY_PLAYLIST)
        val templatePending = PendingIntent.getBroadcast(
            context,
            0,
            clickTemplate,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        views.setPendingIntentTemplate(R.id.widget_playlist_list, templatePending)

        // Header taps open the app.
        views.setOnClickPendingIntent(R.id.widget_playlist_header, launchApp(context))

        manager.updateAppWidget(widgetId, views)
        manager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_playlist_list)
    }

    /** Tell every instance to reload its list (e.g. after playlists change). */
    private fun notifyDataChanged(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, PlaylistWidgetProvider::class.java))
        if (ids.isNotEmpty()) {
            manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_playlist_list)
        }
    }

    /** Resolve the playlist's songs and start playback, off the main thread. */
    private fun playPlaylist(context: Context, mediaId: MediaId) {
        val entryPoint = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
        val mediaLibraryRepository = entryPoint.mediaLibraryRepository()
        val playerRepository = entryPoint.playerRepository()
        scope.launch {
            try {
                val playlist = mediaLibraryRepository.getPlaylist(mediaId).first() ?: return@launch
                val songs = playlist.songs.orEmpty().filter { it.isPlayable }
                if (songs.isEmpty()) return@launch
                playerRepository.playAll(
                    songs = songs,
                    startIndex = 0,
                    context = PlaybackContext.Playlist(playlist.id, playlist.name)
                )
            } catch (e: Exception) {
                Timber.w(e, "Playlist widget failed to start playback for $mediaId")
            }
        }
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

    companion object {
        const val ACTION_PLAY_PLAYLIST = "com.viperplayer.widget.ACTION_PLAY_PLAYLIST"
        const val ACTION_REFRESH = "com.viperplayer.widget.ACTION_PLAYLIST_REFRESH"
        const val EXTRA_PLAYLIST_ID = "com.viperplayer.widget.EXTRA_PLAYLIST_ID"

        // A process-wide scope for the short playlist-resolve + play work triggered by a row tap.
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }
}
