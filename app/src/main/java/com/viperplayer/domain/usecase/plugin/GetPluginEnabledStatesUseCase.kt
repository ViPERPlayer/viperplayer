package com.viperplayer.domain.usecase.plugin

import com.viperplayer.domain.repository.PluginRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case for getting plugin enabled states.
 */
class GetPluginEnabledStatesUseCase @Inject constructor(
    private val repository: PluginRepository
) {
    operator fun invoke(): Flow<Map<String, Boolean>> {
        return repository.pluginDisabledStates
    }
}

