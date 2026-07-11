package com.viperplayer.data.local.entity

import androidx.room.ColumnInfo
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

    // IIR Equalizer. Keep the @ColumnInfo defaultValue in sync with the field default below.
    @ColumnInfo(defaultValue = "0")
    val iirEqualizerEnabled: Boolean = ViperDefaults.IIR_EQUALIZER_ENABLED,
    @ColumnInfo(defaultValue = "10")
    val iirEqualizerBandCount: Int = ViperDefaults.IIR_EQUALIZER_BAND_COUNT,
    @ColumnInfo(defaultValue = "'Flat'")
    val iirEqualizerPreset: String = ViperDefaults.IIR_EQUALIZER_PRESET,
    // Band gains as comma-separated floats (variable length; empty -> a flat curve of bandCount).
    @ColumnInfo(defaultValue = "''")
    val iirEqualizerBandGains: String = "",

    // Playback Gain (schema v2)
    @ColumnInfo(defaultValue = "0")
    val playbackGainEnabled: Boolean = ViperDefaults.PLAYBACK_GAIN_ENABLED,
    @ColumnInfo(defaultValue = "1")
    val playbackGainStrength: Int = ViperDefaults.PLAYBACK_GAIN_STRENGTH,
    @ColumnInfo(defaultValue = "1")
    val playbackGainMaxGain: Int = ViperDefaults.PLAYBACK_GAIN_MAX_GAIN,
    @ColumnInfo(defaultValue = "0")
    val playbackGainOutputThreshold: Float = ViperDefaults.PLAYBACK_GAIN_OUTPUT_THRESHOLD,

    // Convolver (schema v2)
    @ColumnInfo(defaultValue = "0")
    val convolverEnabled: Boolean = ViperDefaults.CONVOLVER_ENABLED,
    val convolverImpulseResponse: String? = ViperDefaults.CONVOLVER_IMPULSE_RESPONSE,
    @ColumnInfo(defaultValue = "0")
    val convolverCrossChannel: Int = ViperDefaults.CONVOLVER_CROSS_CHANNEL,

    // FET Compressor. Keep the @ColumnInfo defaults in sync with the FetCompressorState() defaults.
    @ColumnInfo(defaultValue = "0")
    val fetCompressorEnabled: Boolean = false,
    @ColumnInfo(defaultValue = "100")
    val fetCompressorThreshold: Int = 100,
    @ColumnInfo(defaultValue = "100")
    val fetCompressorRatio: Int = 100,
    @ColumnInfo(defaultValue = "0")
    val fetCompressorKnee: Int = 0,
    @ColumnInfo(defaultValue = "1")
    val fetCompressorAutoKnee: Boolean = true,
    @ColumnInfo(defaultValue = "0")
    val fetCompressorGain: Int = 0,
    @ColumnInfo(defaultValue = "1")
    val fetCompressorAutoGain: Boolean = true,
    @ColumnInfo(defaultValue = "20")
    val fetCompressorAttack: Int = 20,
    @ColumnInfo(defaultValue = "1")
    val fetCompressorAutoAttack: Boolean = true,
    @ColumnInfo(defaultValue = "50")
    val fetCompressorRelease: Int = 50,
    @ColumnInfo(defaultValue = "1")
    val fetCompressorAutoRelease: Boolean = true,
    @ColumnInfo(defaultValue = "0")
    val fetCompressorKneeMulti: Int = 0,
    @ColumnInfo(defaultValue = "80")
    val fetCompressorMaxAttack: Int = 80,
    @ColumnInfo(defaultValue = "100")
    val fetCompressorMaxRelease: Int = 100,
    @ColumnInfo(defaultValue = "20")
    val fetCompressorCrest: Int = 20,
    @ColumnInfo(defaultValue = "50")
    val fetCompressorAdapt: Int = 50,
    @ColumnInfo(defaultValue = "1")
    val fetCompressorNoClip: Boolean = true,

    // Headphone Surround+ / VHE (schema v5)
    @ColumnInfo(defaultValue = "0")
    val headphoneSurroundEnabled: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val headphoneSurroundLevel: Int = 0,

    // Reverberation (schema v5)
    @ColumnInfo(defaultValue = "0")
    val reverberationEnabled: Boolean = ViperDefaults.REVERBERATION_ENABLED,
    @ColumnInfo(defaultValue = "50")
    val reverberationRoomSize: Int = ViperDefaults.REVERBERATION_ROOM_SIZE,
    @ColumnInfo(defaultValue = "100")
    val reverberationWidth: Int = ViperDefaults.REVERBERATION_WIDTH,
    @ColumnInfo(defaultValue = "50")
    val reverberationDamp: Int = ViperDefaults.REVERBERATION_DAMP,
    @ColumnInfo(defaultValue = "30")
    val reverberationWet: Int = ViperDefaults.REVERBERATION_WET,
    @ColumnInfo(defaultValue = "50")
    val reverberationDry: Int = ViperDefaults.REVERBERATION_DRY,
)

