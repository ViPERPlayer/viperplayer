package com.viperplayer.presentation.viper

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Stable
import com.viperplayer.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.domain.model.AutoEqParser
import com.viperplayer.domain.model.AutoEqProfile
import com.viperplayer.domain.model.DynamicSystemDeviceType
import com.viperplayer.domain.model.FetCompressorState
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
@Stable
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

    /**
     * The AutoEq profile behind the current "Custom" curve, iff that curve came from an import.
     * Retained (transiently, in the ViewModel) so a band-count change can re-interpolate the
     * original correction curve onto the new band centers instead of truncating/zero-padding.
     * Any manual Custom edit or preset switch clears it (see [clearImportedAutoEqProfile]).
     */
    private var importedAutoEqProfile: AutoEqProfile? = null

    private fun clearImportedAutoEqProfile() {
        importedAutoEqProfile = null
    }

    fun setIirEqualizerBandCount(count: Int) {
        viewModelScope.launch {
            val imported = importedAutoEqProfile
            viperRepository.updateEffectsState { state ->
                val newGains = when {
                    // Custom curve that came from an AutoEq import: re-interpolate the original
                    // correction curve onto the new band centers so the imported response is
                    // preserved across a band-count change (not truncated/zero-padded).
                    state.iirEqualizer.preset == "Custom" && imported != null ->
                        imported.gainsForBands(
                            bandFrequenciesHz = IirEqualizerPresets.getFrequencies(count),
                            minGainDb = IirEqualizerPresets.MIN_GAIN_DB,
                            maxGainDb = IirEqualizerPresets.MAX_GAIN_DB,
                        )
                    // A hand-edited Custom curve: resize the existing gains (truncate/pad) instead
                    // of fetching "Custom" — which isn't a real preset and would flatten it.
                    state.iirEqualizer.preset == "Custom" ->
                        List(count) { i -> state.iirEqualizer.bandGains.getOrElse(i) { 0f } }
                    // Named preset: use its gains at the new count.
                    else -> IirEqualizerPresets.getPresetGains(state.iirEqualizer.preset, count)
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
        // Choosing a named preset abandons any imported AutoEq curve.
        clearImportedAutoEqProfile()
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
        // A manual band edit turns the curve into a hand-edited Custom one; the imported profile no
        // longer describes it, so stop re-interpolating from it on band-count changes.
        clearImportedAutoEqProfile()
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
        clearImportedAutoEqProfile()
        viewModelScope.launch {
            viperRepository.updateEffectsState { state ->
                state.copy(
                    iirEqualizer = IirEqualizerState()
                )
            }
        }
    }

    /**
     * Import an AutoEq headphone-correction profile (GraphicEQ or ParametricEQ text) from [uri],
     * resample its curve onto the equalizer's current fixed band center-frequencies (log-frequency
     * linear interpolation), and apply the resulting per-band gains (with any profile preamp
     * folded in and clamped to the EQ's dB range). The file read runs on an IO dispatcher inside
     * the repository; parsing happens in the pure [AutoEqParser].
     */
    fun importAutoEqProfile(uri: String) {
        viewModelScope.launch {
            val text = viperAssetRepository.readTextFromUri(uri)
            if (text == null) {
                Toast.makeText(
                    context,
                    context.getString(R.string.iir_autoeq_read_failed),
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }

            when (val result = AutoEqParser.parse(text)) {
                is AutoEqParser.ParseResult.Success -> {
                    // Retain the parsed profile so a later band-count change re-interpolates the
                    // original correction curve instead of truncating/zero-padding the gains.
                    importedAutoEqProfile = result.profile
                    viperRepository.updateEffectsState { state ->
                        val bandFrequencies =
                            IirEqualizerPresets.getFrequencies(state.iirEqualizer.bandCount)
                        val gains = result.profile.gainsForBands(
                            bandFrequenciesHz = bandFrequencies,
                            minGainDb = IirEqualizerPresets.MIN_GAIN_DB,
                            maxGainDb = IirEqualizerPresets.MAX_GAIN_DB,
                        )
                        state.copy(
                            iirEqualizer = state.iirEqualizer.copy(
                                enabled = true,
                                preset = "Custom",
                                bandGains = gains,
                            )
                        )
                    }
                    Toast.makeText(
                        context,
                        context.getString(R.string.iir_autoeq_imported),
                        Toast.LENGTH_SHORT
                    ).show()
                }

                is AutoEqParser.ParseResult.Error -> Toast.makeText(
                    context,
                    context.getString(R.string.iir_autoeq_import_failed, result.reason),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // FET Compressor
    private val fetDefaults = FetCompressorState()

    private fun updateFet(transform: (FetCompressorState) -> FetCompressorState) {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(fetCompressor = transform(it.fetCompressor)) }
        }
    }

    fun setFetCompressorEnabled(enabled: Boolean) = updateFet { it.copy(enabled = enabled) }

    fun setFetCompressorThreshold(value: Int) = updateFet { it.copy(threshold = value) }
    fun resetFetCompressorThreshold() = updateFet { it.copy(threshold = fetDefaults.threshold) }

    fun setFetCompressorRatio(value: Int) = updateFet { it.copy(ratio = value) }
    fun resetFetCompressorRatio() = updateFet { it.copy(ratio = fetDefaults.ratio) }

    fun setFetCompressorKnee(value: Int) = updateFet { it.copy(knee = value) }
    fun resetFetCompressorKnee() = updateFet { it.copy(knee = fetDefaults.knee) }

    fun setFetCompressorAutoKnee(enabled: Boolean) = updateFet { it.copy(autoKnee = enabled) }

    fun setFetCompressorGain(value: Int) = updateFet { it.copy(gain = value) }
    fun resetFetCompressorGain() = updateFet { it.copy(gain = fetDefaults.gain) }

    fun setFetCompressorAutoGain(enabled: Boolean) = updateFet { it.copy(autoGain = enabled) }

    fun setFetCompressorAttack(value: Int) = updateFet { it.copy(attack = value) }
    fun resetFetCompressorAttack() = updateFet { it.copy(attack = fetDefaults.attack) }

    fun setFetCompressorAutoAttack(enabled: Boolean) = updateFet { it.copy(autoAttack = enabled) }

    fun setFetCompressorRelease(value: Int) = updateFet { it.copy(release = value) }
    fun resetFetCompressorRelease() = updateFet { it.copy(release = fetDefaults.release) }

    fun setFetCompressorAutoRelease(enabled: Boolean) = updateFet { it.copy(autoRelease = enabled) }

    fun setFetCompressorKneeMulti(value: Int) = updateFet { it.copy(kneeMulti = value) }
    fun resetFetCompressorKneeMulti() = updateFet { it.copy(kneeMulti = fetDefaults.kneeMulti) }

    fun setFetCompressorMaxAttack(value: Int) = updateFet { it.copy(maxAttack = value) }
    fun resetFetCompressorMaxAttack() = updateFet { it.copy(maxAttack = fetDefaults.maxAttack) }

    fun setFetCompressorMaxRelease(value: Int) = updateFet { it.copy(maxRelease = value) }
    fun resetFetCompressorMaxRelease() = updateFet { it.copy(maxRelease = fetDefaults.maxRelease) }

    fun setFetCompressorCrest(value: Int) = updateFet { it.copy(crest = value) }
    fun resetFetCompressorCrest() = updateFet { it.copy(crest = fetDefaults.crest) }

    fun setFetCompressorAdapt(value: Int) = updateFet { it.copy(adapt = value) }
    fun resetFetCompressorAdapt() = updateFet { it.copy(adapt = fetDefaults.adapt) }

    fun setFetCompressorNoClip(enabled: Boolean) = updateFet { it.copy(noClip = enabled) }

    // Headphone Surround+ (VHE)
    fun setHeadphoneSurroundEnabled(enabled: Boolean) {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(headphoneSurround = it.headphoneSurround.copy(enabled = enabled)) }
        }
    }

    fun setHeadphoneSurroundLevel(level: Int) {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(headphoneSurround = it.headphoneSurround.copy(level = level)) }
        }
    }

    fun resetHeadphoneSurroundLevel() {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(headphoneSurround = it.headphoneSurround.copy(level = 0)) }
        }
    }

    // Reverberation
    fun setReverberationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(reverberation = it.reverberation.copy(enabled = enabled)) }
        }
    }

    fun setReverberationRoomSize(value: Int) {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(reverberation = it.reverberation.copy(roomSize = value)) }
        }
    }

    fun resetReverberationRoomSize() {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(reverberation = it.reverberation.copy(roomSize = ViperDefaults.REVERBERATION_ROOM_SIZE)) }
        }
    }

    fun setReverberationWidth(value: Int) {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(reverberation = it.reverberation.copy(width = value)) }
        }
    }

    fun resetReverberationWidth() {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(reverberation = it.reverberation.copy(width = ViperDefaults.REVERBERATION_WIDTH)) }
        }
    }

    fun setReverberationDamp(value: Int) {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(reverberation = it.reverberation.copy(damp = value)) }
        }
    }

    fun resetReverberationDamp() {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(reverberation = it.reverberation.copy(damp = ViperDefaults.REVERBERATION_DAMP)) }
        }
    }

    fun setReverberationWet(value: Int) {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(reverberation = it.reverberation.copy(wet = value)) }
        }
    }

    fun resetReverberationWet() {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(reverberation = it.reverberation.copy(wet = ViperDefaults.REVERBERATION_WET)) }
        }
    }

    fun setReverberationDry(value: Int) {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(reverberation = it.reverberation.copy(dry = value)) }
        }
    }

    fun resetReverberationDry() {
        viewModelScope.launch {
            viperRepository.updateEffectsState { it.copy(reverberation = it.reverberation.copy(dry = ViperDefaults.REVERBERATION_DRY)) }
        }
    }
}

