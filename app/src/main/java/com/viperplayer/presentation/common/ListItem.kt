package com.viperplayer.presentation.common

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.res.stringResource
import com.viperplayer.R
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddToQueue
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.Explicit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LibraryAddCheck
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueuePlayNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.viperplayer.presentation.ktx.infiniteBasicMarquee
import com.viperplayer.presentation.search.PlayingIndicator
import com.viperplayer.presentation.ktx.formatDuration
import com.viperplayer.presentation.search.model.ItemBadge
import com.viperplayer.presentation.search.model.SearchItem
import com.viperplayer.presentation.theme.Spacing
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Size of a list row's leading slot (artwork thumbnail / track-number cell). */
private val LeadingContentSize = 48.dp

/** Corner radius for non-circular (non-artist) leading artwork thumbnails. */
private val ArtworkCornerRadius = 6.dp

/**
 * Prebuilt leading content for artwork with play indicator overlay.
 */
@Composable
fun ListItemLeadingArtwork(
    artworkUrl: String?,
    type: SearchItem.Type,
    isActive: Boolean,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(LeadingContentSize),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = artworkUrl,
            contentDescription = stringResource(R.string.cd_artwork),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(
                    if (type == SearchItem.Type.ARTIST)
                        CircleShape
                    else
                        RoundedCornerShape(ArtworkCornerRadius)
                )
        )

        PlayingArtworkOverlay(isActive = isActive, isPlaying = isPlaying)
    }
}

/**
 * The now-playing overlay shown centered over artwork: the animated bar graph while playing, a play
 * arrow when the current item is paused. Shared by [ListItemLeadingArtwork] and the home cards so the
 * indicator looks identical everywhere — don't inline a second copy.
 */
@Composable
fun PlayingArtworkOverlay(isActive: Boolean, isPlaying: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible = isActive, modifier = modifier) {
        if (isPlaying) {
            PlayingIndicator(
                color = Color.White,
                modifier = Modifier.height(Spacing.xxl)
            )
        } else {
            Icon(
                Icons.Rounded.PlayArrow,
                contentDescription = stringResource(R.string.action_play),
                tint = Color.White,
            )
        }
    }
}

/**
 * Prebuilt leading content for track number with play indicator.
 */
@Composable
fun ListItemLeadingTrackNumber(
    trackNumber: Int?,
    isActive: Boolean,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(LeadingContentSize),
        contentAlignment = Alignment.Center
    ) {
        if (isActive) {
            if (isPlaying) {
                PlayingIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(Spacing.lg, Spacing.xl),
                    bars = 3,
                    barWidth = 3.dp
                )
            } else {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(R.string.action_play),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        } else {
            Text(
                text = trackNumber?.toString() ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
        }
    }
}

/**
 * Prebuilt trailing content with duration and more button.
 */
@Composable
fun ListItemTrailingWithDuration(
    durationMs: Long?,
    onMoreClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (durationMs != null) {
            Text(
                text = formatDuration(durationMs, padMinutes = true),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }

        IconButton(
            onClick = onMoreClick
        ) {
            Icon(
                Icons.Rounded.MoreVert,
                contentDescription = stringResource(R.string.action_more)
            )
        }
    }
}

/**
 * Base ListItem composable that handles click interactions.
 * 
 * This component is a pure UI component that accepts callbacks for user interactions.
 * It uses [combinedClickable] internally for convenience when callbacks are provided.
 * 
 * Note: Bottom sheets and other stateful UI should be managed at the screen level,
 * not inside this component, to maintain separation of concerns.
 */
