package com.viperplayer.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.viperplayer.data.local.entity.converter.DynamicSystemDeviceTypeConverter
import com.viperplayer.domain.model.DynamicSystemDeviceType
import com.viperplayer.domain.model.ViperDefaults

/**
 * Room entity for ViPER presets.
 * All effect settings are stored in separate columns (normalized schema, no JSON).
 */
@Entity(
    tableName = "viper_presets"
)
@TypeConverters(DynamicSystemDeviceTypeConverter::class)
data class ViperPresetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    
    // Device association (null = default preset)
    val deviceId: String,

    // Global
    val enabled: Boolean = ViperDefaults.ENABLED,
    
    // Master Limiter
    val masterLimiterOutputGain: Int = ViperDefaults.OUTPUT_GAIN_INDEX,
    val masterLimiterOutputPan: Float = ViperDefaults.OUTPUT_PAN,
    val masterLimiterThresholdLimit: Int = ViperDefaults.THRESHOLD_LIMIT_INDEX,
    
    // Spectrum Extension
    val spectrumExtensionEnabled: Boolean = ViperDefaults.SPECTRUM_EXTENSION_ENABLED,
    val spectrumExtensionStrength: Int = ViperDefaults.SPECTRUM_EXTENSION_STRENGTH,
    
    // Field Surround
    val fieldSurroundEnabled: Boolean = ViperDefaults.FIELD_SURROUND_ENABLED,
    val fieldSurroundStrength: Int = ViperDefaults.FIELD_SURROUND_STRENGTH,
    val fieldSurroundMidImageStrength: Int = ViperDefaults.FIELD_SURROUND_MID_IMAGE_STRENGTH,
    
    // Differential Surround
    val differentialSurroundEnabled: Boolean = ViperDefaults.DIFFERENTIAL_SURROUND_ENABLED,
    val differentialSurroundDelay: Int = ViperDefaults.DIFFERENTIAL_SURROUND_DELAY,
    
    // Dynamic System
    val dynamicSystemEnabled: Boolean = ViperDefaults.DYNAMIC_SYSTEM_ENABLED,
    val dynamicSystemDeviceType: DynamicSystemDeviceType = ViperDefaults.DYNAMIC_SYSTEM_DEVICE_TYPE,
    val dynamicSystemBassStrength: Int = ViperDefaults.DYNAMIC_SYSTEM_BASS_STRENGTH,
    
    // Tube Simulator
    val tubeSimulatorEnabled: Boolean = ViperDefaults.TUBE_SIMULATOR_ENABLED,
    
    // ViPER Bass
    val viperBassEnabled: Boolean = ViperDefaults.VIPER_BASS_ENABLED,
    val viperBassMode: Int = ViperDefaults.VIPER_BASS_MODE,
    val viperBassFrequency: Int = ViperDefaults.VIPER_BASS_FREQUENCY,
    val viperBassGain: Int = ViperDefaults.VIPER_BASS_GAIN,
    
    // ViPER Clarity
    val viperClarityEnabled: Boolean = ViperDefaults.VIPER_CLARITY_ENABLED,
    val viperClarityMode: Int = ViperDefaults.VIPER_CLARITY_MODE,
    val viperClarityGain: Int = ViperDefaults.VIPER_CLARITY_GAIN,
    
    // Auditory System Protection
    val auditorySystemProtectionEnabled: Boolean = ViperDefaults.AUDITORY_SYSTEM_PROTECTION_ENABLED,
    val auditorySystemProtectionLevel: Int = ViperDefaults.AUDITORY_SYSTEM_PROTECTION_LEVEL,
    
    // Analog X
    val analogXEnabled: Boolean = ViperDefaults.ANALOG_X_ENABLED,
    val analogXLevel: Int = ViperDefaults.ANALOG_X_LEVEL,
    
    // Speaker Optimization
    val speakerOptimizationEnabled: Boolean = ViperDefaults.SPEAKER_OPTIMIZATION_ENABLED,

    // ViPER DDC
    val viperDdcEnabled: Boolean = ViperDefaults.DDC_ENABLED,
    val viperDdcSelectedFile: String? = null,
)

