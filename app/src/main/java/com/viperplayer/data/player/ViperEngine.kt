package com.viperplayer.data.player

import java.nio.ByteBuffer

/**
 * The native ViPER DSP engine, as the rest of the app sees it.
 *
 * Exists so [ViperEffectsStateApplier] can be unit-tested: the concrete [ViperNativeDriver] loads
 * `libviper.so` in its initialiser and every method is an `external` JNI binding, so it cannot be
 * constructed or stubbed off-device. The parameter-diffing logic that decides WHICH of these calls
 * to make is ordinary decision logic, though, and worth testing on its own.
 */
interface ViperEngine {

    /** Reconfigure the engine for the stream's sample rate (its DSP coefficients are rate-dependent). */
    fun setSamplingRate(samplingRate: Int)

    // Master Limiter
    fun setMasterLimiterOutputGain(gainL: Float, gainR: Float)
    fun setMasterLimiterThresholdLimit(threshold: Float)

    // Spectrum Extension
    fun setSpectrumExtensionEnabled(enabled: Boolean)
    fun setSpectrumExtensionStrength(strength: Int)

    // Field Surround
    fun setFieldSurroundEnabled(enabled: Boolean)
    fun setFieldSurroundStrength(strength: Int)
    fun setFieldSurroundMidImageStrength(strength: Int)

    // Differential Surround
    fun setDifferentialSurroundEnabled(enabled: Boolean)
    fun setDifferentialSurroundDelay(delay: Int)

    // Dynamic System
    fun setDynamicSystemEnabled(enabled: Boolean)
    fun setDynamicSystemDeviceType(deviceTypeOrdinal: Int)
    fun setDynamicSystemBassStrength(strength: Int)

    // Tube Simulator
    fun setTubeSimulatorEnabled(enabled: Boolean)

    // ViPER Bass
    fun setViperBassEnabled(enabled: Boolean)
    fun setViperBassMode(mode: Int)
    fun setViperBassFrequency(frequency: Int)
    fun setViperBassGain(gain: Int)

    // ViPER Clarity
    fun setViperClarityEnabled(enabled: Boolean)
    fun setViperClarityMode(mode: Int)
    fun setViperClarityGain(gain: Int)

    // Auditory System Protection (Cure)
    fun setAuditorySystemProtectionEnabled(enabled: Boolean)
    fun setAuditorySystemProtectionLevel(level: Int)

    // Analog X
    fun setAnalogXEnabled(enabled: Boolean)
    fun setAnalogXLevel(level: Int)

    // Speaker Optimization
    fun setSpeakerOptimizationEnabled(enabled: Boolean)

    // IIR Equalizer
    fun setIirEqualizerEnabled(enabled: Boolean)
    fun setIirEqualizerBandLevel(bandIndex: Int, level: Float)
    fun setIirEqualizerBandCount(bandCountOrdinal: Int)

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
    fun setIirEqualizerBands(bandCountOrdinal: Int, gains: FloatArray)

    // ViPER DDC
    fun setViperDdcEnabled(enabled: Boolean)
    fun viperDdcClearCoeffs()
    fun viperDdcAddCoeffs(samplingRate: Int, coeffs: FloatArray)

    // FET Compressor
    fun setFetCompressorEnabled(enabled: Boolean)
    fun setFetCompressorThreshold(value: Int)   // 0..100
    fun setFetCompressorRatio(value: Int)
    fun setFetCompressorKnee(value: Int)
    fun setFetCompressorAutoKnee(enabled: Boolean)
    fun setFetCompressorGain(value: Int)
    fun setFetCompressorAutoGain(enabled: Boolean)
    fun setFetCompressorAttack(value: Int)
    fun setFetCompressorAutoAttack(enabled: Boolean)
    fun setFetCompressorRelease(value: Int)
    fun setFetCompressorAutoRelease(enabled: Boolean)
    fun setFetCompressorKneeMulti(value: Int)
    fun setFetCompressorMaxAttack(value: Int)
    fun setFetCompressorMaxRelease(value: Int)
    fun setFetCompressorCrest(value: Int)
    fun setFetCompressorAdapt(value: Int)
    fun setFetCompressorNoClip(enabled: Boolean)

    // Headphone Surround+ (VHE)
    fun setHeadphoneSurroundEnabled(enabled: Boolean)
    fun setHeadphoneSurroundLevel(level: Int) // 0..4 (quality)

    // Reverberation (values 0..100, sent to native as value/100 -> 0..1)
    fun setReverberationEnabled(enabled: Boolean)
    fun setReverberationRoomSize(value: Int)
    fun setReverberationWidth(value: Int)
    fun setReverberationDamp(value: Int)
    fun setReverberationWet(value: Int)
    fun setReverberationDry(value: Int)

    // Convolver
    fun setConvolverEnabled(enabled: Boolean)
    fun setConvolverImpulseResponse(channels: Int, kernel: FloatArray)
    fun setConvolverCrossChannel(crossChannel: Int) // 0-100

    // Playback Gain Control
    fun setPlaybackGainEnabled(enabled: Boolean)
    fun setPlaybackGainStrength(strength: Int)
    fun setPlaybackGainMaxGain(maxGain: Int)
    fun setPlaybackGainOutputThreshold(threshold: Float)

    // Audio Processing
    fun process(buffer: ByteBuffer, offset: Int, size: Int)

    fun reset()
}
