package com.viperplayer.presentation.viper.effect

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viperplayer.R
import com.viperplayer.domain.model.FetCompressorState
import com.viperplayer.presentation.viper.component.Effect
import com.viperplayer.presentation.viper.component.ValueSlider

/**
 * FET Compressor (feedback dynamics compressor).
 *
 * All continuous parameters are normalised 0-100 % values (sent to native as
 * value/100 -> [0,1]); the native engine maps them to their real units
 * (threshold dB, attack/release ms, etc.). The auto-* and no-clip parameters are
 * booleans rendered as inline toggles.
 */
@Composable
fun FETCompressorEffect(
    state: FetCompressorState,
    onEnabledChange: (Boolean) -> Unit,
    onThresholdChange: (Int) -> Unit,
    onThresholdReset: () -> Unit,
    onRatioChange: (Int) -> Unit,
    onRatioReset: () -> Unit,
    onKneeChange: (Int) -> Unit,
    onKneeReset: () -> Unit,
    onAutoKneeChange: (Boolean) -> Unit,
    onGainChange: (Int) -> Unit,
    onGainReset: () -> Unit,
    onAutoGainChange: (Boolean) -> Unit,
    onAttackChange: (Int) -> Unit,
    onAttackReset: () -> Unit,
    onAutoAttackChange: (Boolean) -> Unit,
    onReleaseChange: (Int) -> Unit,
    onReleaseReset: () -> Unit,
    onAutoReleaseChange: (Boolean) -> Unit,
    onKneeMultiChange: (Int) -> Unit,
    onKneeMultiReset: () -> Unit,
    onMaxAttackChange: (Int) -> Unit,
    onMaxAttackReset: () -> Unit,
    onMaxReleaseChange: (Int) -> Unit,
    onMaxReleaseReset: () -> Unit,
    onCrestChange: (Int) -> Unit,
    onCrestReset: () -> Unit,
    onAdaptChange: (Int) -> Unit,
    onAdaptReset: () -> Unit,
    onNoClipChange: (Boolean) -> Unit
) {
    Effect(
        icon = painterResource(R.drawable.ic_master_limiter),
        title = stringResource(R.string.fet_compressor),
        checked = state.enabled,
        onCheckedChange = onEnabledChange
    ) {
        ValueSlider(
            title = stringResource(R.string.effect_threshold),
            summaryUnit = "%",
            value = state.threshold,
            onValueChange = onThresholdChange,
            onValueReset = onThresholdReset,
            valueRange = 0..100
        )
        ValueSlider(
            title = stringResource(R.string.effect_ratio),
            summaryUnit = "%",
            value = state.ratio,
            onValueChange = onRatioChange,
            onValueReset = onRatioReset,
            valueRange = 0..100
        )
        ValueSlider(
            title = stringResource(R.string.fet_knee_width),
            summaryUnit = "%",
            value = state.knee,
            onValueChange = onKneeChange,
            onValueReset = onKneeReset,
            valueRange = 0..100
        )
        FetToggleRow(
            title = stringResource(R.string.fet_auto_knee),
            checked = state.autoKnee,
            onCheckedChange = onAutoKneeChange
        )
        ValueSlider(
            title = stringResource(R.string.effect_makeup_gain),
            summaryUnit = "%",
            value = state.gain,
            onValueChange = onGainChange,
            onValueReset = onGainReset,
            valueRange = 0..100
        )
        FetToggleRow(
            title = stringResource(R.string.fet_auto_gain),
            checked = state.autoGain,
            onCheckedChange = onAutoGainChange
        )
        ValueSlider(
            title = stringResource(R.string.effect_attack),
            summaryUnit = "%",
            value = state.attack,
            onValueChange = onAttackChange,
            onValueReset = onAttackReset,
            valueRange = 0..100
        )
        FetToggleRow(
            title = stringResource(R.string.fet_auto_attack),
            checked = state.autoAttack,
            onCheckedChange = onAutoAttackChange
        )
        ValueSlider(
            title = stringResource(R.string.effect_release),
            summaryUnit = "%",
            value = state.release,
            onValueChange = onReleaseChange,
            onValueReset = onReleaseReset,
            valueRange = 0..100
        )
        FetToggleRow(
            title = stringResource(R.string.fet_auto_release),
            checked = state.autoRelease,
            onCheckedChange = onAutoReleaseChange
        )
        ValueSlider(
            title = stringResource(R.string.fet_knee_multiplier),
            summaryUnit = "%",
            value = state.kneeMulti,
            onValueChange = onKneeMultiChange,
            onValueReset = onKneeMultiReset,
            valueRange = 0..100
        )
        ValueSlider(
            title = stringResource(R.string.fet_max_attack),
            summaryUnit = "%",
            value = state.maxAttack,
            onValueChange = onMaxAttackChange,
            onValueReset = onMaxAttackReset,
            valueRange = 0..100
        )
        ValueSlider(
            title = stringResource(R.string.fet_max_release),
            summaryUnit = "%",
            value = state.maxRelease,
            onValueChange = onMaxReleaseChange,
            onValueReset = onMaxReleaseReset,
            valueRange = 0..100
        )
        ValueSlider(
            title = stringResource(R.string.fet_crest_factor),
            summaryUnit = "%",
            value = state.crest,
            onValueChange = onCrestChange,
            onValueReset = onCrestReset,
            valueRange = 0..100
        )
        ValueSlider(
            title = stringResource(R.string.fet_adapt),
            summaryUnit = "%",
            value = state.adapt,
            onValueChange = onAdaptChange,
            onValueReset = onAdaptReset,
            valueRange = 0..100
        )
        FetToggleRow(
            title = stringResource(R.string.fet_no_clip),
            checked = state.noClip,
            onCheckedChange = onNoClipChange
        )
    }
}

@Composable
private fun FetToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 18.sp,
            letterSpacing = 0.5.sp
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
