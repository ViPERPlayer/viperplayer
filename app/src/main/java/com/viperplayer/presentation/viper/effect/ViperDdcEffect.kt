package com.viperplayer.presentation.viper.effect

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.viperplayer.R
import com.viperplayer.domain.model.ViperDdcState
import com.viperplayer.presentation.theme.Spacing
import com.viperplayer.presentation.viper.component.Effect
import com.viperplayer.presentation.viper.component.FrequencyResponseGraph
import java.io.File
import kotlin.math.log10
import kotlin.math.pow

@Composable
fun ViperDdcEffect(
    state: ViperDdcState,
    ddcFiles: List<File>,
    onEnabledChange: (Boolean) -> Unit,
    onFileSelect: (String) -> Unit,
    onFileImport: (String) -> Unit,
    onFileDelete: (String) -> Unit
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { onFileImport(it.toString()) }
    }

    var showDialog by rememberSaveable { mutableStateOf(false) }
    var plotType by rememberSaveable { mutableStateOf(PlotType.MAGNITUDE) }

    Effect(
        icon = painterResource(R.drawable.ic_ddc),
        title = stringResource(R.string.viper_ddc),
        summary = state.selectedDdcFile ?: stringResource(R.string.effect_not_set),
        checked = state.enabled,
        onCheckedChange = onEnabledChange
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showDialog = true }
                .padding(horizontal = Spacing.xxl, vertical = Spacing.sm)
        ) {
            Text(
                text = stringResource(R.string.ddc_headset_correction),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                letterSpacing = 0.5.sp
            )
            Text(
                text = state.selectedDdcFile ?: stringResource(R.string.effect_not_set),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                letterSpacing = 0.25.sp
            )

            if (state.coeffs != null) {
                val data = remember(state.coeffs) {
                    calculateDdcResponse(state.coeffs)
                }

                // Plot Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    PlotType.entries.forEach { type ->
                        val selected = plotType == type
                        FilterChip(
                            selected = selected,
                            onClick = { plotType = type },
                            label = { Text(stringResource(type.labelRes)) }
                        )
                    }
                }

                if (data != null) {
                    val points = when (plotType) {
                        PlotType.MAGNITUDE -> data.magnitude
                        PlotType.PHASE -> data.phase
                        PlotType.GROUP_DELAY -> data.groupDelay
                    }

                    if (points.isNotEmpty()) {
                        // Auto-scale
                        val values = points.map { it.second }
                        val realMin = values.minOrNull() ?: -20f
                        val realMax = values.maxOrNull() ?: 20f

                        val unit = when (plotType) {
                            PlotType.MAGNITUDE -> "dB"
                            PlotType.PHASE -> "°"
                            PlotType.GROUP_DELAY -> "ms"
                        }


                        // Auto-scale with margins
                        val range = realMax - realMin
                        val effectiveRange = if (range == 0f) 10f else range
                        val margin = effectiveRange * 0.1f // 10% margin

                        val minY = realMin - margin
                        val maxY = realMax + margin

                        FrequencyResponseGraph(
                            points = points,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .padding(top = Spacing.lg),
                            minY = minY,
                            maxY = maxY,
                            unit = unit
                        )

                        val rates = state.coeffs.keys.sorted().joinToString(", ")
                        val bandCount = state.coeffs.values.firstOrNull()?.size?.div(5) ?: 0

                        Text(
                            text = stringResource(R.string.ddc_rates_bands, rates, bandCount),
                            modifier = Modifier.padding(top = Spacing.sm),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }


    if (showDialog) {
        Dialog(onDismissRequest = { showDialog = false }) {
            Column(
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(28.dp)
                    )
                    .fillMaxHeight(0.8f) // Limit height
            ) {
                Text(
                    text = stringResource(R.string.ddc_select_file),
                    modifier = Modifier.padding(Spacing.xxl),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .selectableGroup()
                ) {
                    if (ddcFiles.isEmpty()) {
                        Text(
                            text = stringResource(R.string.effect_no_files_loaded),
                            modifier = Modifier
                                .padding(horizontal = Spacing.xxl, vertical = Spacing.lg),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        ddcFiles.forEach { file ->
                            val isSelected = file.name == state.selectedDdcFile
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = isSelected,
                                        role = Role.RadioButton,
                                        onClick = {
                                            onFileSelect(file.name)
                                            showDialog = false
                                        }
                                    )
                                    .padding(horizontal = Spacing.xxl, vertical = Spacing.md),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = null,
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                                Text(
                                    text = file.name,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = Spacing.lg),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                IconButton(onClick = { onFileDelete(file.name) }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.action_delete),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.xxl),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { launcher.launch(arrayOf("*/*")) }
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.action_add_new))
                    }

                    Button(onClick = { showDialog = false }) {
                        Text(stringResource(R.string.action_close))
                    }
                }
            }
        }
    }
}

enum class PlotType(val labelRes: Int) {
    MAGNITUDE(R.string.ddc_plot_magnitude),
    PHASE(R.string.ddc_plot_phase),
    GROUP_DELAY(R.string.ddc_plot_group_delay)
}

data class DdcResponseData(
    val magnitude: List<Pair<Float, Float>>,
    val phase: List<Pair<Float, Float>>,
    val groupDelay: List<Pair<Float, Float>>
)

