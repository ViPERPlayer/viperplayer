package com.viperplayer.presentation.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

/**
 * Fade + slide-up the first time a composable enters composition, with a small index-based stagger
 * so lists/grids cascade in on load. Inside a Lazy list, items re-run this as they (re)enter the
 * viewport, giving a gentle fade-in on scroll as well. Deliberately short and subtle so scrolling
 * never feels gated; the stagger is taken modulo a small window so off-screen indices stay snappy.
 *
 * @param index position used for the stagger (e.g. the item index)
 * @param slidePx how far (px) the content slides up while fading in
 */
@Composable
fun Modifier.revealOnAppear(index: Int = 0, slidePx: Float = 30f): Modifier {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay((index % 8) * 26L)
        visible = true
    }
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "revealOnAppear",
    )
    return graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * slidePx
    }
}
