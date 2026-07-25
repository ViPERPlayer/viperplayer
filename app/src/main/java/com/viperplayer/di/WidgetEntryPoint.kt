package com.viperplayer.di

import com.viperplayer.domain.repository.MediaLibraryRepository
import com.viperplayer.domain.repository.PlayerRepository
import com.viperplayer.domain.repository.PluginRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * EntryPoint for the home-screen widgets, which are [android.appwidget.AppWidgetProvider] /
 * [android.widget.RemoteViewsService] components and therefore not Hilt-managed. Exposes the
 * app's singleton repositories so the playlist and lyric widgets can read the same data the app
 * uses, obtained via
 * [dagger.hilt.android.EntryPointAccessors.fromApplication].
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun mediaLibraryRepository(): MediaLibraryRepository
    fun playerRepository(): PlayerRepository
    fun pluginRepository(): PluginRepository
}
