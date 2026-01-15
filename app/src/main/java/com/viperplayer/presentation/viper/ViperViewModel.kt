package com.viperplayer.presentation.viper

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.domain.model.DynamicSystemDeviceType
import com.viperplayer.domain.model.ViperDefaults
import com.viperplayer.domain.model.ViperEffectsState
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
                it.copy(masterLimiter = it.masterLimiter.copy(outputGain = ViperDefaults.OUTPUT_GAIN_INDEX))
            }
        }
    }

    fun resetMasterLimiterOutputPan() {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(masterLimiter = it.masterLimiter.copy(outputPan = ViperDefaults.OUTPUT_PAN)) }
        }
    }

    fun resetMasterLimiterThresholdLimit() {
        viewModelScope.launch {
            viperRepository.updateEffectsState { 
                it.copy(masterLimiter = it.masterLimiter.copy(thresholdLimit = ViperDefaults.THRESHOLD_LIMIT_INDEX))
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
            viperRepository.updateEffectsState { it.copy(spectrumExtension = it.spectrumExtension.copy(strength = ViperDefaults.SPECTRUM_EXTENSION_STRENGTH)) }
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
            viperRepository.updateEffectsState { it.copy(fieldSurround = it.fieldSurround.copy(surroundStrength = ViperDefaults.FIELD_SURROUND_STRENGTH)) }
        }
    }

    fun resetFieldSurroundMidImageStrength() {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(fieldSurround = it.fieldSurround.copy(midImageStrength = ViperDefaults.FIELD_SURROUND_MID_IMAGE_STRENGTH)) }
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
            viperRepository.updateEffectsState { it.copy(differentialSurround = it.differentialSurround.copy(delay = ViperDefaults.DIFFERENTIAL_SURROUND_DELAY)) }
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
            viperRepository.updateEffectsState { it.copy(dynamicSystem = it.dynamicSystem.copy(dynamicBassStrength = ViperDefaults.DYNAMIC_SYSTEM_BASS_STRENGTH)) }
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
            viperRepository.updateEffectsState { it.copy(viperBass = it.viperBass.copy(mode = ViperDefaults.VIPER_BASS_MODE)) }
        }
    }

    fun resetViperBassFrequency() {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(viperBass = it.viperBass.copy(frequency = ViperDefaults.VIPER_BASS_FREQUENCY)) }
        }
    }

    fun resetViperBassGain() {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(viperBass = it.viperBass.copy(gain = ViperDefaults.VIPER_BASS_GAIN)) }
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
            viperRepository.updateEffectsState { it.copy(viperClarity = it.viperClarity.copy(mode = ViperDefaults.VIPER_CLARITY_MODE)) }
        }
    }

    fun resetViperClarityGain() {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(viperClarity = it.viperClarity.copy(gain = ViperDefaults.VIPER_CLARITY_GAIN)) }
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
            viperRepository.updateEffectsState { it.copy(auditorySystemProtection = it.auditorySystemProtection.copy(level = ViperDefaults.AUDITORY_SYSTEM_PROTECTION_LEVEL)) }
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
            viperRepository.updateEffectsState { it.copy(analogX = it.analogX.copy(level = ViperDefaults.ANALOG_X_LEVEL)) }
        }
    }

    // Speaker Optimization
    fun setSpeakerOptimizationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(speakerOptimization = it.speakerOptimization.copy(enabled = enabled)) }
        }
    }
}

