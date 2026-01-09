package com.viperplayer.presentation.ktx

import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.MarqueeAnimationMode.Companion.Immediately
import androidx.compose.foundation.MarqueeDefaults.RepeatDelayMillis
import androidx.compose.foundation.MarqueeDefaults.Spacing
import androidx.compose.foundation.MarqueeDefaults.Velocity
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

fun Modifier.infiniteBasicMarquee(
    animationMode: MarqueeAnimationMode = Immediately,
    repeatDelayMillis: Int = RepeatDelayMillis,
    initialDelayMillis: Int = if (animationMode == Immediately) repeatDelayMillis else 0,
    spacing: MarqueeSpacing = Spacing,
    velocity: Dp = Velocity,
): Modifier {
    return basicMarquee(
        iterations = Int.MAX_VALUE,
        animationMode = animationMode,
        repeatDelayMillis = repeatDelayMillis,
        initialDelayMillis = initialDelayMillis,
        spacing = spacing,
        velocity = velocity,
    )
}