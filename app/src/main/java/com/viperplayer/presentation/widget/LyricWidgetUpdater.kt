package com.viperplayer.presentation.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.viperplayer.MainActivity
import com.viperplayer.R
import com.viperplayer.di.WidgetEntryPoint
import com.viperplayer.domain.model.Lyrics
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.repository.PlayerRepository
import com.viperplayer.domain.repository.PluginRepository
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Keeps the lyric widget in sync with playback. Observes the app's [PlayerRepository.currentSong],
 * resolves that song's lyrics via [PluginRepository] (reusing the exact data path the now-playing
 * screen uses), and — while a synced lyric is present — polls the playhead and re-renders the widget
 * with the current line ([Lyrics.currentLineIndex]). Falls back to the song title, then to a
 * "no lyrics" placeholder.
 *
 * A process-wide singleton so at most one observer runs. This is UI-adjacent glue for the widget,
 * not app business logic — it only reflects player + lyrics state onto RemoteViews.
 */
internal object LyricWidgetUpdater {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var appContext: Context? = null
    private var observeJob: Job? = null
    private var lineJob: Job? = null

    // The current song's resolved lyrics, cached so the line poller doesn't refetch on every tick.
    private var currentSong: Song? = null
    private var currentLyrics: Lyrics? = null
    private var lastRenderedText: String? = null

    /** Start (or keep) observing playback so the widget updates. Safe to call repeatedly. */
    @Synchronized
    fun ensureStarted(context: Context) {
        appContext = context.applicationContext
        if (observeJob?.isActive == true) return
        val ctx = context.applicationContext
        val entryPoint = EntryPointAccessors.fromApplication(ctx, WidgetEntryPoint::class.java)
        val playerRepository = entryPoint.playerRepository()
        val pluginRepository = entryPoint.pluginRepository()

        observeJob = scope.launch {
            playerRepository.currentSong.collectLatest { song ->
                onSongChanged(song, pluginRepository)
                startLinePolling(playerRepository)
            }
        }
    }

    /** Resolve lyrics for the newly-current [song] (or clear them when nothing is playing). */
    private suspend fun onSongChanged(song: Song?, pluginRepository: PluginRepository) {
        currentSong = song
        currentLyrics = null
        // Force a render for the new song even if its first text matches the previous song's (e.g.
        // two tracks whose placeholder/first line is identical) — the dedupe must not hide the change.
        lastRenderedText = null
        // Immediately show the title (or nothing-playing) while lyrics resolve.
        pushText(placeholderFor(song))
        if (song == null) return
        currentLyrics = try {
            pluginRepository.getLyrics(song).getOrNull()?.takeUnless { it.isEmpty }
        } catch (e: Exception) {
            Timber.w(e, "Lyric widget failed to resolve lyrics for '${song.title}'")
            null
        }
        // Re-render with the resolved lyrics (or the no-lyrics fallback).
        pushText(lineFor(song, currentLyrics, positionMs = 0L))
    }

    /** Poll the playhead and re-render when the active synced line changes. */
    private fun startLinePolling(playerRepository: PlayerRepository) {
        lineJob?.cancel()
        lineJob = scope.launch {
            while (isActive) {
                val song = currentSong
                val lyrics = currentLyrics
                if (song != null && lyrics != null && lyrics.synced) {
                    val position = try {
                        playerRepository.getCurrentPosition()
                    } catch (e: Exception) {
                        0L
                    }
                    pushText(lineFor(song, lyrics, position))
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    /**
     * The text to show for [song] at [positionMs]: the current synced line, else the song title
     * (before the first line / unsynced lyrics), else a "no lyrics" placeholder. Pure given inputs.
     */
    private fun lineFor(song: Song, lyrics: Lyrics?, positionMs: Long): String {
        val ctx = appContext ?: return song.title
        if (lyrics == null) return ctx.getString(R.string.widget_lyric_no_lyrics)
        if (lyrics.synced) {
            val line = lyrics.lines.getOrNull(lyrics.currentLineIndex(positionMs))?.text
            return if (!line.isNullOrBlank()) line else song.title
        }
        // Unsynced lyrics have no timeline to follow — show the title as a stable label.
        if (!lyrics.plainText.isNullOrBlank()) return song.title
        return ctx.getString(R.string.widget_lyric_no_lyrics)
    }

    private fun placeholderFor(song: Song?): String {
        val ctx = appContext ?: return ""
        return song?.title ?: ctx.getString(R.string.widget_nothing_playing)
    }

    /** Push [text] to every lyric-widget instance, skipping redundant re-renders. */
    private fun pushText(text: String) {
        if (text == lastRenderedText) return
        lastRenderedText = text
        val ctx = appContext ?: return
        val views = RemoteViews(ctx.packageName, R.layout.widget_lyric)
        views.setTextViewText(R.id.widget_lyric_text, text)
        views.setOnClickPendingIntent(R.id.widget_lyric_root, launchAppIntent(ctx))
        val manager = AppWidgetManager.getInstance(ctx)
        val ids = manager.getAppWidgetIds(ComponentName(ctx, LyricWidgetProvider::class.java))
        if (ids.isEmpty()) return
        manager.updateAppWidget(ids, views)
    }

    private fun launchAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private const val POLL_INTERVAL_MS = 400L
}
