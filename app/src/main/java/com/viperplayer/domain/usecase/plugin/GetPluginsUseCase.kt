package com.viperplayer.domain.usecase.plugin

import com.viperplayer.domain.model.Plugin
import com.viperplayer.domain.model.PluginInfo
import com.viperplayer.domain.repository.PluginRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case for observing discovered plugins.
 */
class GetDiscoveredPluginsUseCase @Inject constructor(
    private val repository: PluginRepository
) {
    operator fun invoke(): Flow<List<PluginInfo>> = repository.discoveredPlugins
}

/**
 * Use case for observing connected plugins.
 */
class GetConnectedPluginsUseCase @Inject constructor(
    private val repository: PluginRepository
) {
    operator fun invoke(): Flow<List<Plugin>> = repository.connectedPlugins
}

