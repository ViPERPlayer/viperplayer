package com.viperplayer.domain.repository

import com.viperplayer.domain.audio.AudioOutputDevice
import com.viperplayer.domain.model.ViperEffectsState
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository for managing ViPER audio processing settings and state.
 */
interface ViperRepository {
    /**
     * Flow of the current effects state.
     * This reflects the active preset or default state.
     */
    val effectsState: StateFlow<ViperEffectsState>
    
    /**
     * Update a specific effect state. This will automatically persist the change.
     */
    suspend fun updateEffectsState(update: (ViperEffectsState) -> ViperEffectsState)
    
    /**
     * Get the current active device ID, or null if using default.
     */
    val activeDevice: StateFlow<AudioOutputDevice?>

}
