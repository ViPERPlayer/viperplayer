package com.viperplayer.data.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.viperplayer.domain.model.ViperEffectsState
import com.viperplayer.domain.model.ViperSteppedValues
import com.viperplayer.domain.repository.DdcRepository
import com.viperplayer.domain.repository.ViperRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import timber.log.Timber
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ViPER audio processor that processes PCM audio data using native ViPER effects.
 * 
 * This class:
 * - Processes audio through the native ViPER driver
 * - Observes effects state changes and updates the native driver configuration
 * - Only updates changed values to avoid unnecessary recalculations
 * - Runs continuously, ensuring the native driver always has the correct settings
 */
@OptIn(UnstableApi::class)
@Singleton
class ViperAudioProcessor @Inject constructor(
    private val viperRepository: ViperRepository,
    private val ddcRepository: DdcRepository,
    private val nativeDriver: ViperNativeDriver
) : BaseAudioProcessor() {

    private val processorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    // Track previous state to detect changes and only update what's changed
    private var currentState: ViperEffectsState? = null

    init {
        // Start observing effects state and enabled state changes
        observeEffectsState()
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        Timber.d("ViperAudioProcessor.onConfigure called: $inputAudioFormat")
        if (inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            Timber.d("Unsupported audio format: $inputAudioFormat")
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) {
            return
        }

        val outputBuffer = replaceOutputBuffer(size)
        val offset = outputBuffer.position()
        outputBuffer.put(inputBuffer)
        outputBuffer.flip()

        if (currentState?.enabled == true) {
            nativeDriver.process(
                outputBuffer,
                offset,
                size
            )
        }
    }

    override fun onReset() {
        nativeDriver.reset()
        currentState?.let { updateNativeDriverConfiguration(it) }
    }

    /**
     * Observes effects state changes and updates the native driver configuration.
     * Only changed values are updated to avoid unnecessary recalculations.
     */
    private fun observeEffectsState() {
        viperRepository.effectsState
            .onEach { effectsState ->
                updateNativeDriverConfiguration(effectsState)
            }
            .launchIn(processorScope)
    }

    /**
     * Updates the native driver configuration with only the changed values.
     * This prevents unnecessary recalculations and pipeline clears.
     */
    private fun updateNativeDriverConfiguration(state: ViperEffectsState) {
        val current = currentState
        
        // Master Limiter - combine gain and pan to calculate left/right gains
        val gainChanged = current == null || current.masterLimiter.outputGain != state.masterLimiter.outputGain
        val panChanged = current == null || current.masterLimiter.outputPan != state.masterLimiter.outputPan
        
        if (gainChanged || panChanged) {
            val (gainL, gainR) = ViperSteppedValues.calculateLeftRightGains(
                state.masterLimiter.outputGain,
                state.masterLimiter.outputPan
            )
            nativeDriver.setMasterLimiterOutputGain(gainL, gainR)
        }
        if (current == null || current.masterLimiter.thresholdLimit != state.masterLimiter.thresholdLimit) {
            val thresholdValue = ViperSteppedValues.getThresholdLimitValue(state.masterLimiter.thresholdLimit)
            nativeDriver.setMasterLimiterThresholdLimit(thresholdValue)
        }

        // IIR Equalizer
        if (current == null || current.iirEqualizer.enabled != state.iirEqualizer.enabled) {
            nativeDriver.setIirEqualizerEnabled(state.iirEqualizer.enabled)
        }
        if (current == null || current.iirEqualizer.bandCount != state.iirEqualizer.bandCount) {
            nativeDriver.setIirEqualizerBandCount(when (state.iirEqualizer.bandCount) {
                10 -> 0
                15 -> 1
                31 -> 2
                else -> 0
            })
        }
        // Always update bands if any relevant state changed, or do granular check.
        // Granular check is better for performance if many bands.
        state.iirEqualizer.bandGains.forEachIndexed { index, gain ->
            if (current == null ||
                current.iirEqualizer.bandGains.size <= index ||
                current.iirEqualizer.bandGains[index] != gain) {
                nativeDriver.setIirEqualizerBandLevel(index, gain)
            }
        }

        // Spectrum Extension
        if (current == null || current.spectrumExtension.enabled != state.spectrumExtension.enabled) {
            nativeDriver.setSpectrumExtensionEnabled(state.spectrumExtension.enabled)
        }
        if (current == null || current.spectrumExtension.strength != state.spectrumExtension.strength) {
            nativeDriver.setSpectrumExtensionStrength(state.spectrumExtension.strength)
        }

        // Field Surround
        if (current == null || current.fieldSurround.enabled != state.fieldSurround.enabled) {
            nativeDriver.setFieldSurroundEnabled(state.fieldSurround.enabled)
        }
        if (current == null || current.fieldSurround.surroundStrength != state.fieldSurround.surroundStrength) {
            nativeDriver.setFieldSurroundStrength(state.fieldSurround.surroundStrength)
        }
        if (current == null || current.fieldSurround.midImageStrength != state.fieldSurround.midImageStrength) {
            nativeDriver.setFieldSurroundMidImageStrength(state.fieldSurround.midImageStrength)
        }

        // Differential Surround
        if (current == null || current.differentialSurround.enabled != state.differentialSurround.enabled) {
            nativeDriver.setDifferentialSurroundEnabled(state.differentialSurround.enabled)
        }
        if (current == null || current.differentialSurround.delay != state.differentialSurround.delay) {
            nativeDriver.setDifferentialSurroundDelay(state.differentialSurround.delay)
        }

        // Dynamic System
        if (current == null || current.dynamicSystem.enabled != state.dynamicSystem.enabled) {
            nativeDriver.setDynamicSystemEnabled(state.dynamicSystem.enabled)
        }
        if (current == null || current.dynamicSystem.deviceType != state.dynamicSystem.deviceType) {
            nativeDriver.setDynamicSystemDeviceType(state.dynamicSystem.deviceType.ordinal)
        }
        if (current == null || current.dynamicSystem.dynamicBassStrength != state.dynamicSystem.dynamicBassStrength) {
            nativeDriver.setDynamicSystemBassStrength(100 + 20 * state.dynamicSystem.dynamicBassStrength)
        }

        // Tube Simulator
        if (current == null || current.tubeSimulator.enabled != state.tubeSimulator.enabled) {
            nativeDriver.setTubeSimulatorEnabled(state.tubeSimulator.enabled)
        }

        // ViPER Bass
        if (current == null || current.viperBass.enabled != state.viperBass.enabled) {
            nativeDriver.setViperBassEnabled(state.viperBass.enabled)
        }
        if (current == null || current.viperBass.mode != state.viperBass.mode) {
            nativeDriver.setViperBassMode(state.viperBass.mode)
        }
        if (current == null || current.viperBass.frequency != state.viperBass.frequency) {
            nativeDriver.setViperBassFrequency(state.viperBass.frequency)
        }
        if (current == null || current.viperBass.gain != state.viperBass.gain) {
            nativeDriver.setViperBassGain(state.viperBass.gain + 1)
        }

        // ViPER Clarity
        if (current == null || current.viperClarity.enabled != state.viperClarity.enabled) {
            nativeDriver.setViperClarityEnabled(state.viperClarity.enabled)
        }
        if (current == null || current.viperClarity.mode != state.viperClarity.mode) {
            nativeDriver.setViperClarityMode(state.viperClarity.mode)
        }
        if (current == null || current.viperClarity.gain != state.viperClarity.gain) {
            nativeDriver.setViperClarityGain(state.viperClarity.gain)
        }

        // Auditory System Protection
        if (current == null || current.auditorySystemProtection.enabled != state.auditorySystemProtection.enabled) {
            nativeDriver.setAuditorySystemProtectionEnabled(state.auditorySystemProtection.enabled)
        }
        if (current == null || current.auditorySystemProtection.level != state.auditorySystemProtection.level) {
            nativeDriver.setAuditorySystemProtectionLevel(state.auditorySystemProtection.level)
        }

        // Analog X
        if (current == null || current.analogX.enabled != state.analogX.enabled) {
            nativeDriver.setAnalogXEnabled(state.analogX.enabled)
        }
        if (current == null || current.analogX.level != state.analogX.level) {
            nativeDriver.setAnalogXLevel(state.analogX.level)
        }

        // Speaker Optimization
        if (current == null || current.speakerOptimization.enabled != state.speakerOptimization.enabled) {
            nativeDriver.setSpeakerOptimizationEnabled(state.speakerOptimization.enabled)
        }

        // ViPER DDC
        if (current == null || current.viperDdc.enabled != state.viperDdc.enabled) {
            nativeDriver.setViperDdcEnabled(state.viperDdc.enabled)
        }
        
        // Check for coefficients change (content) rather than just file name
        if (current == null || current.viperDdc.coeffs != state.viperDdc.coeffs) {
            val coeffsMap = state.viperDdc.coeffs
            
            // Always clear first when content changes
            nativeDriver.viperDdcClearCoeffs()
            
            if (coeffsMap != null && coeffsMap.isNotEmpty()) {
                coeffsMap.forEach { (rate, coeffs) ->
                     nativeDriver.viperDdcAddCoeffs(rate, coeffs.toFloatArray())
                }
            }
        }
        
        // Update previous state
        currentState = state
    }
}

