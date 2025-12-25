package com.viperplayer.presentation.common

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.viperplayer.domain.model.PlayerState
import com.viperplayer.presentation.theme.ViPERPlayerTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * A compact mini player that displays current playback information.
 * Tapping anywhere on it navigates to the full player screen.
 */
@Composable
fun MiniPlayer(
    playerState: PlayerState,
    onPlayPauseClick: () -> Unit,
    onMiniPlayerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
//    if (!playerState.hasContent) {
//        return
//    }

    val position = 50L
    val duration = 100L
    val title = "Song title"
    val artists = listOf(
        "Artist 1",
        "Artist 2"
    )

    val pureBlack = false
    val useDarkTheme = isSystemInDarkTheme()
    val isPlaying = false
    val playbackState = null
    val error = null

    // Cast state
    val isCasting = false
    val castIsPlaying = false

    // Use Cast state when casting
    val effectiveIsPlaying = if (isCasting) castIsPlaying else isPlaying

    val currentView = LocalView.current
    val layoutDirection = LocalLayoutDirection.current
    val coroutineScope = rememberCoroutineScope()
    val swipeSensitivity = 0.73f
    val swipeThumbnail = true

    val configuration = LocalConfiguration.current
    val isTabletLandscape = configuration.screenWidthDp >= 600 &&
            configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val offsetXAnimatable = remember { Animatable(0f) }
    var dragStartTime by remember { mutableLongStateOf(0L) }
    var totalDragDistance by remember { mutableFloatStateOf(0f) }

    val animationSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow
    )

    val overlayAlpha by animateFloatAsState(
        targetValue = if (effectiveIsPlaying) 0.0f else 0.4f,
        label = "overlay_alpha",
        animationSpec = animationSpec
    )

    /**
     * Calculates the auto-swipe threshold based on swipe sensitivity.
     * The formula uses a sigmoid function to determine the threshold dynamically.
     * Constants:
     * - -11.44748: Controls the steepness of the sigmoid curve.
     * - 9.04945: Adjusts the midpoint of the curve.
     * - 600: Base threshold value in pixels.
     *
     * @param swipeSensitivity The sensitivity value (typically between 0 and 1).
     * @return The calculated auto-swipe threshold in pixels.
     */
    fun calculateAutoSwipeThreshold(swipeSensitivity: Float): Int {
        return (600 / (1f + kotlin.math.exp(-(-11.44748 * swipeSensitivity + 9.04945)))).roundToInt()
    }
    val autoSwipeThreshold = calculateAutoSwipeThreshold(swipeSensitivity)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp)
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
            .height(64.dp)
            // Move the swipe detection to the outer box to affect the entire box
            .let { baseModifier ->
                if (swipeThumbnail) {
                    baseModifier.pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                dragStartTime = System.currentTimeMillis()
                                totalDragDistance = 0f
                            },
                            onDragCancel = {
                                coroutineScope.launch {
                                    offsetXAnimatable.animateTo(
                                        targetValue = 0f,
                                        animationSpec = animationSpec
                                    )
                                }
                            },
                            onHorizontalDrag = { _, dragAmount ->
                                val adjustedDragAmount =
                                    if (layoutDirection == LayoutDirection.Rtl) -dragAmount else dragAmount
                                val canSkipPrevious = true
                                val canSkipNext = true
                                val allowLeft = adjustedDragAmount < 0 && canSkipNext
                                val allowRight = adjustedDragAmount > 0 && canSkipPrevious
                                if (allowLeft || allowRight) {
                                    totalDragDistance += kotlin.math.abs(adjustedDragAmount)
                                    coroutineScope.launch {
                                        offsetXAnimatable.snapTo(offsetXAnimatable.value + adjustedDragAmount)
                                    }
                                }
                            },
                            onDragEnd = {
                                val dragDuration = System.currentTimeMillis() - dragStartTime
                                val velocity = if (dragDuration > 0) totalDragDistance / dragDuration else 0f
                                val currentOffset = offsetXAnimatable.value

                                val minDistanceThreshold = 50f
                                val velocityThreshold = (swipeSensitivity * -8.25f) + 8.5f

                                val shouldChangeSong = (
                                        kotlin.math.abs(currentOffset) > minDistanceThreshold &&
                                                velocity > velocityThreshold
                                        ) || (kotlin.math.abs(currentOffset) > autoSwipeThreshold)

                                if (shouldChangeSong) {
                                    val isRightSwipe = currentOffset > 0

//                                    if (isRightSwipe && canSkipPrevious) {
//                                        playerConnection.player.seekToPreviousMediaItem()
//                                    } else if (!isRightSwipe && canSkipNext) {
//                                        playerConnection.player.seekToNext()
//                                    }
                                }

                                coroutineScope.launch {
                                    offsetXAnimatable.animateTo(
                                        targetValue = 0f,
                                        animationSpec = animationSpec
                                    )
                                }
                            }
                        )
                    }
                } else {
                    baseModifier
                }
            }
    ) {
        // Main MiniPlayer box that moves with swipe
        Box(
            modifier = Modifier
                .then(
                    if (isTabletLandscape) {
                        Modifier
                            .width(500.dp)
                            .align(Alignment.CenterEnd) // Right align
                    } else {
                        Modifier.fillMaxWidth()
                    }
                )
                .height(64.dp) // Circular height
                .offset { IntOffset(offsetXAnimatable.value.roundToInt(), 0) }
                .clip(RoundedCornerShape(32.dp)) // Clip first for perfect rounded corners
                .background(
                    color = if (pureBlack && useDarkTheme) Color.Black else MaterialTheme.colorScheme.surfaceContainer
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(32.dp)
                )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
            ) {
                // Play/Pause button with circular progress indicator (left side)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(48.dp)
                ) {
                    // Circular progress indicator around the play button
                    if (duration > 0) {
                        CircularProgressIndicator(
                            progress = { (position.toFloat() / duration).coerceIn(0f, 1f) },
                            modifier = Modifier.size(48.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp,
                            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        )
                    }

                    // Play/Pause button with thumbnail background
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                shape = CircleShape
                            )
                            .clickable {
//                                if (isCasting) {
//                                    if (castIsPlaying) {
//                                        castHandler?.pause()
//                                    } else {
//                                        castHandler?.play()
//                                    }
//                                } else if (playbackState == Player.STATE_ENDED) {
//                                    playerConnection.player.seekTo(0, 0)
//                                    playerConnection.player.playWhenReady = true
//                                } else {
//                                    playerConnection.togglePlayPause()
//                                }
                            }
                    ) {
                        // Thumbnail background
//                        mediaMetadata?.let { metadata ->
//                            AsyncImage(
//                                model = metadata.thumbnailUrl,
//                                contentDescription = null,
//                                contentScale = ContentScale.Crop,
//                                modifier = Modifier
//                                    .fillMaxSize()
//                                    .clip(CircleShape)
//                            )
//                        }

                        // Semi-transparent overlay for better icon visibility
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    color = Color.Black.copy(alpha = overlayAlpha),
                                    shape = CircleShape
                                )
                        )

                        androidx.compose.animation.AnimatedVisibility(
                            visible = playbackState == Player.STATE_ENDED || !effectiveIsPlaying,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Song info - takes most space in the middle
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    AnimatedContent(
                        targetState = title,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "",
                    ) { title ->
                        Text(
                            text = title,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.basicMarquee(iterations = 1, initialDelayMillis = 3000, velocity = 30.dp),
                        )
                    }

                    if (artists.any { it.isNotBlank() }) {
                        AnimatedContent(
                            targetState = artists.joinToString { it },
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "",
                        ) { artists ->
                            Text(
                                text = artists,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.basicMarquee(iterations = 1, initialDelayMillis = 3000, velocity = 30.dp),
                            )
                        }
                    }

                    // Error indicator
                    androidx.compose.animation.AnimatedVisibility(
                        visible = error != null,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        Text(
                            text = "Error playing",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Cast indicator (simple icon, no button styling)
//                if (isCasting) {
//                    Icon(
//                        painter = painterResource(R.drawable.cast_connected),
//                        contentDescription = "Casting",
//                        tint = MaterialTheme.colorScheme.primary,
//                        modifier = Modifier.size(20.dp)
//                    )
//                    Spacer(modifier = Modifier.width(12.dp))
//                }

                // Subscribe/Subscribed button
//                artists.firstOrNull()?.id?.let { artistId ->
//                    val libraryArtist by database.artist(artistId).collectAsState(initial = null)
//                    val isSubscribed = libraryArtist?.artist?.bookmarkedAt != null
//
//                    Box(
//                        contentAlignment = Alignment.Center,
//                        modifier = Modifier
//                            .size(40.dp)
//                            .clip(CircleShape)
//                            .border(
//                                width = 1.dp,
//                                color = if (isSubscribed)
//                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
//                                else
//                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
//                                shape = CircleShape
//                            )
//                            .background(
//                                color = if (isSubscribed)
//                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
//                                else
//                                    Color.Transparent,
//                                shape = CircleShape
//                            )
//                            .clickable {
//                                database.transaction {
//                                    val artist = libraryArtist?.artist
//                                    if (artist != null) {
//                                        update(artist.toggleLike())
//                                    } else {
//                                        metadata.artists.firstOrNull()?.let { artistInfo ->
//                                            insert(
//                                                ArtistEntity(
//                                                    id = artistInfo.id ?: "",
//                                                    name = artistInfo.name,
//                                                    channelId = null,
//                                                    thumbnailUrl = null,
//                                                ).toggleLike()
//                                            )
//                                        }
//                                    }
//                                }
//                            }
//                    ) {
//                        Icon(
//                            painter = painterResource(
//                                if (isSubscribed) R.drawable.subscribed else R.drawable.subscribe
//                            ),
//                            contentDescription = null,
//                            tint = if (isSubscribed)
//                                MaterialTheme.colorScheme.primary
//                            else
//                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
//                            modifier = Modifier.size(20.dp)
//                        )
//                    }
//                }

                Spacer(modifier = Modifier.width(8.dp))

                // Favorite button (right side)
                val isLiked = false

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .border(
                            width = 1.dp,
                            color = if (isLiked)
                                MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                            else
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            shape = CircleShape
                        )
                        .background(
                            color = if (isLiked)
                                MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                            else
                                Color.Transparent,
                            shape = CircleShape
                        )
                        .clickable {
//                            playerConnection.service.toggleLike()
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = if (isLiked)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Preview
@Composable
fun MiniPlayerPreview() {
    ViPERPlayerTheme {
        Surface {
            MiniPlayer(
                playerState = PlayerState(),
                onPlayPauseClick = {},
                onMiniPlayerClick = {}
            )
        }
    }
}

