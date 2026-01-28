package com.viperplayer.data.repository.mapper

import com.viperplayer.data.local.entity.ViperDdcCoeffEntity
import com.viperplayer.data.local.entity.ViperPresetEntity
import com.viperplayer.data.local.entity.relation.ViperPresetWithCoeffs
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
    fun ViperPresetWithCoeffs.toDomain(): ViperPreset {
        val preset = this.preset
        val coeffsMap = this.ddcCoeffs
            .groupBy { it.sampleRate }
            .mapValues { (_, entries) ->
                entries.sortedBy { it.index }.map { it.value }
            }
        
        return ViperPreset(
            id = preset.id,
            name = preset.name,
            deviceId = preset.deviceId,
            effectsState = ViperEffectsState(
                enabled = preset.enabled,
                masterLimiter = MasterLimiterState(
                    outputGain = preset.masterLimiterOutputGain,
                    outputPan = preset.masterLimiterOutputPan,
                    thresholdLimit = preset.masterLimiterThresholdLimit,
                ),
                spectrumExtension = SpectrumExtensionState(
                    enabled = preset.spectrumExtensionEnabled,
                    strength = preset.spectrumExtensionStrength,
                ),
                fieldSurround = FieldSurroundState(
                    enabled = preset.fieldSurroundEnabled,
                    surroundStrength = preset.fieldSurroundStrength,
                    midImageStrength = preset.fieldSurroundMidImageStrength,
                ),
                differentialSurround = DifferentialSurroundState(
                    enabled = preset.differentialSurroundEnabled,
                    delay = preset.differentialSurroundDelay,
                ),
                dynamicSystem = DynamicSystemState(
                    enabled = preset.dynamicSystemEnabled,
                    deviceType = preset.dynamicSystemDeviceType,
                    dynamicBassStrength = preset.dynamicSystemBassStrength,
                ),
                tubeSimulator = TubeSimulatorState(
                    enabled = preset.tubeSimulatorEnabled,
                ),
                viperBass = ViperBassState(
                    enabled = preset.viperBassEnabled,
                    mode = preset.viperBassMode,
                    frequency = preset.viperBassFrequency,
                    gain = preset.viperBassGain,
                ),
                viperClarity = ViperClarityState(
                    enabled = preset.viperClarityEnabled,
                    mode = preset.viperClarityMode,
                    gain = preset.viperClarityGain,
                ),
                auditorySystemProtection = AuditorySystemProtectionState(
                    enabled = preset.auditorySystemProtectionEnabled,
                    level = preset.auditorySystemProtectionLevel,
                ),
                analogX = AnalogXState(
                    enabled = preset.analogXEnabled,
                    level = preset.analogXLevel,
                ),
                speakerOptimization = SpeakerOptimizationState(
                    enabled = preset.speakerOptimizationEnabled,
                ),
                viperDdc = com.viperplayer.domain.model.ViperDdcState(
                    enabled = preset.viperDdcEnabled,
                    selectedDdcFile = preset.viperDdcSelectedFile,
                    coeffs = coeffsMap.ifEmpty { null }
                ),
            ),
            createdAt = preset.createdAt,
            updatedAt = preset.updatedAt,
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
            // ViPER DDC
            viperDdcEnabled = effectsState.viperDdc.enabled,
            viperDdcSelectedFile = effectsState.viperDdc.selectedDdcFile,
            // Coeffs handled separately
        )
    }

    fun ViperPreset.toCoeffEntities(presetId: Long): List<ViperDdcCoeffEntity> {
        val coeffs = effectsState.viperDdc.coeffs ?: return emptyList()
        val entities = mutableListOf<ViperDdcCoeffEntity>()
        
        coeffs.forEach { (rate, values) ->
            values.forEachIndexed { index, value ->
                entities.add(
                    ViperDdcCoeffEntity(
                        presetId = presetId,
                        sampleRate = rate,
                        index = index,
                        value = value
                    )
                )
            }
        }
        return entities
    }
}

