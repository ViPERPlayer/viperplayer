package com.viperplayer.presentation.viper

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.domain.model.DynamicSystemDeviceType
import com.viperplayer.domain.model.ViperEffectsState
import com.viperplayer.domain.model.ViperSteppedValues
import com.viperplayer.domain.repository.ViperRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI State for ViPER screen containing all effects and settings.
 */
data class ViperUiState(
    val effectsState: ViperEffectsState = ViperEffectsState.default(),
    val activeDeviceId: String? = null,
)

/**
 * ViewModel for ViPER screen.
 * This is the single ViewModel that manages all ViPER effects state.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class ViperViewModel @Inject constructor(
    private val viperRepository: ViperRepository
) : ViewModel() {
    val uiState: StateFlow<ViperUiState> = viperRepository.effectsState.map { effectsState ->
        ViperUiState(
            effectsState = effectsState,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ViperUiState()
    )

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(enabled = enabled) }
        }
    }

    // Master Limiter
    fun setMasterLimiterOutputGain(value: Int) {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(masterLimiter = it.masterLimiter.copy(outputGain = value)) }
        }
    }

    fun setMasterLimiterOutputPan(value: Float) {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(masterLimiter = it.masterLimiter.copy(outputPan = value)) }
        }
    }

    fun setMasterLimiterThresholdLimit(value: Int) {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(masterLimiter = it.masterLimiter.copy(thresholdLimit = value)) }
        }
    }

    fun resetMasterLimiterOutputGain() {
        viewModelScope.launch {
            viperRepository.updateEffectsState { 
                it.copy(masterLimiter = it.masterLimiter.copy(outputGain = ViperSteppedValues.DEFAULT_OUTPUT_GAIN_INDEX))
            }
        }
    }

    fun resetMasterLimiterOutputPan() {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(masterLimiter = it.masterLimiter.copy(outputPan = 0f)) }
        }
    }

    fun resetMasterLimiterThresholdLimit() {
        viewModelScope.launch {
            viperRepository.updateEffectsState { 
                it.copy(masterLimiter = it.masterLimiter.copy(thresholdLimit = ViperSteppedValues.DEFAULT_THRESHOLD_LIMIT_INDEX))
            }
        }
    }

    // Spectrum Extension
    fun setSpectrumExtensionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(spectrumExtension = it.spectrumExtension.copy(enabled = enabled)) }
        }
    }

    fun setSpectrumExtensionStrength(value: Int) {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(spectrumExtension = it.spectrumExtension.copy(strength = value)) }
        }
    }

    fun resetSpectrumExtensionStrength() {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(spectrumExtension = it.spectrumExtension.copy(strength = 10)) }
        }
    }

    // Field Surround
    fun setFieldSurroundEnabled(enabled: Boolean) {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(fieldSurround = it.fieldSurround.copy(enabled = enabled)) }
        }
    }

    fun setFieldSurroundStrength(value: Int) {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(fieldSurround = it.fieldSurround.copy(surroundStrength = value)) }
        }
    }

    fun setFieldSurroundMidImageStrength(value: Int) {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(fieldSurround = it.fieldSurround.copy(midImageStrength = value)) }
        }
    }

    fun resetFieldSurroundStrength() {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(fieldSurround = it.fieldSurround.copy(surroundStrength = 0)) }
        }
    }

    fun resetFieldSurroundMidImageStrength() {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(fieldSurround = it.fieldSurround.copy(midImageStrength = 5)) }
        }
    }

    // Differential Surround
    fun setDifferentialSurroundEnabled(enabled: Boolean) {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(differentialSurround = it.differentialSurround.copy(enabled = enabled)) }
        }
    }

    fun setDifferentialSurroundDelay(value: Int) {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(differentialSurround = it.differentialSurround.copy(delay = value)) }
        }
    }

    fun resetDifferentialSurroundDelay() {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(differentialSurround = it.differentialSurround.copy(delay = 5)) }
        }
    }

    // Dynamic System
    fun setDynamicSystemEnabled(enabled: Boolean) {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(dynamicSystem = it.dynamicSystem.copy(enabled = enabled)) }
        }
    }

    fun setDynamicSystemDeviceType(deviceType: DynamicSystemDeviceType) {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(dynamicSystem = it.dynamicSystem.copy(deviceType = deviceType)) }
        }
    }

    fun setDynamicSystemBassStrength(value: Int) {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(dynamicSystem = it.dynamicSystem.copy(dynamicBassStrength = value)) }
        }
    }

    fun resetDynamicSystemBassStrength() {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(dynamicSystem = it.dynamicSystem.copy(dynamicBassStrength = 0)) }
        }
    }

    // Tube Simulator
    fun setTubeSimulatorEnabled(enabled: Boolean) {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(tubeSimulator = it.tubeSimulator.copy(enabled = enabled)) }
        }
    }

    // ViPER Bass
    fun setViperBassEnabled(enabled: Boolean) {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(viperBass = it.viperBass.copy(enabled = enabled)) }
        }
    }

    fun setViperBassMode(mode: Int) {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(viperBass = it.viperBass.copy(mode = mode)) }
        }
    }

    fun setViperBassFrequency(value: Int) {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(viperBass = it.viperBass.copy(frequency = value)) }
        }
    }

    fun setViperBassGain(value: Int) {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(viperBass = it.viperBass.copy(gain = value)) }
        }
    }

    fun resetViperBassMode() {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(viperBass = it.viperBass.copy(mode = 0)) }
        }
    }

    fun resetViperBassFrequency() {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(viperBass = it.viperBass.copy(frequency = 70)) }
        }
    }

    fun resetViperBassGain() {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(viperBass = it.viperBass.copy(gain = 0)) }
        }
    }

    // ViPER Clarity
    fun setViperClarityEnabled(enabled: Boolean) {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(viperClarity = it.viperClarity.copy(enabled = enabled)) }
        }
    }

    fun setViperClarityMode(mode: Int) {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(viperClarity = it.viperClarity.copy(mode = mode)) }
        }
    }

    fun setViperClarityGain(value: Int) {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(viperClarity = it.viperClarity.copy(gain = value)) }
        }
    }

    fun resetViperClarityMode() {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(viperClarity = it.viperClarity.copy(mode = 0)) }
        }
    }

    fun resetViperClarityGain() {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(viperClarity = it.viperClarity.copy(gain = 1)) }
        }
    }

    // Auditory System Protection
    fun setAuditorySystemProtectionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(auditorySystemProtection = it.auditorySystemProtection.copy(enabled = enabled)) }
        }
    }

    fun setAuditorySystemProtectionLevel(value: Int) {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(auditorySystemProtection = it.auditorySystemProtection.copy(level = value)) }
        }
    }

    fun resetAuditorySystemProtectionLevel() {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(auditorySystemProtection = it.auditorySystemProtection.copy(level = 1)) }
        }
    }

    // Analog X
    fun setAnalogXEnabled(enabled: Boolean) {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(analogX = it.analogX.copy(enabled = enabled)) }
        }
    }

    fun setAnalogXLevel(value: Int) {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(analogX = it.analogX.copy(level = value)) }
        }
    }

    fun resetAnalogXLevel() {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(analogX = it.analogX.copy(level = 2)) }
        }
    }

    // Speaker Optimization
    fun setSpeakerOptimizationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(speakerOptimization = it.speakerOptimization.copy(enabled = enabled)) }
        }
    }
}

