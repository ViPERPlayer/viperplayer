package com.viperplayer.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ViperDdcState(
    val enabled: Boolean = ViperDefaults.DDC_ENABLED,
    val selectedDdcFile: String? = null,
    val coeffs: Map<Int, List<Float>>? = null
)
