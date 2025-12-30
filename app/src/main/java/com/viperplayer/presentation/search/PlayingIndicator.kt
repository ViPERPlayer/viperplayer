package com.viperplayer.presentation.search

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

@Composable
fun PlayingIndicator(
    color: Color,
    modifier: Modifier = Modifier,
    bars: Int = 3,
    barWidth: Dp = 4.dp,
    cornerRadius: Dp = 6.dp,
    updateInterval: Long = 50L // time between updates
) {
    // One Animatable per bar
    val animatables = remember(bars) { List(bars) { Animatable(0.1f) } }

    LaunchedEffect(animatables, updateInterval) {
        // Animate all bars concurrently
        animatables.forEach { animatable ->
            launch {
                while (isActive) {
                    animatable.animateTo(Random.nextFloat() * 0.9f + 0.1f)
                    // Wait for next update
                    delay(updateInterval)
                }
            }
        }
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = modifier
    ) {
        animatables.forEach { animatable ->
            Box(
                modifier = Modifier
                    .width(barWidth)
                    .fillMaxHeight(animatable.value)
                    .background(color, RoundedCornerShape(cornerRadius))
            )
        }
    }
}