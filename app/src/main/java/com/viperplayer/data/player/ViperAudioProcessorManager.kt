package com.viperplayer.data.player

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the ViPER audio processor instance.
 * This is a singleton that holds the processor instance so it can be shared
 * between the PlaybackService (for audio processing) and the repository (for control).
 * 
 * The manager controls the processor by calling methods on it.
 */
@OptIn(UnstableApi::class)
@Singleton
class ViperAudioProcessorManager @Inject constructor(
    /**
     * The ViPER audio processor instance.
     * Injected by Hilt as a singleton, created once and reused throughout the app lifecycle.
     */
    private val processor: ViperAudioProcessor
) {
    
    /**
     * Whether the processor should be active.
     * When false, the processor will pass through audio unchanged.
     */
    @Volatile
    var enabled: Boolean = false
        private set
    
    /**
     * Enable or disable ViPER processing.
     * This calls the processor to update its enabled state.
     */
    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        processor.setEnabled(enabled)
    }
}

