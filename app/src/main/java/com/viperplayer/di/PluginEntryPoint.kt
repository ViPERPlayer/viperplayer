package com.viperplayer.di

import com.viperplayer.data.source.PluginDataSource
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * EntryPoint for accessing PluginDataSource from non-Hilt classes
 * (e.g., BroadcastReceivers).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PluginEntryPoint {
    fun pluginDataSource(): PluginDataSource
}

