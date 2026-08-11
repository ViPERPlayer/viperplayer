package com.viperplayer.data.player

import com.viperplayer.domain.model.ViperEffectsState
import com.viperplayer.domain.model.ViperSteppedValues
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Translates a [ViperEffectsState] into calls on the native [ViperEngine], sending only the values
 * that actually changed.
 *
 * Split out of [ViperAudioProcessor] because this is where the interesting decisions live — roughly
 * sixty "did this field change?" branches, one per effect parameter — and inside an
 * `AudioProcessor` subclass they could not be tested at all. Skipping unchanged values is not just
 * an optimisation: several native setters recompute filter coefficients, and re-sending an
 * unchanged value would disturb an effect that the user never touched.
 *
 * Stateless by design. The caller owns the "what did we send last time?" state and passes it in as
 * [previous], which makes re-applying everything a matter of passing `null` rather than a flag that
 * has to thread through sixty branches.
 */
@Singleton
class ViperEffectsStateApplier @Inject constructor(
    private val engine: ViperEngine,
) {

    /**
     * Pushes the difference between [previous] and [next] to the engine.
     *
     * @param previous the state last sent to the engine, or null to send everything. Null is the
     *   right answer whenever the engine has been reset underneath us and the last-sent state no
     *   longer describes what it actually holds.
     */
    fun apply(previous: ViperEffectsState?, next: ViperEffectsState) {
        val current = previous
        val state = next


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
            engine.setMasterLimiterOutputGain(gainL, gainR)
        }
        if (current == null || current.masterLimiter.thresholdLimit != state.masterLimiter.thresholdLimit) {
            val thresholdValue =
                ViperSteppedValues.getThresholdLimitValue(state.masterLimiter.thresholdLimit)
            engine.setMasterLimiterThresholdLimit(thresholdValue)
        }

        // IIR Equalizer
        if (current == null || current.iirEqualizer.enabled != state.iirEqualizer.enabled) {
            engine.setIirEqualizerEnabled(state.iirEqualizer.enabled)
        }
        // Band count and gains go over in ONE native call. Sending the count and then each gain
        // separately let the audio thread render a buffer against a half-applied curve — new band
        // count with old gains, or only some bands updated. Skipped entirely when neither changed,
        // so a slider that is not moving costs nothing.
        if (current == null ||
            current.iirEqualizer.bandCount != state.iirEqualizer.bandCount ||
            current.iirEqualizer.bandGains != state.iirEqualizer.bandGains
        ) {
            engine.setIirEqualizerBands(
                when (state.iirEqualizer.bandCount) {
                    10 -> 0
                    15 -> 1
                    31 -> 2
                    else -> 0
                },
                state.iirEqualizer.bandGains.toFloatArray(),
            )
        }

        // Spectrum Extension
        if (current == null || current.spectrumExtension.enabled != state.spectrumExtension.enabled) {
            engine.setSpectrumExtensionEnabled(state.spectrumExtension.enabled)
        }
        if (current == null || current.spectrumExtension.strength != state.spectrumExtension.strength) {
            engine.setSpectrumExtensionStrength(state.spectrumExtension.strength)
        }

        // Field Surround
        if (current == null || current.fieldSurround.enabled != state.fieldSurround.enabled) {
            engine.setFieldSurroundEnabled(state.fieldSurround.enabled)
        }
        if (current == null || current.fieldSurround.surroundStrength != state.fieldSurround.surroundStrength) {
            engine.setFieldSurroundStrength(state.fieldSurround.surroundStrength)
        }
        if (current == null || current.fieldSurround.midImageStrength != state.fieldSurround.midImageStrength) {
            engine.setFieldSurroundMidImageStrength(state.fieldSurround.midImageStrength)
        }

        // Differential Surround
        if (current == null || current.differentialSurround.enabled != state.differentialSurround.enabled) {
            engine.setDifferentialSurroundEnabled(state.differentialSurround.enabled)
        }
        if (current == null || current.differentialSurround.delay != state.differentialSurround.delay) {
            engine.setDifferentialSurroundDelay(state.differentialSurround.delay)
        }

        // Playback Gain Control
        if (current == null || current.playbackGain.enabled != state.playbackGain.enabled) {
            engine.setPlaybackGainEnabled(state.playbackGain.enabled)
        }
        if (current == null || current.playbackGain.strength != state.playbackGain.strength) {
            engine.setPlaybackGainStrength(state.playbackGain.strength)
        }
        if (current == null || current.playbackGain.maxGain != state.playbackGain.maxGain) {
            engine.setPlaybackGainMaxGain(state.playbackGain.maxGain)
        }
        if (current == null || current.playbackGain.outputThreshold != state.playbackGain.outputThreshold) {
            engine.setPlaybackGainOutputThreshold(state.playbackGain.outputThreshold)
        }

        // Dynamic System
        if (current == null || current.dynamicSystem.enabled != state.dynamicSystem.enabled) {
            engine.setDynamicSystemEnabled(state.dynamicSystem.enabled)
        }
        if (current == null || current.dynamicSystem.deviceType != state.dynamicSystem.deviceType) {
            engine.setDynamicSystemDeviceType(state.dynamicSystem.deviceType.ordinal)
        }
        if (current == null || current.dynamicSystem.dynamicBassStrength != state.dynamicSystem.dynamicBassStrength) {
            engine.setDynamicSystemBassStrength(100 + 20 * state.dynamicSystem.dynamicBassStrength)
        }

        // Tube Simulator
        if (current == null || current.tubeSimulator.enabled != state.tubeSimulator.enabled) {
            engine.setTubeSimulatorEnabled(state.tubeSimulator.enabled)
        }

        // ViPER Bass
        if (current == null || current.viperBass.enabled != state.viperBass.enabled) {
            engine.setViperBassEnabled(state.viperBass.enabled)
        }
        if (current == null || current.viperBass.mode != state.viperBass.mode) {
            engine.setViperBassMode(state.viperBass.mode)
        }
        if (current == null || current.viperBass.frequency != state.viperBass.frequency) {
            engine.setViperBassFrequency(state.viperBass.frequency)
        }
        if (current == null || current.viperBass.gain != state.viperBass.gain) {
            engine.setViperBassGain(state.viperBass.gain + 1)
        }

        // ViPER Clarity
        if (current == null || current.viperClarity.enabled != state.viperClarity.enabled) {
            engine.setViperClarityEnabled(state.viperClarity.enabled)
        }
        if (current == null || current.viperClarity.mode != state.viperClarity.mode) {
            engine.setViperClarityMode(state.viperClarity.mode)
        }
        if (current == null || current.viperClarity.gain != state.viperClarity.gain) {
            engine.setViperClarityGain(state.viperClarity.gain)
        }

        // Auditory System Protection
        if (current == null || current.auditorySystemProtection.enabled != state.auditorySystemProtection.enabled) {
            engine.setAuditorySystemProtectionEnabled(state.auditorySystemProtection.enabled)
        }
        if (current == null || current.auditorySystemProtection.level != state.auditorySystemProtection.level) {
            engine.setAuditorySystemProtectionLevel(state.auditorySystemProtection.level)
        }

        // Analog X
        if (current == null || current.analogX.enabled != state.analogX.enabled) {
            engine.setAnalogXEnabled(state.analogX.enabled)
        }
        if (current == null || current.analogX.level != state.analogX.level) {
            engine.setAnalogXLevel(state.analogX.level)
        }

        // Speaker Optimization
        if (current == null || current.speakerOptimization.enabled != state.speakerOptimization.enabled) {
            engine.setSpeakerOptimizationEnabled(state.speakerOptimization.enabled)
        }

        // ViPER DDC
        if (current == null || current.viperDdc.enabled != state.viperDdc.enabled) {
            engine.setViperDdcEnabled(state.viperDdc.enabled)
        }

        // Convolver
        if (current == null || current.convolver.enabled != state.convolver.enabled) {
            engine.setConvolverEnabled(state.convolver.enabled)
        }
        if (current == null || current.convolver.crossChannel != state.convolver.crossChannel) {
            engine.setConvolverCrossChannel(state.convolver.crossChannel)
        }

        // FET Compressor
        if (current == null || current.fetCompressor.enabled != state.fetCompressor.enabled) {
            engine.setFetCompressorEnabled(state.fetCompressor.enabled)
        }
        if (current == null || current.fetCompressor.threshold != state.fetCompressor.threshold) {
            engine.setFetCompressorThreshold(state.fetCompressor.threshold)
        }
        if (current == null || current.fetCompressor.ratio != state.fetCompressor.ratio) {
            engine.setFetCompressorRatio(state.fetCompressor.ratio)
        }
        if (current == null || current.fetCompressor.knee != state.fetCompressor.knee) {
            engine.setFetCompressorKnee(state.fetCompressor.knee)
        }
        if (current == null || current.fetCompressor.autoKnee != state.fetCompressor.autoKnee) {
            engine.setFetCompressorAutoKnee(state.fetCompressor.autoKnee)
        }
        if (current == null || current.fetCompressor.gain != state.fetCompressor.gain) {
            engine.setFetCompressorGain(state.fetCompressor.gain)
        }
        if (current == null || current.fetCompressor.autoGain != state.fetCompressor.autoGain) {
            engine.setFetCompressorAutoGain(state.fetCompressor.autoGain)
        }
        if (current == null || current.fetCompressor.attack != state.fetCompressor.attack) {
            engine.setFetCompressorAttack(state.fetCompressor.attack)
        }
        if (current == null || current.fetCompressor.autoAttack != state.fetCompressor.autoAttack) {
            engine.setFetCompressorAutoAttack(state.fetCompressor.autoAttack)
        }
        if (current == null || current.fetCompressor.release != state.fetCompressor.release) {
            engine.setFetCompressorRelease(state.fetCompressor.release)
        }
        if (current == null || current.fetCompressor.autoRelease != state.fetCompressor.autoRelease) {
            engine.setFetCompressorAutoRelease(state.fetCompressor.autoRelease)
        }
        if (current == null || current.fetCompressor.kneeMulti != state.fetCompressor.kneeMulti) {
            engine.setFetCompressorKneeMulti(state.fetCompressor.kneeMulti)
        }
        if (current == null || current.fetCompressor.maxAttack != state.fetCompressor.maxAttack) {
            engine.setFetCompressorMaxAttack(state.fetCompressor.maxAttack)
        }
        if (current == null || current.fetCompressor.maxRelease != state.fetCompressor.maxRelease) {
            engine.setFetCompressorMaxRelease(state.fetCompressor.maxRelease)
        }
        if (current == null || current.fetCompressor.crest != state.fetCompressor.crest) {
            engine.setFetCompressorCrest(state.fetCompressor.crest)
        }
        if (current == null || current.fetCompressor.adapt != state.fetCompressor.adapt) {
            engine.setFetCompressorAdapt(state.fetCompressor.adapt)
        }
        if (current == null || current.fetCompressor.noClip != state.fetCompressor.noClip) {
            engine.setFetCompressorNoClip(state.fetCompressor.noClip)
        }

        // Headphone Surround+ (VHE)
        if (current == null || current.headphoneSurround.enabled != state.headphoneSurround.enabled) {
            engine.setHeadphoneSurroundEnabled(state.headphoneSurround.enabled)
        }
        if (current == null || current.headphoneSurround.level != state.headphoneSurround.level) {
            engine.setHeadphoneSurroundLevel(state.headphoneSurround.level)
        }

        // Reverberation
        if (current == null || current.reverberation.enabled != state.reverberation.enabled) {
            engine.setReverberationEnabled(state.reverberation.enabled)
        }
        if (current == null || current.reverberation.roomSize != state.reverberation.roomSize) {
            engine.setReverberationRoomSize(state.reverberation.roomSize)
        }
        if (current == null || current.reverberation.width != state.reverberation.width) {
            engine.setReverberationWidth(state.reverberation.width)
        }
        if (current == null || current.reverberation.damp != state.reverberation.damp) {
            engine.setReverberationDamp(state.reverberation.damp)
        }
        if (current == null || current.reverberation.wet != state.reverberation.wet) {
            engine.setReverberationWet(state.reverberation.wet)
        }
        if (current == null || current.reverberation.dry != state.reverberation.dry) {
            engine.setReverberationDry(state.reverberation.dry)
        }

        // Check for coefficients change (content) rather than just file name
        if (current == null || current.viperDdc.coeffs != state.viperDdc.coeffs) {
            val coeffsMap = state.viperDdc.coeffs

            // Always clear first when content changes
            engine.viperDdcClearCoeffs()

            if (coeffsMap != null && coeffsMap.isNotEmpty()) {
                coeffsMap.forEach { (rate, coeffs) ->
                    engine.viperDdcAddCoeffs(rate, coeffs.toFloatArray())
                }
            }
        }
    }
}
