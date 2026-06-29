package com.viperplayer.data.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.viperplayer.domain.model.ViperEffectsState
import com.viperplayer.domain.model.ViperSteppedValues
import com.viperplayer.domain.repository.ViperAssetRepository
import com.viperplayer.domain.repository.ViperRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
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
    private val viperAssetRepository: ViperAssetRepository,
    private val audioFileDecoder: AudioFileDecoder,
    private val nativeDriver: ViperNativeDriver,
    @ApplicationContext private val context: Context
) : BaseAudioProcessor() {

    private val processorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Track previous state to detect changes and only update what's changed
    private var currentState: ViperEffectsState? = null

    // Cache for loaded IR path to avoid reloading same file
    private var loadedIrPath: String? = null

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
        val gainChanged =
            current == null || current.masterLimiter.outputGain != state.masterLimiter.outputGain
        val panChanged =
            current == null || current.masterLimiter.outputPan != state.masterLimiter.outputPan

        if (gainChanged || panChanged) {
            val (gainL, gainR) = ViperSteppedValues.calculateLeftRightGains(
                state.masterLimiter.outputGain,
                state.masterLimiter.outputPan
            )
            nativeDriver.setMasterLimiterOutputGain(gainL, gainR)
        }
        if (current == null || current.masterLimiter.thresholdLimit != state.masterLimiter.thresholdLimit) {
            val thresholdValue =
                ViperSteppedValues.getThresholdLimitValue(state.masterLimiter.thresholdLimit)
            nativeDriver.setMasterLimiterThresholdLimit(thresholdValue)
        }

        // IIR Equalizer
        if (current == null || current.iirEqualizer.enabled != state.iirEqualizer.enabled) {
            nativeDriver.setIirEqualizerEnabled(state.iirEqualizer.enabled)
        }
        if (current == null || current.iirEqualizer.bandCount != state.iirEqualizer.bandCount) {
            nativeDriver.setIirEqualizerBandCount(
                when (state.iirEqualizer.bandCount) {
                    10 -> 0
                    15 -> 1
                    31 -> 2
                    else -> 0
                }
            )
        }
        // Always update bands if any relevant state changed, or do granular check.
        // Granular check is better for performance if many bands.
        state.iirEqualizer.bandGains.forEachIndexed { index, gain ->
            if (current == null ||
                current.iirEqualizer.bandGains.size <= index ||
                current.iirEqualizer.bandGains[index] != gain
            ) {
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
        nativeDriver.setDifferentialSurroundEnabled(state.differentialSurround.enabled)
        nativeDriver.setDifferentialSurroundDelay(state.differentialSurround.delay)

        // Playback Gain Control
        nativeDriver.setPlaybackGainEnabled(state.playbackGain.enabled)
        nativeDriver.setPlaybackGainStrength(state.playbackGain.strength)
        nativeDriver.setPlaybackGainMaxGain(state.playbackGain.maxGain)
        nativeDriver.setPlaybackGainOutputThreshold(state.playbackGain.outputThreshold)

        // Dynamic System
        nativeDriver.setDynamicSystemEnabled(state.dynamicSystem.enabled)
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

        // Convolver
        if (current == null || current.convolver.enabled != state.convolver.enabled) {
            nativeDriver.setConvolverEnabled(state.convolver.enabled)
        }
        if (current == null || current.convolver.crossChannel != state.convolver.crossChannel) {
            nativeDriver.setConvolverCrossChannel(state.convolver.crossChannel)
        }

        // FET Compressor
        if (current == null || current.fetCompressor.enabled != state.fetCompressor.enabled) {
            nativeDriver.setFetCompressorEnabled(state.fetCompressor.enabled)
        }
        if (current == null || current.fetCompressor.threshold != state.fetCompressor.threshold) {
            nativeDriver.setFetCompressorThreshold(state.fetCompressor.threshold)
        }
        if (current == null || current.fetCompressor.ratio != state.fetCompressor.ratio) {
            nativeDriver.setFetCompressorRatio(state.fetCompressor.ratio)
        }
        if (current == null || current.fetCompressor.knee != state.fetCompressor.knee) {
            nativeDriver.setFetCompressorKnee(state.fetCompressor.knee)
        }
        if (current == null || current.fetCompressor.autoKnee != state.fetCompressor.autoKnee) {
            nativeDriver.setFetCompressorAutoKnee(state.fetCompressor.autoKnee)
        }
        if (current == null || current.fetCompressor.gain != state.fetCompressor.gain) {
            nativeDriver.setFetCompressorGain(state.fetCompressor.gain)
        }
        if (current == null || current.fetCompressor.autoGain != state.fetCompressor.autoGain) {
            nativeDriver.setFetCompressorAutoGain(state.fetCompressor.autoGain)
        }
        if (current == null || current.fetCompressor.attack != state.fetCompressor.attack) {
            nativeDriver.setFetCompressorAttack(state.fetCompressor.attack)
        }
        if (current == null || current.fetCompressor.autoAttack != state.fetCompressor.autoAttack) {
            nativeDriver.setFetCompressorAutoAttack(state.fetCompressor.autoAttack)
        }
        if (current == null || current.fetCompressor.release != state.fetCompressor.release) {
            nativeDriver.setFetCompressorRelease(state.fetCompressor.release)
        }
        if (current == null || current.fetCompressor.autoRelease != state.fetCompressor.autoRelease) {
            nativeDriver.setFetCompressorAutoRelease(state.fetCompressor.autoRelease)
        }
        if (current == null || current.fetCompressor.kneeMulti != state.fetCompressor.kneeMulti) {
            nativeDriver.setFetCompressorKneeMulti(state.fetCompressor.kneeMulti)
        }
        if (current == null || current.fetCompressor.maxAttack != state.fetCompressor.maxAttack) {
            nativeDriver.setFetCompressorMaxAttack(state.fetCompressor.maxAttack)
        }
        if (current == null || current.fetCompressor.maxRelease != state.fetCompressor.maxRelease) {
            nativeDriver.setFetCompressorMaxRelease(state.fetCompressor.maxRelease)
        }
        if (current == null || current.fetCompressor.crest != state.fetCompressor.crest) {
            nativeDriver.setFetCompressorCrest(state.fetCompressor.crest)
        }
        if (current == null || current.fetCompressor.adapt != state.fetCompressor.adapt) {
            nativeDriver.setFetCompressorAdapt(state.fetCompressor.adapt)
        }
        if (current == null || current.fetCompressor.noClip != state.fetCompressor.noClip) {
            nativeDriver.setFetCompressorNoClip(state.fetCompressor.noClip)
        }

        // Headphone Surround+ (VHE)
        if (current == null || current.headphoneSurround.enabled != state.headphoneSurround.enabled) {
            nativeDriver.setHeadphoneSurroundEnabled(state.headphoneSurround.enabled)
        }
        if (current == null || current.headphoneSurround.level != state.headphoneSurround.level) {
            nativeDriver.setHeadphoneSurroundLevel(state.headphoneSurround.level)
        }

        // Reverberation
        if (current == null || current.reverberation.enabled != state.reverberation.enabled) {
            nativeDriver.setReverberationEnabled(state.reverberation.enabled)
        }
        if (current == null || current.reverberation.roomSize != state.reverberation.roomSize) {
            nativeDriver.setReverberationRoomSize(state.reverberation.roomSize)
        }
        if (current == null || current.reverberation.width != state.reverberation.width) {
            nativeDriver.setReverberationWidth(state.reverberation.width)
        }
        if (current == null || current.reverberation.damp != state.reverberation.damp) {
            nativeDriver.setReverberationDamp(state.reverberation.damp)
        }
        if (current == null || current.reverberation.wet != state.reverberation.wet) {
            nativeDriver.setReverberationWet(state.reverberation.wet)
        }
        if (current == null || current.reverberation.dry != state.reverberation.dry) {
            nativeDriver.setReverberationDry(state.reverberation.dry)
        }

        // Handle Impulse Response File Loading
        val newIrPath = state.convolver.impulseResponse
        if (newIrPath != null && newIrPath != loadedIrPath) {
            // Load in background
            processorScope.launch(Dispatchers.IO) {
                try {
                    // Resolve file from repository
                    val kernelFile = viperAssetRepository.getKernelFile(newIrPath)
                    if (kernelFile != null && kernelFile.exists()) {
                        val uri = Uri.fromFile(kernelFile)
                        val decoded = audioFileDecoder.decodeToFloatArray(context, uri)
                        if (decoded != null) {
                            Timber.d("Loaded IR file: ${kernelFile.name}, Channels: ${decoded.second}, Samples: ${decoded.first.size}")
                            nativeDriver.setConvolverImpulseResponse(decoded.second, decoded.first)
                            loadedIrPath = newIrPath
                        } else {
                            Timber.e("Failed to decode IR file: $newIrPath")
                        }
                    } else {
                        Timber.e("Kernel file not found: $newIrPath")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error loading IR file: $newIrPath")
                }
            }
        } else if (newIrPath == null && loadedIrPath != null) {
            // Clear kernel
            nativeDriver.setConvolverImpulseResponse(1, FloatArray(0))
            loadedIrPath = null
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

