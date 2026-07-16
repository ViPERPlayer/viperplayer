package com.viperplayer.presentation.viper.effect

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viperplayer.R
import com.viperplayer.domain.model.IirEqualizerPresets
import com.viperplayer.domain.model.IirEqualizerState
import com.viperplayer.presentation.viper.component.Effect
import com.viperplayer.presentation.viper.component.EqualizerGraph
import com.viperplayer.presentation.viper.component.VerticalSlider
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IirEqualizerEffect(
    state: IirEqualizerState,
    onEnabledChange: (Boolean) -> Unit,
    onBandCountChange: (Int) -> Unit,
    onPresetChange: (String) -> Unit,
    onBandGainChange: (Int, Float) -> Unit,
    onImportAutoEq: (String) -> Unit,
    onReset: () -> Unit
) {
    // AutoEq profiles are plain text; some providers hand them out as .txt (text/plain) and some as
    // generic octet streams, so accept both.
    val autoEqLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { onImportAutoEq(it.toString()) }
    }

    Effect(
        icon = painterResource(R.drawable.ic_spectrum), // Used ic_spectrum as fallback
        title = stringResource(R.string.iir_equalizer),
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
                text = stringResource(R.string.iir_band_count),
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
                        Text(stringResource(R.string.iir_bands_count, count))
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
                label = { Text(stringResource(R.string.effect_preset)) },
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

        Spacer(modifier = Modifier.height(8.dp))

        // Import AutoEq / GraphicEQ correction profile
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = {
                    autoEqLauncher.launch(arrayOf("text/plain", "application/octet-stream", "*/*"))
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.FileUpload,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.iir_import_autoeq))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

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
                    // Range is -12dB to +12dB (24 dB span) at 0.1 dB steps => 240 intervals; the
                    // Slider `steps` parameter is (intervals - 1), i.e. 239 (see VerticalSlider below).
                    // Force Locale.US so the dB value renders "-5.0 dB", not "-5,0 dB" in
                    // comma-decimal locales.
                    Text(
                        text = String.format(Locale.US, "%.1f dB", state.bandGains.getOrElse(index) { 0f }),
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center
                    )

                    VerticalSlider(
                        value = state.bandGains.getOrElse(index) { 0f },
                        onValueChange = { onBandGainChange(index, it) },
                        valueRange = IirEqualizerPresets.MIN_GAIN_DB..IirEqualizerPresets.MAX_GAIN_DB,
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
