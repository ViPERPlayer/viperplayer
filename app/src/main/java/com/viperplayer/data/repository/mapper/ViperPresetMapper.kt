package com.viperplayer.data.repository.mapper

import com.viperplayer.data.local.entity.ViperPresetEntity
import com.viperplayer.domain.model.AnalogXState
import com.viperplayer.domain.model.AuditorySystemProtectionState
import com.viperplayer.domain.model.DifferentialSurroundState
import com.viperplayer.domain.model.DynamicSystemState
import com.viperplayer.domain.model.FieldSurroundState
import com.viperplayer.domain.model.MasterLimiterState
import com.viperplayer.domain.model.SpeakerOptimizationState
import com.viperplayer.domain.model.SpectrumExtensionState
import com.viperplayer.domain.model.TubeSimulatorState
import com.viperplayer.domain.model.ViperBassState
import com.viperplayer.domain.model.ViperClarityState
import com.viperplayer.domain.model.ViperEffectsState
import com.viperplayer.domain.model.ViperPreset

/**
 * Mapper functions to convert between Room entities and domain models.
 */
object ViperPresetMapper {
    fun ViperPresetEntity.toDomain(): ViperPreset {
        return ViperPreset(
            id = id,
            name = name,
            deviceId = deviceId,
            effectsState = ViperEffectsState(
                enabled = enabled,
                masterLimiter = MasterLimiterState(
                    outputGain = masterLimiterOutputGain,
                    outputPan = masterLimiterOutputPan,
                    thresholdLimit = masterLimiterThresholdLimit,
                ),
                spectrumExtension = SpectrumExtensionState(
                    enabled = spectrumExtensionEnabled,
                    strength = spectrumExtensionStrength,
                ),
                fieldSurround = FieldSurroundState(
                    enabled = fieldSurroundEnabled,
                    surroundStrength = fieldSurroundStrength,
                    midImageStrength = fieldSurroundMidImageStrength,
                ),
                differentialSurround = DifferentialSurroundState(
                    enabled = differentialSurroundEnabled,
                    delay = differentialSurroundDelay,
                ),
                dynamicSystem = DynamicSystemState(
                    enabled = dynamicSystemEnabled,
                    deviceType = dynamicSystemDeviceType,
                    dynamicBassStrength = dynamicSystemBassStrength,
                ),
                tubeSimulator = TubeSimulatorState(
                    enabled = tubeSimulatorEnabled,
                ),
                viperBass = ViperBassState(
                    enabled = viperBassEnabled,
                    mode = viperBassMode,
                    frequency = viperBassFrequency,
                    gain = viperBassGain,
                ),
                viperClarity = ViperClarityState(
                    enabled = viperClarityEnabled,
                    mode = viperClarityMode,
                    gain = viperClarityGain,
                ),
                auditorySystemProtection = AuditorySystemProtectionState(
                    enabled = auditorySystemProtectionEnabled,
                    level = auditorySystemProtectionLevel,
                ),
                analogX = AnalogXState(
                    enabled = analogXEnabled,
                    level = analogXLevel,
                ),
                speakerOptimization = SpeakerOptimizationState(
                    enabled = speakerOptimizationEnabled,
                ),
            ),
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    fun ViperPreset.toEntity(): ViperPresetEntity {
        return ViperPresetEntity(
            id = id,
            name = name,
            deviceId = deviceId,
            createdAt = createdAt,
            updatedAt = updatedAt,
            // Global
            enabled = effectsState.enabled,
            // Master Limiter
            masterLimiterOutputGain = effectsState.masterLimiter.outputGain,
            masterLimiterOutputPan = effectsState.masterLimiter.outputPan,
            masterLimiterThresholdLimit = effectsState.masterLimiter.thresholdLimit,
            // Spectrum Extension
            spectrumExtensionEnabled = effectsState.spectrumExtension.enabled,
            spectrumExtensionStrength = effectsState.spectrumExtension.strength,
            // Field Surround
            fieldSurroundEnabled = effectsState.fieldSurround.enabled,
            fieldSurroundStrength = effectsState.fieldSurround.surroundStrength,
            fieldSurroundMidImageStrength = effectsState.fieldSurround.midImageStrength,
            // Differential Surround
            differentialSurroundEnabled = effectsState.differentialSurround.enabled,
            differentialSurroundDelay = effectsState.differentialSurround.delay,
            // Dynamic System
            dynamicSystemEnabled = effectsState.dynamicSystem.enabled,
            dynamicSystemDeviceType = effectsState.dynamicSystem.deviceType,
            dynamicSystemBassStrength = effectsState.dynamicSystem.dynamicBassStrength,
            // Tube Simulator
            tubeSimulatorEnabled = effectsState.tubeSimulator.enabled,
            // ViPER Bass
            viperBassEnabled = effectsState.viperBass.enabled,
            viperBassMode = effectsState.viperBass.mode,
            viperBassFrequency = effectsState.viperBass.frequency,
            viperBassGain = effectsState.viperBass.gain,
            // ViPER Clarity
            viperClarityEnabled = effectsState.viperClarity.enabled,
            viperClarityMode = effectsState.viperClarity.mode,
            viperClarityGain = effectsState.viperClarity.gain,
            // Auditory System Protection
            auditorySystemProtectionEnabled = effectsState.auditorySystemProtection.enabled,
            auditorySystemProtectionLevel = effectsState.auditorySystemProtection.level,
            // Analog X
            analogXEnabled = effectsState.analogX.enabled,
            analogXLevel = effectsState.analogX.level,
            // Speaker Optimization
            speakerOptimizationEnabled = effectsState.speakerOptimization.enabled,
        )
    }
}

