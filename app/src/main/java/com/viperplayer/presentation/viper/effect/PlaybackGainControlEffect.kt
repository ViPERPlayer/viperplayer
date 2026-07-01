package com.viperplayer.presentation.viper.effect

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.viperplayer.R
import com.viperplayer.domain.model.PlaybackGainState
import com.viperplayer.presentation.viper.component.Effect
import com.viperplayer.presentation.viper.component.ValueSlider

@Composable
fun PlaybackGainControlEffect(
    state: PlaybackGainState,
    onEnabledChange: (Boolean) -> Unit,
    onStrengthChange: (Int) -> Unit,
    onStrengthReset: () -> Unit,
    onMaxGainChange: (Int) -> Unit,
    onMaxGainReset: () -> Unit,
    onOutputThresholdChange: (Float) -> Unit,
    onOutputThresholdReset: () -> Unit
) {
    Effect(
        icon = painterResource(R.drawable.ic_playback_gain),
        title = stringResource(R.string.playback_gain_control),
        checked = state.enabled,
        onCheckedChange = onEnabledChange
    ) {
        // Strength: 1 to 3
        ValueSlider(
            title = stringResource(R.string.pgc_strength),
            summary = when (state.strength) {
                1 -> stringResource(R.string.pgc_strength_slight)
                2 -> stringResource(R.string.pgc_strength_moderate)
                3 -> stringResource(R.string.pgc_strength_strong)
                else -> state.strength.toString()
            },
            value = state.strength,
            onValueChange = onStrengthChange,
            onValueReset = onStrengthReset,
            valueRange = 1..3
        )

        // Maximum gain: 1 to 10 + infinite
        // Logic: 11 represents Infinite
        ValueSlider(
            title = stringResource(R.string.pgc_maximum_gain),
            summary = if (state.maxGain > 10) "\u221E" else "${state.maxGain}x",
            value = state.maxGain,
            onValueChange = onMaxGainChange,
            onValueReset = onMaxGainReset,
            valueRange = 1..11 // 11 is infinite
        )

        // Output threshold: -10.5, -6, -3, -1.9, -1, 0
        // We can map linear slider 0-5 to these values
        val thresholdValues = listOf(-10.5f, -6.0f, -3.0f, -1.9f, -1.0f, 0.0f)
        // Find closest index for current value
        val currentIndex =
            thresholdValues.indexOfFirst { kotlin.math.abs(it - state.outputThreshold) < 0.1f }
                .takeIf { it >= 0 } ?: 5

        ValueSlider(
            title = stringResource(R.string.pgc_output_threshold),
            summary = "${state.outputThreshold} dB",
            value = currentIndex + 1, // 1-based index for slider
            onValueChange = { index ->
                val newIndex = (index - 1).coerceIn(0, thresholdValues.lastIndex)
                onOutputThresholdChange(thresholdValues[newIndex])
            },
            onValueReset = onOutputThresholdReset,
            valueRange = 1..6
        )
    }
}