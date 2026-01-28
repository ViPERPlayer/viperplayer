package com.viperplayer.presentation.viper.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import kotlin.math.log10
import kotlin.math.pow

@Composable
fun FrequencyResponseGraph(
    points: List<Pair<Float, Float>>, // Frequency -> Value
    modifier: Modifier = Modifier,
    minFreq: Float = 20f,
    maxFreq: Float = 20000f,
    minY: Float = -20f,
    maxY: Float = 20f,
    unit: String = "dB",
    lineColor: Color = MaterialTheme.colorScheme.primary,
    gridColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 10.sp
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // Helper to map frequency (log) to X
        fun xForFreq(freq: Float): Float {
            val logMin = log10(minFreq)
            val logMax = log10(maxFreq)
            val logVal = log10(freq.coerceIn(minFreq, maxFreq))
            return ((logVal - logMin) / (logMax - logMin) * width).toFloat()
        }

        // Helper to map value (linear) to Y
        fun yForValue(value: Float): Float {
            val range = maxY - minY
            val norm = (value.coerceIn(minY, maxY) - minY) / range
            return (height * (1f - norm))
        }

        // Draw Grid
        // Vertical lines for frequencies
        val freqLabels = listOf(
            20f to "20",
            50f to "50",
            100f to "100",
            200f to "200",
            500f to "500",
            1000f to "1k",
            2000f to "2k",
            5000f to "5k",
            10000f to "10k",
            20000f to "20k"
        )

        freqLabels.forEach { (freq, label) ->
            val x = xForFreq(freq)
            drawLine(
                color = gridColor,
                start = Offset(x, 0f),
                end = Offset(x, height),
                strokeWidth = 1f
            )
            // Draw label at bottom
            drawText(
                textMeasurer = textMeasurer,
                text = label,
                style = labelStyle,
                topLeft = Offset(x + 2f, height - 30f)
            )
        }

        // Horizontal lines
        val range = maxY - minY
        // Heuristic for steps based on unit or magnitude
        val targetStep = range / 4f
        val exponent = kotlin.math.floor(log10(targetStep))
        val fraction = targetStep / 10f.pow(exponent)
        
        val niceFraction = when {
            fraction < 1.5 -> 1f
            fraction < 3.5 -> 2f
            fraction < 7.5 -> 5f
            else -> 10f
        }
        
        val step = if (unit == "°" && range >= 180) 45f else (niceFraction * 10f.pow(exponent))
        
        // Snap start to step
        val startVal = kotlin.math.floor(minY / step).toFloat() * step
        var currentVal = startVal
        
        while (currentVal <= maxY) {
            // Avoid drawing way out of bounds if snap pushed it below
            if (currentVal >= minY) {
                val y = yForValue(currentVal)
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1f
                )
                if (currentVal != minY && currentVal != maxY) {
                     drawText(
                        textMeasurer = textMeasurer,
                        text = "${currentVal.toInt()}$unit",
                        style = labelStyle,
                        topLeft = Offset(2f, y - 15f)
                    )
                }
            }
            currentVal += step
        }
        
        // Zero line emphasis
        if (minY < 0 && maxY > 0) {
            val zeroY = yForValue(0f)
            drawLine(
                color = gridColor.copy(alpha = 0.5f),
                start = Offset(0f, zeroY),
                end = Offset(width, zeroY),
                strokeWidth = 2f
            )
        }

        // Draw Curve
        if (points.isNotEmpty()) {
            val path = Path()
            var first = true
            
            points.forEach { (freq, value) ->
                val x = xForFreq(freq)
                val y = yForValue(value)
                if (first) {
                    path.moveTo(x, y)
                    first = false
                } else {
                    path.lineTo(x, y)
                }
            }
            
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 3f)
            )
        }
    }
}
