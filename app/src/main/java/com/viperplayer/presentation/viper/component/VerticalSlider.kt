package com.viperplayer.presentation.viper.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.requiredSizeIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    colors: SliderColors = SliderDefaults.colors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    width: Dp = 40.dp,
    height: Dp = 200.dp,
    steps: Int = 0
) {
    // Rotates the standard slider by -90 degrees and adjusts layout constraints
    // to match the visual vertical size.
    Layout(
        content = {
            Slider(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                valueRange = valueRange,
                colors = colors,
                interactionSource = interactionSource,
                steps = steps,
                modifier = Modifier
                    .graphicsLayer {
                        rotationZ = -90f
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                    .requiredSizeIn(
                        minWidth = height,
                        minHeight = width,
                        maxWidth = height,
                        maxHeight = width
                    )
            )
        },
        modifier = modifier
    ) { measurables, constraints ->
        val placeable = measurables.first().measure(
            Constraints(
                minWidth = height.roundToPx(),
                minHeight = width.roundToPx(),
                maxWidth = height.roundToPx(),
                maxHeight = width.roundToPx()
            )
        )

        layout(placeable.height, placeable.width) {
            // Position the rotated slider correctly
            // Rotation is about (0,0) (top-left)
            // After -90 deg rotation, the slider goes UP from (0,0)
            // So we need to shift it DOWN by its width (which is now visual height)
            placeable.place(0, placeable.width)
        }
    }
}
