package com.viperplayer.presentation.viper

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viperplayer.R
import com.viperplayer.domain.model.ViperEffectsState
import com.viperplayer.presentation.common.ViperScaffold
import com.viperplayer.presentation.common.components.SurfaceCard
import com.viperplayer.presentation.ktx.bottom
import com.viperplayer.presentation.viper.effect.AnalogXEffect
import com.viperplayer.presentation.viper.effect.AuditorySystemProtectionEffect
import com.viperplayer.presentation.viper.effect.ConvolverEffect
import com.viperplayer.presentation.viper.effect.DifferentialSurroundEffect
import com.viperplayer.presentation.viper.effect.DynamicSystemEffect
import com.viperplayer.presentation.viper.effect.FETCompressorEffect
import com.viperplayer.presentation.viper.effect.FieldSurroundEffect
import com.viperplayer.presentation.viper.effect.HeadphoneSurroundPlusEffect
import com.viperplayer.presentation.viper.effect.IirEqualizerEffect
import com.viperplayer.presentation.viper.effect.MasterLimiterEffect
import com.viperplayer.presentation.viper.effect.PlaybackGainControlEffect
import com.viperplayer.presentation.viper.effect.ReverberationEffect
import com.viperplayer.presentation.viper.effect.SpeakerOptimizationEffect
import com.viperplayer.presentation.viper.effect.SpectrumExtensionEffect
import com.viperplayer.presentation.viper.effect.TubeSimulator6N1JEffect
import com.viperplayer.presentation.viper.effect.ViPERBassEffect
import com.viperplayer.presentation.viper.effect.ViPERClarityEffect
import com.viperplayer.presentation.viper.effect.ViperDdcEffect

/** Horizontal inset for the ViPER FX screen content, matching the mockup's edge gutter. */
private val ScreenHorizontalPadding = 16.dp

/** Gap between stacked effect cards in the mockup. */
private val EffectCardSpacing = 10.dp

/** Opacity applied to an effect card whose effect is currently switched off (mockup dims them). */
private const val OffEffectAlpha = 0.72f

