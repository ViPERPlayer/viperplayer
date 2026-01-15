package com.viperplayer.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.viperplayer.data.local.entity.converter.DynamicSystemDeviceTypeConverter
import com.viperplayer.domain.model.DynamicSystemDeviceType

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
    val enabled: Boolean = false,
    
    // Master Limiter
    val masterLimiterOutputGain: Int = 11,
    val masterLimiterOutputPan: Float = 0f,
    val masterLimiterThresholdLimit: Int = 5,
    
    // Spectrum Extension
    val spectrumExtensionEnabled: Boolean = false,
    val spectrumExtensionStrength: Int = 0,
    
    // Field Surround
    val fieldSurroundEnabled: Boolean = false,
    val fieldSurroundStrength: Int = 0,
    val fieldSurroundMidImageStrength: Int = 0,
    
    // Differential Surround
    val differentialSurroundEnabled: Boolean = false,
    val differentialSurroundDelay: Int = 0,
    
    // Dynamic System
    val dynamicSystemEnabled: Boolean = false,
    val dynamicSystemDeviceType: DynamicSystemDeviceType = DynamicSystemDeviceType.EXTREME_HEADPHONE_V2,
    val dynamicSystemBassStrength: Int = 0,
    
    // Tube Simulator
    val tubeSimulatorEnabled: Boolean = false,
    
    // ViPER Bass
    val viperBassEnabled: Boolean = false,
    val viperBassMode: Int = 0,
    val viperBassFrequency: Int = 60,
    val viperBassGain: Int = 6,
    
    // ViPER Clarity
    val viperClarityEnabled: Boolean = false,
    val viperClarityMode: Int = 0,
    val viperClarityGain: Int = 0,
    
    // Auditory System Protection
    val auditorySystemProtectionEnabled: Boolean = false,
    
    // Analog X
    val analogXEnabled: Boolean = false,
    val analogXLevel: Int = 0,
    
    // Speaker Optimization
    val speakerOptimizationEnabled: Boolean = false,
    val speakerOptimizationDelay: Int = 0,
)

