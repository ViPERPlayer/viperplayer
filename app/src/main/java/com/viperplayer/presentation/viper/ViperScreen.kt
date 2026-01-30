package com.viperplayer.presentation.viper

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viperplayer.R
import com.viperplayer.presentation.common.ViperScaffold
import com.viperplayer.presentation.ktx.bottom
import com.viperplayer.presentation.viper.component.SwitchBar
import com.viperplayer.presentation.viper.effect.AnalogXEffect
import com.viperplayer.presentation.viper.effect.AuditorySystemProtectionEffect
import com.viperplayer.presentation.viper.effect.ConvolverEffect
import com.viperplayer.presentation.viper.effect.DifferentialSurroundEffect
import com.viperplayer.presentation.viper.effect.DynamicSystemEffect
import com.viperplayer.presentation.viper.effect.FieldSurroundEffect
import com.viperplayer.presentation.viper.effect.IirEqualizerEffect
import com.viperplayer.presentation.viper.effect.MasterLimiterEffect
import com.viperplayer.presentation.viper.effect.PlaybackGainControlEffect
import com.viperplayer.presentation.viper.effect.SpeakerOptimizationEffect
import com.viperplayer.presentation.viper.effect.SpectrumExtensionEffect
import com.viperplayer.presentation.viper.effect.TubeSimulator6N1JEffect
import com.viperplayer.presentation.viper.effect.ViPERBassEffect
import com.viperplayer.presentation.viper.effect.ViPERClarityEffect
import com.viperplayer.presentation.viper.effect.ViperDdcEffect

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
            Spacer(Modifier.height(29.dp))
            SwitchBar(
                modifier = Modifier.padding(horizontal = 24.dp),
                title = {
                    Crossfade(
                        targetState = state.effectsState.enabled,
                    ) { enabled ->
                        if (enabled) {
                            Text(
                                text = stringResource(R.string.main_enabled),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.main_disabled),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                checked = state.effectsState.enabled,
                onCheckedChange = viewModel::setEnabled
            )

            AnimatedVisibility(
                visible = state.effectsState.enabled
            ) {
                Column {
                    Spacer(Modifier.height(24.dp))

                    val effects = state.effectsState

                    MasterLimiterEffect(
                        state = effects.masterLimiter,
                        onOutputGainChange = { viewModel.setMasterLimiterOutputGain(it) },
                        onOutputGainReset = { viewModel.resetMasterLimiterOutputGain() },
                        onOutputPanChange = { viewModel.setMasterLimiterOutputPan(it) },
                        onOutputPanReset = { viewModel.resetMasterLimiterOutputPan() },
                        onThresholdLimitChange = { viewModel.setMasterLimiterThresholdLimit(it) },
                        onThresholdLimitReset = { viewModel.resetMasterLimiterThresholdLimit() }
                    )
                    PlaybackGainControlEffect(
                        state = effects.differentialSurround,
                        onEnabledChange = { viewModel.setDifferentialSurroundEnabled(it) },
                        onDelayChange = { viewModel.setDifferentialSurroundDelay(it) },
                        onDelayReset = { viewModel.resetDifferentialSurroundDelay() }
                    )
//                    FETCompressorEffect()
                    ViperDdcEffect(
                        state = effects.viperDdc,
                        ddcFiles = state.ddcFiles,
                        onEnabledChange = { viewModel.setViperDdcEnabled(it) },
                        onFileSelect = { viewModel.setViperDdcFile(it) },
                        onFileImport = { viewModel.importDdcFile(it) },
                        onFileDelete = { viewModel.deleteDdcFile(it) }
                    )
                    SpectrumExtensionEffect(
                        state = effects.spectrumExtension,
                        onEnabledChange = { viewModel.setSpectrumExtensionEnabled(it) },
                        onStrengthChange = { viewModel.setSpectrumExtensionStrength(it) },
                        onStrengthReset = { viewModel.resetSpectrumExtensionStrength() }
                    )

                    IirEqualizerEffect(
                        state = effects.iirEqualizer,
                        onEnabledChange = { viewModel.setIirEqualizerEnabled(it) },
                        onBandCountChange = { viewModel.setIirEqualizerBandCount(it) },
                        onPresetChange = { viewModel.setIirEqualizerPreset(it) },
                        onBandGainChange = { index, gain -> viewModel.setIirEqualizerBandGain(index, gain) },
                        onReset = { viewModel.resetIirEqualizer() }
                    )
                    ConvolverEffect(
                        state = effects.convolver,
                        kernelFiles = state.kernelFiles,
                        onEnabledChange = viewModel::setConvolverEnabled,
                        onImportImpulse = viewModel::importConvolverImpulseResponse,
                        onSelectImpulse = viewModel::selectConvolverImpulseResponse,
                        onDeleteImpulse = viewModel::deleteConvolverImpulseResponse,
                        onCrossChannelChange = viewModel::setConvolverCrossChannel
                    )
                    FieldSurroundEffect(
                        state = effects.fieldSurround,
                        onEnabledChange = { viewModel.setFieldSurroundEnabled(it) },
                        onSurroundStrengthChange = { viewModel.setFieldSurroundStrength(it) },
                        onSurroundStrengthReset = { viewModel.resetFieldSurroundStrength() },
                        onMidImageStrengthChange = { viewModel.setFieldSurroundMidImageStrength(it) },
                        onMidImageStrengthReset = { viewModel.resetFieldSurroundMidImageStrength() }
                    )
                    DifferentialSurroundEffect(
                        state = effects.differentialSurround,
                        onEnabledChange = { viewModel.setDifferentialSurroundEnabled(it) },
                        onDelayChange = { viewModel.setDifferentialSurroundDelay(it) },
                        onDelayReset = { viewModel.resetDifferentialSurroundDelay() }
                    )
//                    HeadphoneSurroundPlusEffect()
//                    ReverberationEffect()
                    DynamicSystemEffect(
                        state = effects.dynamicSystem,
                        onEnabledChange = { viewModel.setDynamicSystemEnabled(it) },
                        onDeviceTypeChange = { viewModel.setDynamicSystemDeviceType(it) },
                        onBassStrengthChange = { viewModel.setDynamicSystemBassStrength(it) },
                        onBassStrengthReset = { viewModel.resetDynamicSystemBassStrength() }
                    )
                    TubeSimulator6N1JEffect(
                        state = effects.tubeSimulator,
                        onEnabledChange = { viewModel.setTubeSimulatorEnabled(it) }
                    )
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
                    ViPERClarityEffect(
                        state = effects.viperClarity,
                        onEnabledChange = { viewModel.setViperClarityEnabled(it) },
                        onModeChange = { viewModel.setViperClarityMode(it) },
                        onModeReset = { viewModel.resetViperClarityMode() },
                        onGainChange = { viewModel.setViperClarityGain(it) },
                        onGainReset = { viewModel.resetViperClarityGain() }
                    )
                    AuditorySystemProtectionEffect(
                        state = effects.auditorySystemProtection,
                        onEnabledChange = { viewModel.setAuditorySystemProtectionEnabled(it) },
                        onLevelChange = { viewModel.setAuditorySystemProtectionLevel(it) },
                        onLevelReset = { viewModel.resetAuditorySystemProtectionLevel() }
                    )
                    AnalogXEffect(
                        state = effects.analogX,
                        onEnabledChange = { viewModel.setAnalogXEnabled(it) },
                        onLevelChange = { viewModel.setAnalogXLevel(it) },
                        onLevelReset = { viewModel.resetAnalogXLevel() }
                    )
                    SpeakerOptimizationEffect(
                        state = effects.speakerOptimization,
                        onEnabledChange = { viewModel.setSpeakerOptimizationEnabled(it) }
                    )
                }
            }
        }
    }
}