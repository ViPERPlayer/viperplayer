package com.viperplayer.domain.model

import kotlinx.serialization.Serializable

/**
 * Unified state model for all ViPER audio processing effects.
 * This represents the complete configuration of all effects.
 */
@Serializable
data class ViperEffectsState(
    // Global
    val enabled: Boolean = false,

    // Master Limiter
    val masterLimiter: MasterLimiterState = MasterLimiterState(),
    
    // Spectrum Extension
    val spectrumExtension: SpectrumExtensionState = SpectrumExtensionState(),
    
    // Field Surround
    val fieldSurround: FieldSurroundState = FieldSurroundState(),
    
    // Differential Surround
    val differentialSurround: DifferentialSurroundState = DifferentialSurroundState(),
    
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
    val outputGain: Int = ViperSteppedValues.DEFAULT_OUTPUT_GAIN_INDEX,
    val outputPan: Float = 0f, // -1.0 to 1.0, where 0 = center
    val thresholdLimit: Int = ViperSteppedValues.DEFAULT_THRESHOLD_LIMIT_INDEX,
)

@Serializable
data class SpectrumExtensionState(
    val enabled: Boolean = false,
    val strength: Int = 0, // 0-100
)

@Serializable
data class FieldSurroundState(
    val enabled: Boolean = false,
    val surroundStrength: Int = 0, // 0-100
    val midImageStrength: Int = 0, // 0-100
)

@Serializable
data class DifferentialSurroundState(
    val enabled: Boolean = false,
    val delay: Int = 0, // 0-30 (ms)
)

@Serializable
data class DynamicSystemState(
    val enabled: Boolean = false,
    val deviceType: DynamicSystemDeviceType = DynamicSystemDeviceType.EXTREME_HEADPHONE_V2,
    val dynamicBassStrength: Int = 0, // 0-100
)

@Serializable
data class TubeSimulatorState(
    val enabled: Boolean = false,
)

@Serializable
data class ViperBassState(
    val enabled: Boolean = false,
    val mode: Int = 0, // 0=Natural, 1=Pure Bass+, 2=Subwoofer
    val frequency: Int = 60, // 15-150 Hz
    val gain: Int = 6, // 1-12 (index in gainSummaryValues)
)

@Serializable
data class ViperClarityState(
    val enabled: Boolean = false,
    val mode: Int = 0, // Mode index
    val gain: Int = 0, // 0-100
)

@Serializable
data class AuditorySystemProtectionState(
    val enabled: Boolean = false,
)

@Serializable
data class AnalogXState(
    val enabled: Boolean = false,
    val level: Int = 0, // 0-100
)

@Serializable
data class SpeakerOptimizationState(
    val enabled: Boolean = false,
    val delay: Int = 0, // 0-30 (ms)
)

