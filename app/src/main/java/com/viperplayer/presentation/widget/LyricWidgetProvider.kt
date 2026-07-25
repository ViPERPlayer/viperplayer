package com.viperplayer.presentation.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

/**
 * Home-screen lyric widget: shows the current line of synced lyrics for the now-playing track,
 * updating as playback progresses and falling back to the song title / a "no lyrics" placeholder.
 *
 * State is kept live by [LyricWidgetUpdater] (a long-lived observer of the app's current song +
 * playhead, mirroring the now-playing widget's updater). A widget has no ViewModel; the lyrics
 * resolution and current-line logic live in the updater by design.
 */
class LyricWidgetProvider : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        LyricWidgetUpdater.ensureStarted(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        LyricWidgetUpdater.ensureStarted(context)
    }
}
