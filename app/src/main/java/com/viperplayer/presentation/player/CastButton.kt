package com.viperplayer.presentation.player

import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.media3.cast.MediaRouteButton

/**
 * The Google Cast route button for the player top bar.
 *
 * Wraps media3's Compose [MediaRouteButton], which self-initializes the Cast framework (via the
 * manifest-registered [com.viperplayer.data.player.cast.CastOptionsProvider]) and — importantly —
 * renders nothing until a cast route is actually available, so it only appears when the user has a
 * castable device on the network. Tapping it opens the system output-switcher / route chooser.
 *
 * The button tints itself from [LocalContentColor]; we pin it to white to match the player's other
 * top-bar icons. No data/business logic lives here — the framework owns discovery and session state.
 *
 * On a device without Google Play Services the underlying [MediaRouteButton] initializes the Cast
 * context asynchronously (via `CastContext.getSharedInstance(context, executor)`), which defers/stores
 * the failure instead of throwing, so the media-route selector simply never populates and the button
 * stays hidden — it degrades gracefully rather than crashing. (Still worth a no-Play-Services device
 * smoke-test, since this path can't be exercised by JVM unit tests.)
 */
@Composable
fun CastButton(modifier: Modifier = Modifier) {
    CompositionLocalProvider(LocalContentColor provides Color.White) {
        MediaRouteButton(modifier = modifier)
    }
}
