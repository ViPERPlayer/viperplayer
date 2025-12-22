package com.viperplayer.domain.usecase.plugin

import com.viperplayer.domain.model.Plugin
import com.viperplayer.domain.repository.PluginRepository
import javax.inject.Inject

/**
 * Use case for connecting to a plugin.
 */
class ConnectPluginUseCase @Inject constructor(
    private val repository: PluginRepository
) {
    suspend operator fun invoke(pluginId: String): Result<Plugin> {
        return repository.connectPlugin(pluginId)
    }
}

