package com.viperplayer.presentation.viper.effect

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

private val outputGainValues = listOf(
    1,
    5,
    10,
    20,
    30,
    40,
    50,
    60,
    70,
    80,
    90,
    100,
    110,
    120,
    130,
    140,
    150,
    160,
    170,
    180,
    190,
    200,
)

private val thresholdLimitValues = listOf(
    30,
    50,
    70,
    80,
    90,
    100,
)

data class MasterLimiterUiState(
    val outputGain: Int = 0,
    val outputPan: Int = 0,
    val thresholdLimit: Int = 0,
)

@HiltViewModel
class MasterLimiterViewModel @Inject constructor(
    // TODO: Inject repository
) : ViewModel() {
    private val _state = MutableStateFlow(MasterLimiterUiState())
    val state = _state.asStateFlow()

    fun setOutputGain(outputGain: Int) {
        _state.update { it.copy(outputGain = outputGain) }
    }

    fun resetOutputGain() {
        // TODO
    }

    fun setOutputPan(outputPan: Int) {
        _state.update { it.copy(outputPan = outputPan) }
    }

    fun resetOutputPan() {
        // TODO
    }

    fun setThresholdLimit(thresholdLimit: Int) {
        _state.update { it.copy(thresholdLimit = thresholdLimit) }
    }

    fun resetThresholdLimit() {
        // TODO
    }
}
