package com.viperplayer.data.player

import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridge to the native ViPER driver.
 * Provides methods to set all effect parameters that are called from JNI.
 */
@Singleton
class ViperNativeDriver @Inject constructor() : ViperEngine {

    init {
        System.loadLibrary("viper")
    }

    /** Reconfigure the engine for the stream's sample rate (its DSP coefficients are rate-dependent). */
    override external fun setSamplingRate(samplingRate: Int)

    // Master Limiter
    override external fun setMasterLimiterOutputGain(gainL: Float, gainR: Float)
    override external fun setMasterLimiterThresholdLimit(threshold: Float)

    // Spectrum Extension
    override external fun setSpectrumExtensionEnabled(enabled: Boolean)
    override external fun setSpectrumExtensionStrength(strength: Int)

    // Field Surround
    override external fun setFieldSurroundEnabled(enabled: Boolean)
    override external fun setFieldSurroundStrength(strength: Int)
    override external fun setFieldSurroundMidImageStrength(strength: Int)

    // Differential Surround
    override external fun setDifferentialSurroundEnabled(enabled: Boolean)
    override external fun setDifferentialSurroundDelay(delay: Int)

    // Dynamic System
    override external fun setDynamicSystemEnabled(enabled: Boolean)
    override external fun setDynamicSystemDeviceType(deviceTypeOrdinal: Int)
    override external fun setDynamicSystemBassStrength(strength: Int)

    // Tube Simulator
    override external fun setTubeSimulatorEnabled(enabled: Boolean)

    // ViPER Bass
    override external fun setViperBassEnabled(enabled: Boolean)
    override external fun setViperBassMode(mode: Int)
    override external fun setViperBassFrequency(frequency: Int)
    override external fun setViperBassGain(gain: Int)

    // ViPER Clarity
    override external fun setViperClarityEnabled(enabled: Boolean)
    override external fun setViperClarityMode(mode: Int)
    override external fun setViperClarityGain(gain: Int)

    // Auditory System Protection (Cure)
    override external fun setAuditorySystemProtectionEnabled(enabled: Boolean)
    override external fun setAuditorySystemProtectionLevel(level: Int)

    // Analog X
    override external fun setAnalogXEnabled(enabled: Boolean)
    override external fun setAnalogXLevel(level: Int)

    // Speaker Optimization
    override external fun setSpeakerOptimizationEnabled(enabled: Boolean)

    // IIR Equalizer
    override external fun setIirEqualizerEnabled(enabled: Boolean)
    override external fun setIirEqualizerBandLevel(bandIndex: Int, level: Float)
    override external fun setIirEqualizerBandCount(bandCountOrdinal: Int)

    /**
     * Apply the band count and every band gain in a SINGLE native call.
     *
     * Prefer this over `setIirEqualizerBandCount` + N x `setIirEqualizerBandLevel`: those are
     * separate JNI calls made from a Default-dispatcher coroutine, so the audio thread can render
     * buffers part-way through the sequence — with the new band count but the old gains, or with
     * only some bands applied. The engine applies this one atomically with respect to `process`.
     *
     * [gains] is indexed by band; bands beyond its length are set to unity (0 dB).
     */
    override external fun setIirEqualizerBands(bandCountOrdinal: Int, gains: FloatArray)

    // ViPER DDC
    override external fun setViperDdcEnabled(enabled: Boolean)
    override external fun viperDdcClearCoeffs()
    override external fun viperDdcAddCoeffs(samplingRate: Int, coeffs: FloatArray)

    // FET Compressor
    override external fun setFetCompressorEnabled(enabled: Boolean)
    override external fun setFetCompressorThreshold(value: Int)   // 0..100
    override external fun setFetCompressorRatio(value: Int)
    override external fun setFetCompressorKnee(value: Int)
    override external fun setFetCompressorAutoKnee(enabled: Boolean)
    override external fun setFetCompressorGain(value: Int)
    override external fun setFetCompressorAutoGain(enabled: Boolean)
    override external fun setFetCompressorAttack(value: Int)
    override external fun setFetCompressorAutoAttack(enabled: Boolean)
    override external fun setFetCompressorRelease(value: Int)
    override external fun setFetCompressorAutoRelease(enabled: Boolean)
    override external fun setFetCompressorKneeMulti(value: Int)
    override external fun setFetCompressorMaxAttack(value: Int)
    override external fun setFetCompressorMaxRelease(value: Int)
    override external fun setFetCompressorCrest(value: Int)
    override external fun setFetCompressorAdapt(value: Int)
    override external fun setFetCompressorNoClip(enabled: Boolean)

    // Headphone Surround+ (VHE)
    override external fun setHeadphoneSurroundEnabled(enabled: Boolean)
    override external fun setHeadphoneSurroundLevel(level: Int) // 0..4 (quality)

    // Reverberation (values 0..100, sent to native as value/100 -> 0..1)
    override external fun setReverberationEnabled(enabled: Boolean)
    override external fun setReverberationRoomSize(value: Int)
    override external fun setReverberationWidth(value: Int)
    override external fun setReverberationDamp(value: Int)
    override external fun setReverberationWet(value: Int)
    override external fun setReverberationDry(value: Int)

    // Convolver
    override external fun setConvolverEnabled(enabled: Boolean)
    override external fun setConvolverImpulseResponse(channels: Int, kernel: FloatArray)
    override external fun setConvolverCrossChannel(crossChannel: Int) // 0-100

    // Playback Gain Control
    override external fun setPlaybackGainEnabled(enabled: Boolean)
    override external fun setPlaybackGainStrength(strength: Int)
    override external fun setPlaybackGainMaxGain(maxGain: Int)
    override external fun setPlaybackGainOutputThreshold(threshold: Float)

    // Audio Processing
    override external fun process(buffer: ByteBuffer, offset: Int, size: Int)

    override external fun reset()
}

