package com.viperplayer.presentation.viper.effect

import androidx.lifecycle.ViewModel
import com.viperplayer.domain.model.DynamicSystemDeviceType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class DynamicSystemUiState(
    val enabled: Boolean = false,
    val deviceType: DynamicSystemDeviceType = DynamicSystemDeviceType.EXTREME_HEADPHONE_V2,
    val dynamicBassStrength: Int = 0,
)

@HiltViewModel
class DynamicSystemViewModel @Inject constructor(
    // TODO: Inject repository
) : ViewModel() {
    private val _state = MutableStateFlow(DynamicSystemUiState())
    val state = _state.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        _state.update { it.copy(enabled = enabled) }
    }

    fun setDeviceType(deviceType: DynamicSystemDeviceType) {
        _state.update { it.copy(deviceType = deviceType) }
    }

    fun setDynamicBassStrength(dynamicBassStrength: Int) {
        _state.update { it.copy(dynamicBassStrength = dynamicBassStrength) }
    }

    fun resetDynamicBassStrength() {
        // TODO
    }
}
