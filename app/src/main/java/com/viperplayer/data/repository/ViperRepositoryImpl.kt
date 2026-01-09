package com.viperplayer.data.repository

import com.viperplayer.data.player.ViperAudioProcessorManager
import com.viperplayer.domain.repository.ViperRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of ViperRepository that manages ViPER audio processing state.
 */
@Singleton
class ViperRepositoryImpl @Inject constructor(
    private val processorManager: ViperAudioProcessorManager
) : ViperRepository {
    
    private val _enabled = MutableStateFlow(processorManager.enabled)
    override val enabled: StateFlow<Boolean> = _enabled.asStateFlow()
    
    init {
        // Sync initial state
        _enabled.value = processorManager.enabled
    }
    
    override suspend fun setEnabled(enabled: Boolean) {
        processorManager.setEnabled(enabled)
        _enabled.update { enabled }
    }
}

