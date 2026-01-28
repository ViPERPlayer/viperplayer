package com.viperplayer.domain.model

/**
 * Centralized default values for all ViPER effects.
 * All default values should be defined here to ensure consistency across the codebase.
 */
object ViperDefaults {
    // Global
    const val ENABLED = false
    
    // Master Limiter
    val OUTPUT_GAIN_INDEX = ViperSteppedValues.DEFAULT_OUTPUT_GAIN_INDEX
    const val OUTPUT_PAN = 0f // -1.0 to 1.0, where 0 = center
    val THRESHOLD_LIMIT_INDEX = ViperSteppedValues.DEFAULT_THRESHOLD_LIMIT_INDEX
    
    // Spectrum Extension
    const val SPECTRUM_EXTENSION_ENABLED = false
    const val SPECTRUM_EXTENSION_STRENGTH = 10 // 0-100
    
    // Field Surround
    const val FIELD_SURROUND_ENABLED = false
    const val FIELD_SURROUND_STRENGTH = 0 // 0-100
    const val FIELD_SURROUND_MID_IMAGE_STRENGTH = 5 // 0-100
    
    // Differential Surround
    const val DIFFERENTIAL_SURROUND_ENABLED = false
    const val DIFFERENTIAL_SURROUND_DELAY = 5 // 0-30 (ms)
    
    // Dynamic System
    const val DYNAMIC_SYSTEM_ENABLED = false
    val DYNAMIC_SYSTEM_DEVICE_TYPE = DynamicSystemDeviceType.EXTREME_HEADPHONE_V2
    const val DYNAMIC_SYSTEM_BASS_STRENGTH = 0 // 0-100
    
    // Tube Simulator
    const val TUBE_SIMULATOR_ENABLED = false
    
    // ViPER Bass
    const val VIPER_BASS_ENABLED = false
    const val VIPER_BASS_MODE = 0 // 0=Natural, 1=Pure Bass+, 2=Subwoofer
    const val VIPER_BASS_FREQUENCY = 70 // 15-150 Hz
    const val VIPER_BASS_GAIN = 0 // Index in gainSummaryValues
    
    // ViPER Clarity
    const val VIPER_CLARITY_ENABLED = false
    const val VIPER_CLARITY_MODE = 0 // Mode index (0-2)
    const val VIPER_CLARITY_GAIN = 1 // Gain index (0-9)
    
    // Auditory System Protection
    const val AUDITORY_SYSTEM_PROTECTION_ENABLED = false
    const val AUDITORY_SYSTEM_PROTECTION_LEVEL = 1 // 1-3
    
    // Analog X
    const val ANALOG_X_ENABLED = false
    const val ANALOG_X_LEVEL = 1 // 1-3
    
    // Speaker Optimization
    const val SPEAKER_OPTIMIZATION_ENABLED = false

    // DDC
    const val DDC_ENABLED = false

    // IIR Equalizer
    const val IIR_EQUALIZER_ENABLED = false
    const val IIR_EQUALIZER_BAND_COUNT = 10
    const val IIR_EQUALIZER_PRESET = "Flat"
    val IIR_EQUALIZER_BAND_GAINS = List(IIR_EQUALIZER_BAND_COUNT) { 0f }
}