// Simple Complex number helper
data class Complex(val r: Double, val i: Double) {
    operator fun plus(other: Complex) = Complex(r + other.r, i + other.i)
    operator fun minus(other: Complex) = Complex(r - other.r, i - other.i)
    operator fun times(other: Complex) =
        Complex(r * other.r - i * other.i, r * other.i + i * other.r)

    operator fun div(other: Complex): Complex {
        val d = other.r * other.r + other.i * other.i
        return Complex((r * other.r + i * other.i) / d, (i * other.r - r * other.i) / d)
    }

    fun abs(): Double = kotlin.math.sqrt(r * r + i * i)
    fun arg(): Double = kotlin.math.atan2(i, r) // Phase in radians
}

fun calculateDdcResponse(coeffsMap: Map<Int, List<Float>>): DdcResponseData? {
    // Prefer 44100 or 48000, else first available
    val targetRate = 44100
    val rate = if (coeffsMap.containsKey(targetRate)) targetRate
    else if (coeffsMap.containsKey(48000)) 48000
    else coeffsMap.keys.firstOrNull() ?: return null

    val coeffs = coeffsMap[rate] ?: return null

    // Coeffs are [b0, b1, b2, a1, a2] per biquad
    // 5 floats per biquad
    val numBiquads = coeffs.size / 5

    // Use denser points for GD, but Graph uses 100 points.
    // Let's use 200 points for calc to get better GD derivative
    val frequencies = generateLogFrequencies(20f, 20000f, 200)

    val magnitude = mutableListOf<Pair<Float, Float>>()
    val phase = mutableListOf<Pair<Float, Float>>()
    val groupDelay = mutableListOf<Pair<Float, Float>>()

    var prevPhase = 0.0
    var prevFreq = 0.0

    for ((index, freq) in frequencies.withIndex()) {
        val omega = 2.0 * Math.PI * freq / rate
        val zInv1 = Complex(kotlin.math.cos(omega), -kotlin.math.sin(omega)) // z^-1
        val zInv2 = zInv1 * zInv1 // z^-2

        var totalResponse = Complex(1.0, 0.0)

        for (i in 0 until numBiquads) {
            val base = i * 5
            val b0 = coeffs[base + 0].toDouble()
            val b1 = coeffs[base + 1].toDouble()
            val b2 = coeffs[base + 2].toDouble()
            val a1 = coeffs[base + 3].toDouble()
            val a2 = coeffs[base + 4].toDouble()

            val num = Complex(b0, 0.0) + (Complex(b1, 0.0) * zInv1) + (Complex(b2, 0.0) * zInv2)
            val den = Complex(1.0, 0.0) - (Complex(a1, 0.0) * zInv1) - (Complex(a2, 0.0) * zInv2)

            if (den.r != 0.0 || den.i != 0.0) {
                totalResponse = totalResponse * (num / den)
            }
        }

        // Magnitude
        val mag = totalResponse.abs()
        val db = 20.0 * log10(mag)
        magnitude.add(freq to db.toFloat())

        // Phase (Unwrapped)
        var ph = totalResponse.arg() // -PI to PI

        // Basic unwrapping
        // If jump > PI, decrement by 2PI. If jump < -PI, increment by 2PI.
        // Actually, we process sequentially.
        // But for display we want Wrapped (-180, 180) typically? Or Unwrapped?
        // Let's store unwrapped for GD, and wrapped for display.
        // Wait, for GD we need dPhi/dw.

        // Handle unwrapping relative to previous *accumulated* phase if we were accumulating.
        // But here we just get principal arg.
        // Let's approximate GD with finite difference of principal args, correcting for wrapping.

        val diffPhys = if (index > 0) ph - prevPhase else 0.0
        // Correct wrap
        var diff = diffPhys
        if (diff > Math.PI) diff -= 2 * Math.PI
        if (diff < -Math.PI) diff += 2 * Math.PI

        // Valid for GD
        if (index > 0) {
            2.0 * Math.PI * (freq - prevFreq) / rate
            // GD = - dPhi / dOmega (normalized omega) -> - dPhi/dw * 1/Fs ? 
            // GD(seconds) = - dPhi / d(2*pi*f/Fs) * (1/Fs) ???
            // GD(seconds) = - dPhi / d(2*pi*f) = -dPhi/df * 1/(2pi)

            // dOmega_analog = 2*pi*df.
            // GD = - dPhi / dOmega_analog
            val dt = -diff / (2.0 * Math.PI * (freq - prevFreq)) // Seconds
            groupDelay.add(freq to (dt * 1000.0).toFloat()) // ms
        } else {
            groupDelay.add(freq to 0f)
        }

        prevPhase = ph
        prevFreq = freq.toDouble()

        // Output Phase in Degrees (Wrapped)
        phase.add(freq to Math.toDegrees(ph).toFloat())
    }

    return DdcResponseData(magnitude, phase, groupDelay)
}

fun generateLogFrequencies(start: Float, end: Float, count: Int): List<Float> {
    val logStart = log10(start.toDouble())
    val logEnd = log10(end.toDouble())
    val step = (logEnd - logStart) / (count - 1)

    return (0 until count).map {
        10.0.pow(logStart + it * step).toFloat()
    }
}