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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.viperplayer.presentation.viper.effect.DifferentialSurroundEffect
import com.viperplayer.presentation.viper.effect.DynamicSystemEffect
import com.viperplayer.presentation.viper.effect.FieldSurroundEffect
import com.viperplayer.presentation.viper.effect.MasterLimiterEffect
import com.viperplayer.presentation.viper.effect.SpeakerOptimizationEffect
import com.viperplayer.presentation.viper.effect.SpectrumExtensionEffect
import com.viperplayer.presentation.viper.effect.TubeSimulator6N1JEffect
import com.viperplayer.presentation.viper.effect.ViPERBassEffect
import com.viperplayer.presentation.viper.effect.ViPERClarityEffect

@Composable
fun ViperScreen(
    rootPadding: PaddingValues,
    viewModel: ViperViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var openPresetDialog by remember { mutableStateOf(false) }

    ViperScaffold(
//        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
//            MainTopAppBar(
//                scrollBehavior = scrollBehavior,
//                onOpenPresets = {
//                    openPresetDialog = true
//                },
//                onOpenDriverStatus = {
//                    openStatusDialog = true
//                },
//                onOpenSettings = onNavigateToSettings,
//            )
        },
    ) { contentPadding ->
//        BottomSheet(
//            onDismissRequest = { /*TODO*/ }
//        ) {
//            Text("Hello, Bottom Sheet!")
//        }
//        EqualizerBottomSheetPreview()

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
                        targetState = state.enabled,
                    ) { enabled ->
                        if (enabled) {
                            Text(
                                text = stringResource(R.string.main_enabled)
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.main_disabled)
                            )
                        }
                    }
                },
                checked = state.enabled,
                onCheckedChange = viewModel::setEnabled
            )

            AnimatedVisibility(
                visible = state.enabled
            ) {
                Column {
                    Spacer(Modifier.height(24.dp))

                    MasterLimiterEffect()
//                    PlaybackGainControlEffect()
//                    FETCompressorEffect()
//                    ViPERDDCEffect()
                    SpectrumExtensionEffect()
//                    FIREqualizerEffect()
//                    ConvolverEffect()
                    FieldSurroundEffect()
                    DifferentialSurroundEffect()
//                    HeadphoneSurroundPlusEffect()
//                    ReverberationEffect()
                    DynamicSystemEffect()
                    TubeSimulator6N1JEffect()
                    ViPERBassEffect()
                    ViPERClarityEffect()
                    AuditorySystemProtectionEffect()
                    AnalogXEffect()
                    SpeakerOptimizationEffect()
                }
            }
        }
    }

    if (openPresetDialog) {
//        PresetDialog(onDismissRequest = { openPresetDialog = false })
    }
}