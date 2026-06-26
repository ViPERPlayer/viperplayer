package com.viperplayer.presentation.viper

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.domain.model.DynamicSystemDeviceType
import com.viperplayer.domain.model.IirEqualizerPresets
import com.viperplayer.domain.model.IirEqualizerState
import com.viperplayer.domain.model.ViperDefaults
import com.viperplayer.domain.model.ViperEffectsState


import com.viperplayer.domain.repository.ViperAssetRepository
import com.viperplayer.domain.repository.ViperRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * UI State for ViPER screen containing all effects and settings.
 */
data class ViperUiState(
    val effectsState: ViperEffectsState = ViperEffectsState.default(),
    val activeDeviceId: String? = null,
    val ddcFiles: List<File> = emptyList(),
    val kernelFiles: List<File> = emptyList()
)

/**
 * ViewModel for ViPER screen.
 * This is the single ViewModel that manages all ViPER effects state.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class ViperViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val viperRepository: ViperRepository,
    private val viperAssetRepository: ViperAssetRepository
) : ViewModel() {

    val uiState: StateFlow<ViperUiState> = combine(
        viperRepository.effectsState,
        viperAssetRepository.ddcFiles,
        viperAssetRepository.kernelFiles
    ) { effectsState, ddcFiles, kernelFiles ->
        ViperUiState(
            effectsState = effectsState,
            ddcFiles = ddcFiles,
            kernelFiles = kernelFiles
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
            viperRepository.updateEffectsState {
                it.copy(
                    masterLimiter = it.masterLimiter.copy(
                        outputGain = value
                    )
                )
            }
        }
    }

    fun setMasterLimiterOutputPan(value: Float) {
        viewModelScope.launch {
            viperRepository.updateEffectsState {
                it.copy(
                    masterLimiter = it.masterLimiter.copy(
                        outputPan = value
                    )
                )
            }
        }
    }

    fun setMasterLimiterThresholdLimit(value: Int) {
        viewModelScope.launch {
            viperRepository.updateEffectsState {
                it.copy(
                    masterLimiter = it.masterLimiter.copy(
                        thresholdLimit = value
                    )
                )
            }
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
            viperRepository.updateEffectsState {
                it.copy(
                    masterLimiter = it.masterLimiter.copy(
                        outputPan = ViperDefaults.OUTPUT_PAN
                    )
                )
            }
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
            viperRepository.updateEffectsState {
                it.copy(
                    spectrumExtension = it.spectrumExtension.copy(
                        enabled = enabled
                    )
                )
            }
        }
    }

    fun setSpectrumExtensionStrength(value: Int) {
        viewModelScope.launch {
            viperRepository.updateEffectsState {
                it.copy(
                    spectrumExtension = it.spectrumExtension.copy(
                        strength = value
                    )
                )
            }
        }
    }

    fun resetSpectrumExtensionStrength() {
        viewModelScope.launch {
            viperRepository.updateEffectsState {
                it.copy(
                    spectrumExtension = it.spectrumExtension.copy(
                        strength = ViperDefaults.SPECTRUM_EXTENSION_STRENGTH
                    )
                )
            }
        }
    }

    // Field Surround
    fun setFieldSurroundEnabled(enabled: Boolean) {
        viewModelScope.launch {
            viperRepository.updateEffectsState {
                it.copy(
                    fieldSurround = it.fieldSurround.copy(
                        enabled = enabled
                    )
                )
            }
        }
    }

    fun setFieldSurroundStrength(value: Int) {
        viewModelScope.launch {
            viperRepository.updateEffectsState {
                it.copy(
                    fieldSurround = it.fieldSurround.copy(
                        surroundStrength = value
                    )
                )
            }
        }
    }

    fun setFieldSurroundMidImageStrength(value: Int) {
        viewModelScope.launch {
            viperRepository.updateEffectsState {
                it.copy(
                    fieldSurround = it.fieldSurround.copy(
                        midImageStrength = value
                    )
                )
            }
        }
    }

    fun resetFieldSurroundStrength() {
        viewModelScope.launch {
            viperRepository.updateEffectsState {
                it.copy(
                    fieldSurround = it.fieldSurround.copy(
                        surroundStrength = ViperDefaults.FIELD_SURROUND_STRENGTH
                    )
                )
            }
        }
    }

    fun resetFieldSurroundMidImageStrength() {
        viewModelScope.launch {
            viperRepository.updateEffectsState {
                it.copy(
                    fieldSurround = it.fieldSurround.copy(
                        midImageStrength = ViperDefaults.FIELD_SURROUND_MID_IMAGE_STRENGTH
                    )
                )
            }
        }
    }

    // Differential Surround
    fun setDifferentialSurroundEnabled(enabled: Boolean) {
        viewModelScope.launch {
            viperRepository.updateEffectsState {
                it.copy(
                    differentialSurround = it.differentialSurround.copy(
                        enabled = enabled
                    )
                )
            }
        }
    }

    fun setDifferentialSurroundDelay(value: Int) {
        viewModelScope.launch {
            viperRepository.updateEffectsState {
                it.copy(
                    differentialSurround = it.differentialSurround.copy(
                        delay = value
                    )
                )
            }
        }
    }

    fun resetDifferentialSurroundDelay() {
        viewModelScope.launch {
            viperRepository.updateEffectsState {
                it.copy(
                    differentialSurround = it.differentialSurround.copy(
                        delay = ViperDefaults.DIFFERENTIAL_SURROUND_DELAY
                    )
                )
            }
        }
    }

    // Playback Gain Control
    fun setPlaybackGainEnabled(enabled: Boolean) {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(playbackGain = it.playbackGain.copy(enabled = enabled)) }
        }
    }

    fun setPlaybackGainStrength(strength: Int) {
        viewModelScope.launch {
            viperRepository.updateEffectsState {
                it.copy(
                    playbackGain = it.playbackGain.copy(
                        strength = strength
                    )
                )
            }
        }
    }

    fun resetPlaybackGainStrength() {
        viewModelScope.launch {
            viperRepository.updateEffectsState {
                it.copy(
                    playbackGain = it.playbackGain.copy(
                        strength = ViperDefaults.PLAYBACK_GAIN_STRENGTH
                    )
                )
            }
        }
    }

    fun setPlaybackGainMaxGain(maxGain: Int) {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(playbackGain = it.playbackGain.copy(maxGain = maxGain)) }
        }
    }

    fun resetPlaybackGainMaxGain() {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(playbackGain = it.playbackGain.copy(maxGain = ViperDefaults.PLAYBACK_GAIN_MAX_GAIN)) }
        }
    }

    fun setPlaybackGainOutputThreshold(threshold: Float) {
        viewModelScope.launch {
            viperRepository.updateEffectsState {
                it.copy(
                    playbackGain = it.playbackGain.copy(
                        outputThreshold = threshold
                    )
                )
            }
        }
    }

    fun resetPlaybackGainOutputThreshold() {
        viewModelScope.launch {
            viperRepository.updateEffectsState {
                it.copy(
                    playbackGain = it.playbackGain.copy(
                        outputThreshold = ViperDefaults.PLAYBACK_GAIN_OUTPUT_THRESHOLD
                    )
                )
            }
        }
    }

    // Dynamic System
    fun setDynamicSystemEnabled(enabled: Boolean) {
        viewModelScope.launch {
            viperRepository.updateEffectsState {
                it.copy(
                    dynamicSystem = it.dynamicSystem.copy(
                        enabled = enabled
                    )
                )
            }
        }
    }

    fun setDynamicSystemDeviceType(deviceType: DynamicSystemDeviceType) {
        viewModelScope.launch {
            viperRepository.updateEffectsState {
                it.copy(
                    dynamicSystem = it.dynamicSystem.copy(
                        deviceType = deviceType
                    )
                )
            }
        }
    }

    fun setDynamicSystemBassStrength(value: Int) {
        viewModelScope.launch {
            viperRepository.updateEffectsState {
                it.copy(
                    dynamicSystem = it.dynamicSystem.copy(
                        dynamicBassStrength = value
                    )
                )
            }
        }
    }

    fun resetDynamicSystemBassStrength() {
        viewModelScope.launch {
            viperRepository.updateEffectsState {
                it.copy(
                    dynamicSystem = it.dynamicSystem.copy(
                        dynamicBassStrength = ViperDefaults.DYNAMIC_SYSTEM_BASS_STRENGTH
                    )
                )
            }
        }
    }

    // Tube Simulator
    fun setTubeSimulatorEnabled(enabled: Boolean) {
        viewModelScope.launch {
            viperRepository.updateEffectsState {
                it.copy(
                    tubeSimulator = it.tubeSimulator.copy(
                        enabled = enabled
                    )
                )
            }
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
            viperRepository.updateEffectsState {
                it.copy(
                    auditorySystemProtection = it.auditorySystemProtection.copy(
                        enabled = enabled
                    )
                )
            }
        }
    }

    fun setAuditorySystemProtectionLevel(value: Int) {
        viewModelScope.launch {
            viperRepository.updateEffectsState {
                it.copy(
                    auditorySystemProtection = it.auditorySystemProtection.copy(
                        level = value
                    )
                )
            }
        }
    }

    fun resetAuditorySystemProtectionLevel() {
        viewModelScope.launch {
            viperRepository.updateEffectsState {
                it.copy(
                    auditorySystemProtection = it.auditorySystemProtection.copy(
                        level = ViperDefaults.AUDITORY_SYSTEM_PROTECTION_LEVEL
                    )
                )
            }
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

    // DDC
    fun setViperDdcEnabled(enabled: Boolean) {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(viperDdc = it.viperDdc.copy(enabled = enabled)) }
        }
    }

    fun setViperDdcFile(fileName: String) {
        viewModelScope.launch {
            val coeffs = viperAssetRepository.parseDdcCoeffs(fileName)
            viperRepository.updateEffectsState {
                it.copy(
                    viperDdc = it.viperDdc.copy(
                        selectedDdcFile = fileName,
                        coeffs = coeffs
                    )
                )
            }
        }
    }

    fun importDdcFile(uri: String) {
        viewModelScope.launch {
            when (val result = viperAssetRepository.importDdc(uri)) {
                is ViperAssetRepository.DdcImportResult.Success -> {
                    Toast.makeText(
                        context,
                        "DDC file imported successfully",
                        Toast.LENGTH_SHORT
                    ).show()
                    setViperDdcFile(result.fileName)
                }

                ViperAssetRepository.DdcImportResult.InvalidExtension -> Toast.makeText(
                    context,
                    "Error: Invalid file extension (must be .vdc)",
                    Toast.LENGTH_SHORT
                ).show()

                ViperAssetRepository.DdcImportResult.InvalidContent -> Toast.makeText(
                    context,
                    "Error: Invalid file content (parse failed)",
                    Toast.LENGTH_SHORT
                ).show()

                ViperAssetRepository.DdcImportResult.IOError -> Toast.makeText(
                    context,
                    "Error: import failed (IO error)",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun deleteDdcFile(fileName: String) {
        viewModelScope.launch {
            viperAssetRepository.deleteDdc(fileName)
            // If the deleted file was used, keep the logic (though now we store coeffs, so we could theoretically keep them)
            // But usually user expects deletion to reset if selection is gone.
            // Let's reset for consistency, or we could keep it as "Custom" if we wanted.
            // For now, reset selection.
            viperRepository.updateEffectsState { state ->
                if (state.viperDdc.selectedDdcFile == fileName) {
                    state.copy(
                        viperDdc = state.viperDdc.copy(
                            selectedDdcFile = null,
                            coeffs = null
                        )
                    )
                } else {
                    state
                }
            }
        }
    }

    // Convolver
    fun setConvolverEnabled(enabled: Boolean) {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(convolver = it.convolver.copy(enabled = enabled)) }
        }
    }

    fun selectConvolverImpulseResponse(fileName: String) {
        viewModelScope.launch {
            viperRepository.updateEffectsState {
                it.copy(
                    convolver = it.convolver.copy(
                        impulseResponse = fileName
                    )
                )
            }
        }
    }

    fun importConvolverImpulseResponse(uri: String) {
        viewModelScope.launch {
            // Import the file first
            val fileName = viperAssetRepository.importKernel(uri)
            if (fileName != null) {
                viperRepository.updateEffectsState {
                    it.copy(
                        convolver = it.convolver.copy(
                            impulseResponse = fileName
                        )
                    )
                }
                Toast.makeText(context, "Impulse loaded: $fileName", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Failed to load impulse response", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    fun deleteConvolverImpulseResponse(fileName: String) {
        viewModelScope.launch {
            viperAssetRepository.deleteKernel(fileName)
            viperRepository.updateEffectsState { state ->
                if (state.convolver.impulseResponse == fileName) {
                    state.copy(convolver = state.convolver.copy(impulseResponse = null))
                } else {
                    state
                }
            }
        }
    }

    fun setConvolverCrossChannel(value: Int) {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(convolver = it.convolver.copy(crossChannel = value)) }
        }
    }

    // Speaker Optimization
    fun setSpeakerOptimizationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            viperRepository.updateEffectsState {
                it.copy(
                    speakerOptimization = it.speakerOptimization.copy(
                        enabled = enabled
                    )
                )
            }
        }
    }

    // IIR Equalizer
    fun setIirEqualizerEnabled(enabled: Boolean) {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(iirEqualizer = it.iirEqualizer.copy(enabled = enabled)) }
        }
    }

    fun setIirEqualizerBandCount(count: Int) {
        viewModelScope.launch {
            viperRepository.updateEffectsState { state ->
                // For a named preset, use its gains at the new count. For a custom curve, resize the
                // existing gains (truncate/pad) instead of fetching "Custom" — which isn't a real
                // preset and would flatten/wipe the user's curve.
                val newGains = if (state.iirEqualizer.preset == "Custom") {
                    List(count) { i -> state.iirEqualizer.bandGains.getOrElse(i) { 0f } }
                } else {
                    IirEqualizerPresets.getPresetGains(state.iirEqualizer.preset, count)
                }
                state.copy(
                    iirEqualizer = state.iirEqualizer.copy(
                        bandCount = count,
                        bandGains = newGains
                    )
                )
            }
        }
    }

    fun setIirEqualizerPreset(preset: String) {
        viewModelScope.launch {
            viperRepository.updateEffectsState { state ->
                val newGains =
                    IirEqualizerPresets.getPresetGains(preset, state.iirEqualizer.bandCount)
                state.copy(
                    iirEqualizer = state.iirEqualizer.copy(
                        preset = preset,
                        bandGains = newGains
                    )
                )
            }
        }
    }

    fun setIirEqualizerBandGain(index: Int, gain: Float) {
        viewModelScope.launch {
            viperRepository.updateEffectsState { state ->
                val currentGains = state.iirEqualizer.bandGains.toMutableList()
                if (index in currentGains.indices) {
                    currentGains[index] = gain
                }
                state.copy(
                    iirEqualizer = state.iirEqualizer.copy(
                        preset = "Custom", // Auto-switch to Custom
                        bandGains = currentGains
                    )
                )
            }
        }
    }

    fun resetIirEqualizer() {
        viewModelScope.launch {
            viperRepository.updateEffectsState { state ->
                state.copy(
                    iirEqualizer = IirEqualizerState()
                )
            }
        }
    }
}

