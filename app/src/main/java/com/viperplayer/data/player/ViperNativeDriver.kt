package com.viperplayer.data.player

import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridge to the native ViPER driver.
 * Provides methods to set all effect parameters that are called from JNI.
 */
@Singleton
class ViperNativeDriver @Inject constructor() {

    init {
        System.loadLibrary("viper")
    }

    // Master Limiter
    external fun setMasterLimiterOutputGain(gainL: Float, gainR: Float)
    external fun setMasterLimiterThresholdLimit(threshold: Float)

    // Spectrum Extension
    external fun setSpectrumExtensionEnabled(enabled: Boolean)
    external fun setSpectrumExtensionStrength(strength: Int)

    // Field Surround
    external fun setFieldSurroundEnabled(enabled: Boolean)
    external fun setFieldSurroundStrength(strength: Int)
    external fun setFieldSurroundMidImageStrength(strength: Int)

    // Differential Surround
    external fun setDifferentialSurroundEnabled(enabled: Boolean)
    external fun setDifferentialSurroundDelay(delay: Int)

    // Dynamic System
    external fun setDynamicSystemEnabled(enabled: Boolean)
    external fun setDynamicSystemDeviceType(deviceTypeOrdinal: Int)
    external fun setDynamicSystemBassStrength(strength: Int)

    // Tube Simulator
    external fun setTubeSimulatorEnabled(enabled: Boolean)

    // ViPER Bass
    external fun setViperBassEnabled(enabled: Boolean)
    external fun setViperBassMode(mode: Int)
    external fun setViperBassFrequency(frequency: Int)
    external fun setViperBassGain(gain: Int)

    // ViPER Clarity
    external fun setViperClarityEnabled(enabled: Boolean)
    external fun setViperClarityMode(mode: Int)
    external fun setViperClarityGain(gain: Int)

    // Auditory System Protection (Cure)
    external fun setAuditorySystemProtectionEnabled(enabled: Boolean)
    external fun setAuditorySystemProtectionLevel(level: Int)

    // Analog X
    external fun setAnalogXEnabled(enabled: Boolean)
    external fun setAnalogXLevel(level: Int)

    // Speaker Optimization
    external fun setSpeakerOptimizationEnabled(enabled: Boolean)

    // IIR Equalizer
    external fun setIirEqualizerEnabled(enabled: Boolean)
    external fun setIirEqualizerBandLevel(bandIndex: Int, level: Float)
    external fun setIirEqualizerBandCount(bandCountOrdinal: Int)

    // ViPER DDC
    external fun setViperDdcEnabled(enabled: Boolean)
    external fun viperDdcClearCoeffs()
    external fun viperDdcAddCoeffs(samplingRate: Int, coeffs: FloatArray)

    // Convolver
    external fun setConvolverEnabled(enabled: Boolean)
    external fun setConvolverImpulseResponse(channels: Int, kernel: FloatArray)
    external fun setConvolverCrossChannel(crossChannel: Int) // 0-100

    // Playback Gain Control
    external fun setPlaybackGainEnabled(enabled: Boolean)
    external fun setPlaybackGainStrength(strength: Int)
    external fun setPlaybackGainMaxGain(maxGain: Int)
    external fun setPlaybackGainOutputThreshold(threshold: Float)

    // Audio Processing
    external fun process(buffer: ByteBuffer, offset: Int, size: Int)

    external fun reset()
}

