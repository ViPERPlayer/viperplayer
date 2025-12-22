package com.viperplayer.domain.usecase.plugin

import com.viperplayer.domain.repository.PluginRepository
import timber.log.Timber
import javax.inject.Inject

/**
 * Use case for discovering installed plugins.
 */
class DiscoverPluginsUseCase @Inject constructor(
    private val repository: PluginRepository
) {
    operator fun invoke() {
        repository.discoverPlugins()
    }
}

