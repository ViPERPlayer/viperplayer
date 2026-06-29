package com.viperplayer.presentation.viper.effect

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import com.viperplayer.R
import com.viperplayer.domain.model.HeadphoneSurroundState
import com.viperplayer.presentation.viper.component.Effect
import com.viperplayer.presentation.viper.component.ValueSlider

/**
 * Headphone Surround+ (VHE) — virtual headphone surround engine driven by a single
 * quality / effect-level parameter (0..4). The native engine only supports the
 * effect at 44100 Hz and 48000 Hz; other rates pass through unchanged.
 */
@Composable
fun HeadphoneSurroundPlusEffect(
    state: HeadphoneSurroundState,
    onEnabledChange: (Boolean) -> Unit,
    onLevelChange: (Int) -> Unit,
    onLevelReset: () -> Unit
) {
    Effect(
        icon = painterResource(R.drawable.ic_diff_surround),
        title = "Headphone surround+",
        checked = state.enabled,
        onCheckedChange = onEnabledChange
    ) {
        ValueSlider(
            title = "Level",
            summary = "Level ${state.level + 1}",
            value = state.level,
            onValueChange = onLevelChange,
            onValueReset = onLevelReset,
            valueRange = 0..4,
            steps = 3
        )
    }
}
