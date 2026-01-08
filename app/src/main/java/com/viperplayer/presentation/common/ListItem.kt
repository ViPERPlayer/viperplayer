package com.viperplayer.presentation.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.Explicit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LibraryAddCheck
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.viperplayer.presentation.search.PlayingIndicator
import com.viperplayer.presentation.search.model.ItemBadge
import com.viperplayer.presentation.search.model.SearchItem
import java.util.concurrent.TimeUnit

/**
 * Formats milliseconds to MM:SS format.
 */
private fun formatDuration(millis: Long?): String {
    if (millis == null || millis <= 0) return "--:--"
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
    return "%02d:%02d".format(minutes, seconds)
}

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
        modifier = modifier.size(48.dp), // TODO: Do not hardcode this here!
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = artworkUrl,
            contentDescription = "Artwork",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(
                    if (type == SearchItem.Type.ARTIST)
                        CircleShape
                    else
                        RoundedCornerShape(6.dp) // TODO: Do not hardcode this here!
                )
        )

        AnimatedVisibility(
            visible = isActive
        ) {
            if (isPlaying) {
                PlayingIndicator(
                    color = Color.White,
                    modifier = Modifier.height(24.dp)
                )
            } else {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                )
            }
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
        modifier = modifier.size(48.dp), // TODO: Do not hardcode this here!
        contentAlignment = Alignment.Center
    ) {
        if (isActive) {
            if (isPlaying) {
                PlayingIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp, 20.dp),
                    bars = 3,
                    barWidth = 3.dp
                )
            } else {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = "Play",
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
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (durationMs != null) {
            Text(
                text = formatDuration(durationMs),
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
                contentDescription = "More"
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
            Icon(Icons.Rounded.MoreVert, contentDescription = "More")
        }
    },
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
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
    
    ListItem(
        headlineContent = {
            Column {
                Text(
                    text = title,
                    modifier = Modifier.basicMarquee(
                        iterations = Int.MAX_VALUE
                    ),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )

                if (badges.isNotEmpty() || subtitle != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                                modifier = Modifier.basicMarquee(
                                    iterations = Int.MAX_VALUE
                                ),
                                color = MaterialTheme.colorScheme.secondary,
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
        modifier = modifier.then(clickModifier)
    )
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
                Icon(Icons.Rounded.MoreVert, contentDescription = "More")
            }
        },
        onClick = onClick,
        onLongClick = onLongClick,
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
        modifier = modifier
    )
}