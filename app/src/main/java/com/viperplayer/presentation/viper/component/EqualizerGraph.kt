package com.viperplayer.presentation.viper.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.viperplayer.domain.model.ViperEqualizerMath

@Composable
fun EqualizerGraph(
    bandCount: Int,
    gains: List<Float>,
    modifier: Modifier = Modifier
) {
    // Calculate points - This is expensive so we should optimize or move to ViewModel
    // For now, remember derived state
    val points = remember(bandCount, gains) {
        ViperEqualizerMath.calculateFrequencyResponse(bandCount, gains)
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    MaterialTheme.colorScheme.surfaceVariant
    val outlineColor = MaterialTheme.colorScheme.outline

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(150.dp)
            .background(Color.Transparent)
    ) {
        val width = size.width
        val height = size.height

        // Draw grid lines (simplified)
        // 0dB line
        val zeroY = height / 2
        drawLine(
            color = outlineColor.copy(alpha = 0.5f),
            start = Offset(0f, zeroY),
            end = Offset(width, zeroY),
            strokeWidth = 1.dp.toPx()
        )

        // +12dB line
        drawLine(
            color = outlineColor.copy(alpha = 0.2f),
            start = Offset(0f, 0f),
            end = Offset(width, 0f),
            strokeWidth = 1.dp.toPx()
        )

        // -12dB line
        drawLine(
            color = outlineColor.copy(alpha = 0.2f),
            start = Offset(0f, height),
            end = Offset(width, height),
            strokeWidth = 1.dp.toPx()
        )

        // Frequency lines (logarithmic)
        val freqs = listOf(100f, 1000f, 10000f)
        freqs.forEach { freq ->
            val x = ViperEqualizerMath.freqToX(freq) * width
            drawLine(
                color = outlineColor.copy(alpha = 0.2f),
                start = Offset(x, 0f),
                end = Offset(x, height),
                strokeWidth = 1.dp.toPx()
            )
        }

        // Draw graph
        val path = Path()
        if (points.isNotEmpty()) {
            val maxDB = 12.0 // +/- 12dB range
            val stepX = width / (points.size - 1)

            points.forEachIndexed { index, dB ->
                val x = index * stepX
                // Map dB to Y: +12 -> 0, -12 -> height
                // y = height/2 - (dB / maxDB) * (height/2)
                val y = (zeroY - (dB / maxDB) * zeroY).toFloat().coerceIn(0f, height)

                if (index == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }

            drawPath(
                path = path,
                color = primaryColor,
                style = Stroke(width = 2.dp.toPx())
            )

            // Fill area below
            path.lineTo(width, height)
            path.lineTo(0f, height)
            path.close()

            drawPath(
                path = path,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.3f),
                        Color.Transparent
                    )
                )
            )
        }
    }
}
