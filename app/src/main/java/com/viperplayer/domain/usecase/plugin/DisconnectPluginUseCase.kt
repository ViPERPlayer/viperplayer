package com.viperplayer.domain.usecase.plugin

import com.viperplayer.domain.repository.PluginRepository
import javax.inject.Inject

/**
 * Use case for disconnecting from a plugin.
 */
class DisconnectPluginUseCase @Inject constructor(
    private val repository: PluginRepository
) {
    suspend operator fun invoke(pluginId: String) {
        repository.disconnectPlugin(pluginId)
    }
}

