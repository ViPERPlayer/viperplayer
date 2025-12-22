package com.viperplayer.domain.usecase.plugin

import com.viperplayer.domain.repository.PluginRepository
import timber.log.Timber
import javax.inject.Inject

/**
 * Use case for disabling a plugin.
 */
class DisablePluginUseCase @Inject constructor(
    private val repository: PluginRepository
) {
    companion object {
        private const val TAG = "DisablePluginUseCase"
    }
    
    suspend operator fun invoke(pluginId: String): Result<Unit> {
        Timber.d("Disabling plugin: $pluginId")
        val result = repository.disablePlugin(pluginId)
        result.onSuccess {
            Timber.d("Successfully disabled plugin: $pluginId")
        }.onFailure { e ->
            Timber.e(e, "Failed to disable plugin: $pluginId")
        }
        return result
    }
}

