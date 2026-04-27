package com.viperplayer.domain.model

import kotlinx.serialization.Serializable

/**
 * Unified state model for all ViPER audio processing effects.
 * This represents the complete configuration of all effects.
 */
@Serializable
data class ViperEffectsState(
    // Global
    val enabled: Boolean = ViperDefaults.ENABLED,

    // Master Limiter
    val masterLimiter: MasterLimiterState = MasterLimiterState(),

    // Spectrum Extension
    val spectrumExtension: SpectrumExtensionState = SpectrumExtensionState(),

    // Field Surround
    val fieldSurround: FieldSurroundState = FieldSurroundState(),

    // Differential Surround
    val differentialSurround: DifferentialSurroundState = DifferentialSurroundState(),

    // Playback Gain Control
    val playbackGain: PlaybackGainState = PlaybackGainState(),

    // Dynamic System
    val dynamicSystem: DynamicSystemState = DynamicSystemState(),

    // Tube Simulator 6N1J
    val tubeSimulator: TubeSimulatorState = TubeSimulatorState(),

    // ViPER Bass
    val viperBass: ViperBassState = ViperBassState(),

    // ViPER Clarity
    val viperClarity: ViperClarityState = ViperClarityState(),

    // Auditory System Protection
    val auditorySystemProtection: AuditorySystemProtectionState = AuditorySystemProtectionState(),

    // Analog X
    val analogX: AnalogXState = AnalogXState(),

    // Speaker Optimization
    val speakerOptimization: SpeakerOptimizationState = SpeakerOptimizationState(),

    // ViPER DDC
    val viperDdc: ViperDdcState = ViperDdcState(),

    // Convolver
    val convolver: ViperConvolverState = ViperConvolverState(),

    // IIR Equalizer
    val iirEqualizer: IirEqualizerState = IirEqualizerState(),
) {
    companion object {
        /**
         * Returns default state with all effects at their default values.
         */
        fun default(): ViperEffectsState = ViperEffectsState()
    }
}

@Serializable
data class MasterLimiterState(
    val outputGain: Int = ViperDefaults.OUTPUT_GAIN_INDEX,
    val outputPan: Float = ViperDefaults.OUTPUT_PAN,
    val thresholdLimit: Int = ViperDefaults.THRESHOLD_LIMIT_INDEX,
)

@Serializable
data class SpectrumExtensionState(
    val enabled: Boolean = ViperDefaults.SPECTRUM_EXTENSION_ENABLED,
    val strength: Int = ViperDefaults.SPECTRUM_EXTENSION_STRENGTH,
)

@Serializable
data class FieldSurroundState(
    val enabled: Boolean = ViperDefaults.FIELD_SURROUND_ENABLED,
    val surroundStrength: Int = ViperDefaults.FIELD_SURROUND_STRENGTH,
    val midImageStrength: Int = ViperDefaults.FIELD_SURROUND_MID_IMAGE_STRENGTH,
)

@Serializable
data class DifferentialSurroundState(
    val enabled: Boolean = ViperDefaults.DIFFERENTIAL_SURROUND_ENABLED,
    val delay: Int = ViperDefaults.DIFFERENTIAL_SURROUND_DELAY,
)

@Serializable
data class PlaybackGainState(
    val enabled: Boolean = ViperDefaults.PLAYBACK_GAIN_ENABLED,
    val strength: Int = ViperDefaults.PLAYBACK_GAIN_STRENGTH,
    val maxGain: Int = ViperDefaults.PLAYBACK_GAIN_MAX_GAIN,
    val outputThreshold: Float = ViperDefaults.PLAYBACK_GAIN_OUTPUT_THRESHOLD,
)

@Serializable
data class DynamicSystemState(
    val enabled: Boolean = ViperDefaults.DYNAMIC_SYSTEM_ENABLED,
    val deviceType: DynamicSystemDeviceType = ViperDefaults.DYNAMIC_SYSTEM_DEVICE_TYPE,
    val dynamicBassStrength: Int = ViperDefaults.DYNAMIC_SYSTEM_BASS_STRENGTH,
)

@Serializable
data class TubeSimulatorState(
    val enabled: Boolean = ViperDefaults.TUBE_SIMULATOR_ENABLED,
)

@Serializable
data class ViperBassState(
    val enabled: Boolean = ViperDefaults.VIPER_BASS_ENABLED,
    val mode: Int = ViperDefaults.VIPER_BASS_MODE,
    val frequency: Int = ViperDefaults.VIPER_BASS_FREQUENCY,
    val gain: Int = ViperDefaults.VIPER_BASS_GAIN,
)

@Serializable
data class ViperClarityState(
    val enabled: Boolean = ViperDefaults.VIPER_CLARITY_ENABLED,
    val mode: Int = ViperDefaults.VIPER_CLARITY_MODE,
    val gain: Int = ViperDefaults.VIPER_CLARITY_GAIN,
)

@Serializable
data class AuditorySystemProtectionState(
    val enabled: Boolean = ViperDefaults.AUDITORY_SYSTEM_PROTECTION_ENABLED,
    val level: Int = ViperDefaults.AUDITORY_SYSTEM_PROTECTION_LEVEL,
)

@Serializable
data class AnalogXState(
    val enabled: Boolean = ViperDefaults.ANALOG_X_ENABLED,
    val level: Int = ViperDefaults.ANALOG_X_LEVEL,
)

@Serializable
data class SpeakerOptimizationState(
    val enabled: Boolean = ViperDefaults.SPEAKER_OPTIMIZATION_ENABLED,
)

@Serializable
data class IirEqualizerState(
    val enabled: Boolean = ViperDefaults.IIR_EQUALIZER_ENABLED,
    val bandCount: Int = ViperDefaults.IIR_EQUALIZER_BAND_COUNT,
    val preset: String = ViperDefaults.IIR_EQUALIZER_PRESET,
    val bandGains: List<Float> = ViperDefaults.IIR_EQUALIZER_BAND_GAINS,
)

