package com.viperplayer.domain.repository

import kotlinx.coroutines.flow.StateFlow

/**
 * Repository for managing ViPER audio processing settings and state.
 */
interface ViperRepository {
    /**
     * Flow of whether ViPER processing is enabled.
     */
    val enabled: StateFlow<Boolean>
    
    /**
     * Enable or disable ViPER audio processing.
     */
    suspend fun setEnabled(enabled: Boolean)
}

