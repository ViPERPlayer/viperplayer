package com.viperplayer.di

import com.viperplayer.data.preferences.PushSyncPreferences
import com.viperplayer.data.source.PluginDataSource
import com.viperplayer.data.sync.push.PluginWriteGateway
import com.viperplayer.data.sync.push.PushSyncGate
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Wires the two-way push sync drain to its collaborators via narrow interfaces (kept narrow so
 * [com.viperplayer.data.sync.push.PushSyncManager] is unit-testable without Android):
 *  - [PluginWriteGateway] is fulfilled by [PluginDataSource] (the account-library write ops).
 *  - [PushSyncGate] reads the per-plugin opt-in from [PushSyncPreferences].
 */
@Module
@InstallIn(SingletonComponent::class)
object PushSyncModule {

    @Provides
    @Singleton
    fun providePluginWriteGateway(dataSource: PluginDataSource): PluginWriteGateway = dataSource

    @Provides
    @Singleton
    fun providePushSyncGate(preferences: PushSyncPreferences): PushSyncGate =
        PushSyncGate { pluginId -> preferences.isEnabledSync(pluginId) }
}
