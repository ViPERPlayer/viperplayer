package com.viperplayer.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents a ViPER preset containing effect configurations.
 * Each preset is linked to a device.
 */
@Serializable
data class ViperPreset(
    val id: Long = 0,
    val name: String,
    val effectsState: ViperEffectsState,
    val deviceId: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

