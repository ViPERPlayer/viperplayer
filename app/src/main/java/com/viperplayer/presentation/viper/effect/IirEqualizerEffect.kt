package com.viperplayer.presentation.viper.effect

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viperplayer.R
import com.viperplayer.domain.model.IirEqualizerPresets
import com.viperplayer.domain.model.IirEqualizerState
import com.viperplayer.presentation.viper.component.Effect
import com.viperplayer.presentation.viper.component.EqualizerGraph
import com.viperplayer.presentation.viper.component.VerticalSlider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IirEqualizerEffect(
    state: IirEqualizerState,
    onEnabledChange: (Boolean) -> Unit,
    onBandCountChange: (Int) -> Unit,
    onPresetChange: (String) -> Unit,
    onBandGainChange: (Int, Float) -> Unit,
    onReset: () -> Unit
) {
    Effect(
        icon = painterResource(R.drawable.ic_spectrum), // Used ic_spectrum as fallback
        title = "IIR Equalizer", // Using string literal as I can't check strings.xml easily right now, TODO
        checked = state.enabled,
        onCheckedChange = onEnabledChange
    ) {
        // Band Count Selector
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "Band Count",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                val bands = listOf(10, 15, 31)
                bands.forEachIndexed { index, count ->
                    SegmentedButton(
                        selected = state.bandCount == count,
                        onClick = { onBandCountChange(count) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = bands.size)
                    ) {
                        Text("$count Bands")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Preset Selector
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                readOnly = true,
                value = state.preset,
                onValueChange = {},
                label = { Text("Preset") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                IirEqualizerPresets.PRESETS.forEach { preset ->
                    DropdownMenuItem(
                        text = { Text(preset) },
                        onClick = {
                            onPresetChange(preset)
                            expanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Graph
        EqualizerGraph(
            bandCount = state.bandCount,
            gains = state.bandGains,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Sliders
        val frequencies = remember(state.bandCount) {
            IirEqualizerPresets.getFrequencies(state.bandCount)
        }

        val scrollState = rememberScrollState()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            frequencies.forEachIndexed { index, freq ->
                val freqLabel = if (freq >= 1000) {
                    "${(freq / 1000).toInt()}k"
                } else {
                    freq.toInt().toString()
                }

                Column(
                    modifier = Modifier.width(40.dp), // Fixed width for alignment
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Vertical Slider wrapper?
                    // We don't have a specific VerticalSlider in the summary, assume standard Slider rotated or custom
                    // Since I cannot implement a complex vertical slider easily without context, 
                    // I will check if WSTSlider can be vertical OR use a custom layout.
                    // For now, let's use a simplified representation or check WSTSlider source.
                    // Actually, let's check `WSTSlider.kt` to see if it supports vertical.

                    // Assuming we need to implement a Vertical Slider here. 
                    // Compose Material3 doesn't have a VerticalSlider yet (experimental in 1.4?).
                    // Let's assume standard Slider is horizontal.

                    // Re-use WSTSlider? No, that looks horizontal.
                    // Let's assume for now we use a vertical Column with Text + Slider (rotated?)

                    // Let's implement a quick CustomVerticalSlider using standard Slider with rotate modifier

                    // Range is -12dB to +12dB = 24dB span.
                    // We want 0.1dB steps.
                    // Total intervals = 24 / 0.1 = 240 intervals.
                    // Steps parameter in Slider is (intervals - 1), so 239.

                    Text(
                        text = "%.1f dB".format(state.bandGains.getOrElse(index) { 0f }),
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center
                    )

                    VerticalSlider(
                        value = state.bandGains.getOrElse(index) { 0f },
                        onValueChange = { onBandGainChange(index, it) },
                        valueRange = -12f..12f,
                        steps = 239,
                        modifier = Modifier
                            .height(200.dp)
                            .width(40.dp)
                            .padding(vertical = 8.dp)
                    )

                    Text(
                        text = freqLabel,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
