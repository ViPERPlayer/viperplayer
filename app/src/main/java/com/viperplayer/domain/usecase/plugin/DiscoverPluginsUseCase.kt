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
    companion object {
        private const val TAG = "DiscoverPluginsUseCase"
    }
    
    suspend operator fun invoke() {
        Timber.d("[$TAG] Discovering plugins")
        try {
            repository.discoverPlugins()
            Timber.d("[$TAG] Plugin discovery completed")
        } catch (e: Exception) {
            Timber.e(e, "[$TAG] Error discovering plugins")
            throw e
        }
    }
}