@Composable
fun ViperScreen(
    rootPadding: PaddingValues,
    viewModel: ViperViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ViperScaffold { contentPadding ->
        Column(
            modifier = Modifier
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(rootPadding.bottom()),
        ) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.viper_fx_title),
                modifier = Modifier.padding(horizontal = ScreenHorizontalPadding),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
            )

            Spacer(Modifier.height(20.dp))

            MasterHeroSwitch(
                modifier = Modifier.padding(horizontal = ScreenHorizontalPadding),
                effects = state.effectsState,
                onCheckedChange = viewModel::setEnabled,
            )

            AnimatedVisibility(
                visible = state.effectsState.enabled
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = ScreenHorizontalPadding),
                    verticalArrangement = Arrangement.spacedBy(EffectCardSpacing),
                ) {
                    Spacer(Modifier.height(4.dp))

                    val effects = state.effectsState

                    EffectCard(active = true) {
                        MasterLimiterEffect(
                            state = effects.masterLimiter,
                            onOutputGainChange = { viewModel.setMasterLimiterOutputGain(it) },
                            onOutputGainReset = { viewModel.resetMasterLimiterOutputGain() },
                            onOutputPanChange = { viewModel.setMasterLimiterOutputPan(it) },
                            onOutputPanReset = { viewModel.resetMasterLimiterOutputPan() },
                            onThresholdLimitChange = { viewModel.setMasterLimiterThresholdLimit(it) },
                            onThresholdLimitReset = { viewModel.resetMasterLimiterThresholdLimit() }
                        )
                    }
                    EffectCard(active = effects.playbackGain.enabled) {
                        PlaybackGainControlEffect(
                            state = effects.playbackGain,
                            onEnabledChange = { viewModel.setPlaybackGainEnabled(it) },
                            onStrengthChange = { viewModel.setPlaybackGainStrength(it) },
                            onStrengthReset = { viewModel.resetPlaybackGainStrength() },
                            onMaxGainChange = { viewModel.setPlaybackGainMaxGain(it) },
                            onMaxGainReset = { viewModel.resetPlaybackGainMaxGain() },
                            onOutputThresholdChange = { viewModel.setPlaybackGainOutputThreshold(it) },
                            onOutputThresholdReset = { viewModel.resetPlaybackGainOutputThreshold() }
                        )
                    }
                    EffectCard(active = effects.fetCompressor.enabled) {
                        FETCompressorEffect(
                            state = effects.fetCompressor,
                            onEnabledChange = { viewModel.setFetCompressorEnabled(it) },
                            onThresholdChange = { viewModel.setFetCompressorThreshold(it) },
                            onThresholdReset = { viewModel.resetFetCompressorThreshold() },
                            onRatioChange = { viewModel.setFetCompressorRatio(it) },
                            onRatioReset = { viewModel.resetFetCompressorRatio() },
                            onKneeChange = { viewModel.setFetCompressorKnee(it) },
                            onKneeReset = { viewModel.resetFetCompressorKnee() },
                            onAutoKneeChange = { viewModel.setFetCompressorAutoKnee(it) },
                            onGainChange = { viewModel.setFetCompressorGain(it) },
                            onGainReset = { viewModel.resetFetCompressorGain() },
                            onAutoGainChange = { viewModel.setFetCompressorAutoGain(it) },
                            onAttackChange = { viewModel.setFetCompressorAttack(it) },
                            onAttackReset = { viewModel.resetFetCompressorAttack() },
                            onAutoAttackChange = { viewModel.setFetCompressorAutoAttack(it) },
                            onReleaseChange = { viewModel.setFetCompressorRelease(it) },
                            onReleaseReset = { viewModel.resetFetCompressorRelease() },
                            onAutoReleaseChange = { viewModel.setFetCompressorAutoRelease(it) },
                            onKneeMultiChange = { viewModel.setFetCompressorKneeMulti(it) },
                            onKneeMultiReset = { viewModel.resetFetCompressorKneeMulti() },
                            onMaxAttackChange = { viewModel.setFetCompressorMaxAttack(it) },
                            onMaxAttackReset = { viewModel.resetFetCompressorMaxAttack() },
                            onMaxReleaseChange = { viewModel.setFetCompressorMaxRelease(it) },
                            onMaxReleaseReset = { viewModel.resetFetCompressorMaxRelease() },
                            onCrestChange = { viewModel.setFetCompressorCrest(it) },
                            onCrestReset = { viewModel.resetFetCompressorCrest() },
                            onAdaptChange = { viewModel.setFetCompressorAdapt(it) },
                            onAdaptReset = { viewModel.resetFetCompressorAdapt() },
                            onNoClipChange = { viewModel.setFetCompressorNoClip(it) }
                        )
                    }
                    EffectCard(active = effects.viperDdc.enabled) {
                        ViperDdcEffect(
                            state = effects.viperDdc,
                            ddcFiles = state.ddcFiles,
                            onEnabledChange = { viewModel.setViperDdcEnabled(it) },
                            onFileSelect = { viewModel.setViperDdcFile(it) },
                            onFileImport = { viewModel.importDdcFile(it) },
                            onFileDelete = { viewModel.deleteDdcFile(it) }
                        )
                    }
                    EffectCard(active = effects.spectrumExtension.enabled) {
                        SpectrumExtensionEffect(
                            state = effects.spectrumExtension,
                            onEnabledChange = { viewModel.setSpectrumExtensionEnabled(it) },
                            onStrengthChange = { viewModel.setSpectrumExtensionStrength(it) },
                            onStrengthReset = { viewModel.resetSpectrumExtensionStrength() }
                        )
                    }
                    EffectCard(active = effects.iirEqualizer.enabled) {
                        IirEqualizerEffect(
                            state = effects.iirEqualizer,
                            onEnabledChange = { viewModel.setIirEqualizerEnabled(it) },
                            onBandCountChange = { viewModel.setIirEqualizerBandCount(it) },
                            onPresetChange = { viewModel.setIirEqualizerPreset(it) },
                            onBandGainChange = { index, gain ->
                                viewModel.setIirEqualizerBandGain(
                                    index,
                                    gain
                                )
                            },
                            onImportAutoEq = { viewModel.importAutoEqProfile(it) },
                            onReset = { viewModel.resetIirEqualizer() }
                        )
                    }
                    EffectCard(active = effects.convolver.enabled) {
                        ConvolverEffect(
                            state = effects.convolver,
                            kernelFiles = state.kernelFiles,
                            onEnabledChange = viewModel::setConvolverEnabled,
                            onImportImpulse = viewModel::importConvolverImpulseResponse,
                            onSelectImpulse = viewModel::selectConvolverImpulseResponse,
                            onDeleteImpulse = viewModel::deleteConvolverImpulseResponse,
                            onCrossChannelChange = viewModel::setConvolverCrossChannel
                        )
                    }
                    EffectCard(active = effects.fieldSurround.enabled) {
                        FieldSurroundEffect(
                            state = effects.fieldSurround,
                            onEnabledChange = { viewModel.setFieldSurroundEnabled(it) },
                            onSurroundStrengthChange = { viewModel.setFieldSurroundStrength(it) },
                            onSurroundStrengthReset = { viewModel.resetFieldSurroundStrength() },
                            onMidImageStrengthChange = { viewModel.setFieldSurroundMidImageStrength(it) },
                            onMidImageStrengthReset = { viewModel.resetFieldSurroundMidImageStrength() }
                        )
                    }
                    EffectCard(active = effects.differentialSurround.enabled) {
                        DifferentialSurroundEffect(
                            state = effects.differentialSurround,
                            onEnabledChange = { viewModel.setDifferentialSurroundEnabled(it) },
                            onDelayChange = { viewModel.setDifferentialSurroundDelay(it) },
                            onDelayReset = { viewModel.resetDifferentialSurroundDelay() }
                        )
                    }
                    EffectCard(active = effects.headphoneSurround.enabled) {
                        HeadphoneSurroundPlusEffect(
                            state = effects.headphoneSurround,
                            onEnabledChange = { viewModel.setHeadphoneSurroundEnabled(it) },
                            onLevelChange = { viewModel.setHeadphoneSurroundLevel(it) },
                            onLevelReset = { viewModel.resetHeadphoneSurroundLevel() }
                        )
                    }
                    EffectCard(active = effects.reverberation.enabled) {
                        ReverberationEffect(
                            state = effects.reverberation,
                            onEnabledChange = { viewModel.setReverberationEnabled(it) },
                            onRoomSizeChange = { viewModel.setReverberationRoomSize(it) },
                            onRoomSizeReset = { viewModel.resetReverberationRoomSize() },
                            onWidthChange = { viewModel.setReverberationWidth(it) },
                            onWidthReset = { viewModel.resetReverberationWidth() },
                            onDampChange = { viewModel.setReverberationDamp(it) },
                            onDampReset = { viewModel.resetReverberationDamp() },
                            onWetChange = { viewModel.setReverberationWet(it) },
                            onWetReset = { viewModel.resetReverberationWet() },
                            onDryChange = { viewModel.setReverberationDry(it) },
                            onDryReset = { viewModel.resetReverberationDry() }
                        )
                    }
                    EffectCard(active = effects.dynamicSystem.enabled) {
                        DynamicSystemEffect(
                            state = effects.dynamicSystem,
                            onEnabledChange = { viewModel.setDynamicSystemEnabled(it) },
                            onDeviceTypeChange = { viewModel.setDynamicSystemDeviceType(it) },
                            onBassStrengthChange = { viewModel.setDynamicSystemBassStrength(it) },
                            onBassStrengthReset = { viewModel.resetDynamicSystemBassStrength() }
                        )
                    }
                    EffectCard(active = effects.tubeSimulator.enabled) {
                        TubeSimulator6N1JEffect(
                            state = effects.tubeSimulator,
                            onEnabledChange = { viewModel.setTubeSimulatorEnabled(it) }
                        )
                    }
                    EffectCard(active = effects.viperBass.enabled) {
                        ViPERBassEffect(
                            state = effects.viperBass,
                            onEnabledChange = { viewModel.setViperBassEnabled(it) },
                            onModeChange = { viewModel.setViperBassMode(it) },
                            onModeReset = { viewModel.resetViperBassMode() },
                            onFrequencyChange = { viewModel.setViperBassFrequency(it) },
                            onFrequencyReset = { viewModel.resetViperBassFrequency() },
                            onGainChange = { viewModel.setViperBassGain(it) },
                            onGainReset = { viewModel.resetViperBassGain() }
                        )
                    }
                    EffectCard(active = effects.viperClarity.enabled) {
                        ViPERClarityEffect(
                            state = effects.viperClarity,
                            onEnabledChange = { viewModel.setViperClarityEnabled(it) },
                            onModeChange = { viewModel.setViperClarityMode(it) },
                            onModeReset = { viewModel.resetViperClarityMode() },
                            onGainChange = { viewModel.setViperClarityGain(it) },
                            onGainReset = { viewModel.resetViperClarityGain() }
                        )
                    }
                    EffectCard(active = effects.auditorySystemProtection.enabled) {
                        AuditorySystemProtectionEffect(
                            state = effects.auditorySystemProtection,
                            onEnabledChange = { viewModel.setAuditorySystemProtectionEnabled(it) },
                            onLevelChange = { viewModel.setAuditorySystemProtectionLevel(it) },
                            onLevelReset = { viewModel.resetAuditorySystemProtectionLevel() }
                        )
                    }
                    EffectCard(active = effects.analogX.enabled) {
                        AnalogXEffect(
                            state = effects.analogX,
                            onEnabledChange = { viewModel.setAnalogXEnabled(it) },
                            onLevelChange = { viewModel.setAnalogXLevel(it) },
                            onLevelReset = { viewModel.resetAnalogXLevel() }
                        )
                    }
                    EffectCard(active = effects.speakerOptimization.enabled) {
                        SpeakerOptimizationEffect(
                            state = effects.speakerOptimization,
                            onEnabledChange = { viewModel.setSpeakerOptimizationEnabled(it) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * The hero master switch at the top of the ViPER FX screen: a fully-rounded [primaryContainer] pill
 * with a circular effect-icon badge, a title that crossfades between Enabled/Disabled, a summary of
 * how many effects are currently active, and a trailing switch. The whole pill toggles the master
 * enable (matching the previous SwitchBar behaviour).
 */
@Composable
private fun MasterHeroSwitch(
    effects: ViperEffectsState,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val checked = effects.enabled
    val activeCount = remember(effects) { effects.activeEffectCount() }

    Surface(
        onClick = { onCheckedChange(!checked) },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(999.dp),
        color = if (checked) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val onContainer = if (checked) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = if (checked) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = if (checked) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Crossfade(
                    targetState = checked,
                    label = "masterSwitchTitle",
                ) { enabled ->
                    Text(
                        text = stringResource(
                            if (enabled) R.string.main_enabled else R.string.main_disabled
                        ),
                        color = onContainer,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = if (activeCount > 0) {
                        pluralStringResource(
                            R.plurals.viper_fx_effects_active,
                            activeCount,
                            activeCount,
                        )
                    } else {
                        stringResource(R.string.viper_fx_all_effects_off)
                    },
                    color = onContainer.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors().copy(
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                ),
            )
        }
    }
}

/**
 * Wraps a single effect in the redesign's rounded [SurfaceCard] container. Cards whose effect is
 * switched off are dimmed to [OffEffectAlpha] (the mockup dims inactive effects) with an animated
 * fade so toggling reads smoothly.
 */
@Composable
private fun EffectCard(
    active: Boolean,
    content: @Composable () -> Unit,
) {
    val cardAlpha by animateFloatAsState(
        targetValue = if (active) 1f else OffEffectAlpha,
        animationSpec = tween(250),
        label = "effectCardAlpha",
    )
    SurfaceCard(modifier = Modifier.alpha(cardAlpha)) {
        content()
    }
}

/**
 * Number of currently-enabled effects, for the master switch summary. The master limiter has no
 * enable toggle and is excluded. This is a pure display derivation over already-loaded state.
 */
private fun ViperEffectsState.activeEffectCount(): Int = listOf(
    spectrumExtension.enabled,
    fieldSurround.enabled,
    differentialSurround.enabled,
    playbackGain.enabled,
    dynamicSystem.enabled,
    tubeSimulator.enabled,
    viperBass.enabled,
    viperClarity.enabled,
    auditorySystemProtection.enabled,
    analogX.enabled,
    speakerOptimization.enabled,
    viperDdc.enabled,
    convolver.enabled,
    fetCompressor.enabled,
    headphoneSurround.enabled,
    reverberation.enabled,
    iirEqualizer.enabled,
).count { it }
