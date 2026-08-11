package com.viperplayer.data.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.viperplayer.domain.audio.Resampler
import com.viperplayer.domain.model.ViperEffectsState
import com.viperplayer.domain.model.ViperSteppedValues
import com.viperplayer.domain.repository.ViperAssetRepository
import com.viperplayer.domain.repository.ViperRepository
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
import kotlin.jvm.Volatile

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
    private val impulseResponseDecoder: ImpulseResponseDecoder,
    private val nativeDriver: ViperNativeDriver,
    private val stateApplier: ViperEffectsStateApplier,
) : BaseAudioProcessor() {

    private val processorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Track previous state to detect changes and only update what's changed. @Volatile because it's
    // written from a Default-dispatcher coroutine but read on the audio (queueInput) thread.
    @Volatile
    private var currentState: ViperEffectsState? = null

    /**
     * When true, audio passes through untouched (no native processing) — backs the "Bypass DSP"
     * setting. Written from the playback service (main thread), read on the audio thread.
     */
    @Volatile
    var bypassed: Boolean = false

    /** ViPER processes interleaved stereo; non-stereo formats pass through untouched (set in onConfigure). */
    @Volatile
    private var stereo: Boolean = true

    // Cache for loaded IR path to avoid reloading same file. @Volatile because it is written from
    // the IO coroutine that decodes the kernel and read by updateNativeDriverConfiguration on the
    // Default dispatcher (and, via onReset, on the playback thread).
    @Volatile
    private var loadedIrPath: String? = null

    /**
     * Sample rate the currently loaded impulse response was resampled to, or 0 when none is loaded.
     * A kernel is only correct at the rate it was converted for, so a stream at a different rate has
     * to reload it — see [applyImpulseResponse].
     */
    @Volatile
    private var loadedIrSampleRate: Int = 0

    /** The stream's sample rate, from [onConfigure]. 0 until the first stream is configured. */
    @Volatile
    private var streamSampleRate: Int = 0

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
        // Reconfigure the native engine for the ACTUAL sample rate — its DSP coefficients (EQ centers,
        // reverb decay, bass crossover, …) are rate-dependent and were previously fixed at 44.1 kHz.
        nativeDriver.setSamplingRate(inputAudioFormat.sampleRate)
        streamSampleRate = inputAudioFormat.sampleRate
        // ViPER is a stereo engine; pass other channel counts through untouched rather than mis-framing.
        stereo = inputAudioFormat.channelCount == 2
        // The convolver kernel is resampled for one specific rate, so a stream at a different rate
        // needs it reloaded. Nothing else re-triggers this: the effects state has not changed.
        currentState?.let { applyImpulseResponse(it) }
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

        if (currentState?.enabled == true && !bypassed && stereo) {
            nativeDriver.process(
                outputBuffer,
                offset,
                size
            )
        }
    }

    override fun onReset() {
        nativeDriver.reset()
        // force = true is load-bearing. Without it this re-application is a no-op: it passes
        // `currentState` into a function that diffs against `currentState`, so every comparison is
        // false and nothing at all is pushed back to the native engine.
        currentState?.let { updateNativeDriverConfiguration(it, force = true) }
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
     * Pushes [state] to the native engine and remembers it as the new baseline.
     *
     * @param force re-sends every value rather than just the changed ones. Needed after the engine
     *   has been reset underneath us, where the last-sent state no longer describes what it holds.
     *
     * Synchronized because it is called both from the effects-state coroutine (Dispatchers.Default)
     * and from [onReset] on the playback thread. The `read currentState … write currentState` span
     * is not atomic, so two concurrent runs could interleave and leave the engine holding values
     * from neither state.
     */
    @Synchronized
    private fun updateNativeDriverConfiguration(state: ViperEffectsState, force: Boolean = false) {
        stateApplier.apply(previous = if (force) null else currentState, next = state)
        applyImpulseResponse(state)
        currentState = state
    }

    /**
     * Loads (or clears) the convolver's impulse response for [state].
     *
     * Kept out of [ViperEffectsStateApplier] because decoding a kernel is asynchronous and needs a
     * coroutine scope, where everything else that applier does is a straight-line native call.
     *
     * Reloads whenever the file changes OR the stream's sample rate does: the native convolver
     * convolves the kernel sample-for-sample against the stream and is never told what rate the
     * kernel was recorded at, so the conversion has to happen here.
     */
    private fun applyImpulseResponse(state: ViperEffectsState) {
        val newIrPath = state.convolver.impulseResponse
        val targetRate = streamSampleRate
        val needsLoad = newIrPath != null &&
                (newIrPath != loadedIrPath || targetRate != loadedIrSampleRate)

        if (needsLoad) {
            // Wait for the first stream: without a target rate there is nothing to convert to, and
            // onConfigure calls back here as soon as it knows one.
            if (targetRate <= 0) return
            // Load in background
            processorScope.launch(Dispatchers.IO) {
                try {
                    // Resolve file from repository
                    val kernelFile = viperAssetRepository.getKernelFile(newIrPath)
                    if (kernelFile != null && kernelFile.exists()) {
                        val decoded = impulseResponseDecoder.decode(kernelFile.absolutePath)
                        if (decoded != null) {
                            // A kernel is a time-domain response: convolved against a stream at a
                            // different rate it is stretched or squashed, which moves every feature
                            // of the response off its intended frequency.
                            val kernel = Resampler.resampleInterleaved(
                                interleaved = decoded.interleaved,
                                channels = decoded.channels,
                                srcSampleRate = decoded.sampleRate,
                                dstSampleRate = targetRate,
                            )
                            Timber.d(
                                "Loaded IR file: %s, channels: %d, samples: %d, rate: %d -> %d",
                                kernelFile.name, decoded.channels, kernel.size, decoded.sampleRate, targetRate,
                            )
                            nativeDriver.setConvolverImpulseResponse(decoded.channels, kernel)
                            loadedIrPath = newIrPath
                            loadedIrSampleRate = targetRate
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
            loadedIrSampleRate = 0
        }
    }
}

