package com.viperplayer.domain.usecase.plugin

import com.viperplayer.domain.repository.PluginRepository
import javax.inject.Inject

/**
 * Use case for discovering installed plugins.
 */
class DiscoverPluginsUseCase @Inject constructor(
    private val repository: PluginRepository
) {
    operator suspend fun invoke() {
        repository.discoverPlugins()
    }
}

