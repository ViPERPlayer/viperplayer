package com.viperplayer.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ViperConvolverState(
    val enabled: Boolean = ViperDefaults.CONVOLVER_ENABLED,
    val impulseResponse: String? = ViperDefaults.CONVOLVER_IMPULSE_RESPONSE, // Path to IR file
    val crossChannel: Int = ViperDefaults.CONVOLVER_CROSS_CHANNEL, // 0-100
)
