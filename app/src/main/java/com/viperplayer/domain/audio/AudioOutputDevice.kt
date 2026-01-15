package com.viperplayer.domain.audio

/**
 * Information about an audio output device.
 */
data class AudioOutputDevice(
    val id: String, // Unique identifier for the device
    val name: String, // Human-readable device name
)