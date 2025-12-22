package com.viperplayer.domain.usecase.plugin

import com.viperplayer.domain.repository.PluginRepository
import timber.log.Timber
import javax.inject.Inject

/**
 * Use case for enabling a plugin.
 */
class EnablePluginUseCase @Inject constructor(
    private val repository: PluginRepository
) {
    companion object {
        private const val TAG = "EnablePluginUseCase"
    }
    
    suspend operator fun invoke(pluginId: String): Result<Unit> {
        Timber.d("[$TAG] Enabling plugin: $pluginId")
        val result = repository.enablePlugin(pluginId)
        result.onSuccess {
            Timber.d("[$TAG] Successfully enabled plugin: $pluginId")
        }.onFailure { e ->
            Timber.e(e, "[$TAG] Failed to enable plugin: $pluginId")
        }
        return result
    }
}