@Composable
fun ListItem(
    title: String,
    badges: List<ItemBadge>,
    subtitle: String?,
    isActive: Boolean,
    leadingContent: @Composable () -> Unit,
    trailingContent: @Composable () -> Unit = {
        IconButton(onClick = {}) {
            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.action_more))
        }
    },
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onPlayNext: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // Apply combinedClickable only when callbacks are provided
    // This allows the component to handle both click and long-click interactions
    val clickModifier = if (onClick != null || onLongClick != null) {
        Modifier.combinedClickable(
            onClick = onClick ?: {},
            onLongClick = onLongClick
        )
    } else {
        Modifier
    }

    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val addedToQueueMessage = stringResource(R.string.toast_added_to_queue)
    val playingNextMessage = stringResource(R.string.toast_playing_next)

    // Max drag distance: capped, consistent
    val maxOffset = with(density) { 112.dp.toPx() }
    // Threshold: ~55% of max drag distance
    val threshold = maxOffset * 0.55f

    val offset = remember { Animatable(0f) }

    val draggableState = rememberDraggableState { delta ->
        scope.launch {
            val coercedOffset = (offset.value + delta).coerceIn(-maxOffset, maxOffset)

            val wasPastThreshold = abs(offset.value) >= threshold
            val isPastThreshold = abs(coercedOffset) >= threshold
            if (isPastThreshold != wasPastThreshold) {
                haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
            }

            offset.snapTo(coercedOffset)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .draggable(
                state = draggableState,
                orientation = Orientation.Horizontal,
                onDragStopped = { velocity ->
                    scope.launch {
                        when {
                            offset.value < -threshold -> onPlayNext?.let {
                                it()
                                Toast.makeText(context, playingNextMessage, Toast.LENGTH_SHORT).show()
                            }
                            offset.value > threshold -> onAddToQueue?.let {
                                it()
                                Toast.makeText(context, addedToQueueMessage, Toast.LENGTH_SHORT).show()
                            }
                        }
                        offset.animateTo(0f)
                    }
                }
            )
    ) {
        val absOffsetDp = with(density) { abs(offset.value).toDp() }
        when {
            // dragged to the left, show right ui
            offset.value < 0 -> {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .fillMaxHeight()
                        .width(absOffsetDp)
                        .clipToBounds()
                ) {
                    Icon(
                        Icons.Rounded.QueuePlayNext,
                        contentDescription = stringResource(R.string.action_play_next),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .requiredSize(Spacing.xxl)
                    )
                }
            }

            // dragged to the right, show left ui
            offset.value > 0 -> {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .fillMaxHeight()
                        .width(absOffsetDp)
                        .clipToBounds()
                ) {
                    Icon(
                        Icons.Rounded.AddToQueue,
                        contentDescription = stringResource(R.string.action_add_to_queue),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .requiredSize(Spacing.xxl)
                    )
                }
            }
        }

        ListItem(
            headlineContent = {
                Column {
                    Text(
                        text = title,
                        modifier = Modifier.infiniteBasicMarquee(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )

                    if (badges.isNotEmpty() || subtitle != null) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (badges.isNotEmpty()) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    badges.forEach { badge ->
                                        val icon = when (badge) {
                                            ItemBadge.FAVORITE -> Icons.Rounded.Favorite
                                            ItemBadge.EXPLICIT -> Icons.Rounded.Explicit
                                            ItemBadge.LIBRARY -> Icons.Rounded.LibraryAddCheck
                                            ItemBadge.DOWNLOADING -> Icons.Rounded.Downloading
                                            ItemBadge.DOWNLOADED -> Icons.Rounded.Download
                                        }

                                        Icon(
                                            icon,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            if (subtitle != null) {
                                Text(
                                    subtitle,
                                    modifier = Modifier.infiniteBasicMarquee(),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            },
            leadingContent = leadingContent,
            trailingContent = trailingContent,
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(x = offset.value.roundToInt(), y = 0) }
                .then(clickModifier)
        )
    }
}

/**
 * Convenience overload for artwork-based list items (e.g., search results).
 */
@Composable
fun ListItem(
    type: SearchItem.Type,
    title: String,
    badges: List<ItemBadge>,
    subtitle: String?,
    artworkUrl: String?,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: (() -> Unit)? = null,
    onMoreClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    onPlayNext: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    ListItem(
        title = title,
        badges = badges,
        subtitle = subtitle,
        isActive = isActive,
        leadingContent = {
            ListItemLeadingArtwork(
                artworkUrl = artworkUrl,
                type = type,
                isActive = isActive,
                isPlaying = isPlaying
            )
        },
        trailingContent = {
            IconButton(onClick = onMoreClick) {
                Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.action_more))
            }
        },
        onClick = onClick,
        onLongClick = onLongClick,
        onPlayNext = onPlayNext,
        onAddToQueue = onAddToQueue,
        modifier = modifier
    )
}

/**
 * Convenience overload for track number-based list items (e.g., album songs).
 */
@Composable
fun ListItem(
    type: SearchItem.Type,
    title: String,
    badges: List<ItemBadge>,
    subtitle: String?,
    trackNumber: Int?,
    durationMs: Long?,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: (() -> Unit)? = null,
    onMoreClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    onPlayNext: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    ListItem(
        title = title,
        badges = badges,
        subtitle = subtitle,
        isActive = isActive,
        leadingContent = {
            ListItemLeadingTrackNumber(
                trackNumber = trackNumber,
                isActive = isActive,
                isPlaying = isPlaying
            )
        },
        trailingContent = {
            ListItemTrailingWithDuration(
                durationMs = durationMs,
                onMoreClick = onMoreClick
            )
        },
        onClick = onClick,
        onLongClick = onLongClick,
        onPlayNext = onPlayNext,
        onAddToQueue = onAddToQueue,
        modifier = modifier
    )
}