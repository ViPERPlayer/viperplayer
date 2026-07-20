package com.viperplayer.presentation.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viperplayer.R
import com.viperplayer.presentation.theme.ViPERPlayerTheme

/** Default avatar diameter; also the touch-target floor for tappable avatars. */
val DefaultAvatarSize = 44.dp

/** Thickness of the live-activity gradient ring. */
private val RingThickness = 2.dp

/** Gap between the ring and the inner content so the ring reads as a separate stroke. */
private val RingGap = 2.dp

/**
 * A circular avatar frame that owns the overall [size]. When [hasLiveActivity] is true the frame
 * draws a conic primary → tertiary → primary gradient ring and insets [content] by the ring width
 * plus a small gap to signal the person is currently listening; otherwise [content] fills the frame
 * with no ring.
 *
 * [content] is laid out to fill the (possibly inset) frame, so pass an avatar body that fills its
 * space — [InitialsAvatar] / [PersonGlyphAvatar] do this when their own `size` is left at the default
 * `null`. It's a slot rather than a fixed type so callers (e.g. Home) can also stack a small
 * now-playing artwork badge on top of the avatar inside a [Box].
 *
 * [size] defaults to 44dp — the minimum touch target for a tappable avatar; keep it at least that
 * for interactive avatars, and only go smaller for purely decorative ones (e.g. stacked overlap
 * chips).
 */
@Composable
fun AvatarRing(
    modifier: Modifier = Modifier,
    size: Dp = DefaultAvatarSize,
    hasLiveActivity: Boolean = false,
    showRing: Boolean = hasLiveActivity,
    content: @Composable () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val liveDescription = stringResource(R.string.cd_avatar_live)
    // Draw the gradient identity ring whenever [showRing] — always for profile / signed-in avatars,
    // and also when live. The "live" a11y label is only announced when [hasLiveActivity].
    val ringModifier = if (showRing) {
        Modifier.drawBehind {
            // A true stroked ring hugging the frame edge, so the gradient shows only as a ring
            // (never as a disc behind transparent/non-circular slot content).
            val stroke = RingThickness.toPx()
            drawCircle(
                brush = Brush.sweepGradient(listOf(primary, tertiary, primary)),
                radius = (this.size.minDimension - stroke) / 2f,
                style = Stroke(width = stroke),
            )
        }
    } else {
        Modifier
    }
    val liveSemantics = if (hasLiveActivity) {
        // Merge the inner avatar's own contentDescription into this node so a screen reader
        // announces one element ("<name>, live") instead of two separate focus stops.
        Modifier.semantics(mergeDescendants = true) { contentDescription = liveDescription }
    } else {
        Modifier
    }
    // Inset the content by ring + gap only when the ring is drawn, leaving a real gap around it.
    val inset = if (showRing) RingThickness + RingGap else 0.dp
    Box(
        modifier = modifier.size(size).then(liveSemantics).then(ringModifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(inset)) {
            content()
        }
    }
}

/**
 * A solid colour circle with centered initials — the standard identity avatar body when no photo is
 * available. Defaults to primaryContainer / onPrimaryContainer (the "A" avatar in the mockups); pass
 * [containerColor]/[contentColor] to tint per-person. Pass one or two initials — the glyph scales to
 * ~40% of the diameter and stays on a single line.
 *
 * Leave [size] `null` to fill the parent (the norm inside an [AvatarRing]); pass an explicit size to
 * render the avatar standalone. Initials scale to ~40% of the resolved diameter either way.
 */
@Composable
fun InitialsAvatar(
    text: String,
    modifier: Modifier = Modifier,
    size: Dp? = null,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    BoxWithConstraints(
        modifier = modifier
            .then(if (size != null) Modifier.size(size) else Modifier.fillMaxSize())
            .clip(CircleShape)
            .background(containerColor)
            .semantics { contentDescription = text },
        contentAlignment = Alignment.Center,
    ) {
        val diameter = minOf(maxWidth, maxHeight)
        Text(
            text = text,
            color = contentColor,
            fontSize = (diameter.value * 0.4f).sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/**
 * The fallback avatar body when there's no photo and no initials — a filled circle with a generic
 * person glyph. Used for placeholder/unknown people.
 *
 * Leave [size] `null` to fill the parent (the norm inside an [AvatarRing]).
 */
@Composable
fun PersonGlyphAvatar(
    modifier: Modifier = Modifier,
    size: Dp? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Box(
        modifier = modifier
            .then(if (size != null) Modifier.size(size) else Modifier.fillMaxSize())
            .clip(CircleShape)
            .background(containerColor)
            .padding(if (size != null) size * 0.25f else 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Person,
            contentDescription = stringResource(R.string.cd_avatar),
            tint = contentColor,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF141218)
@Composable
private fun AvatarsPreview() {
    ViPERPlayerTheme(darkTheme = true) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp),
        ) {
            AvatarRing(size = 62.dp, hasLiveActivity = true) {
                InitialsAvatar(text = "M")
            }
            AvatarRing(size = 62.dp, hasLiveActivity = false) {
                InitialsAvatar(text = "S")
            }
            AvatarRing(size = 62.dp, hasLiveActivity = false) {
                PersonGlyphAvatar()
            }
            InitialsAvatar(text = "A", size = 44.dp)
        }
    }
}
