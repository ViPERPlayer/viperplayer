package com.viperplayer.presentation.player

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.Song
import com.viperplayer.presentation.ktx.infiniteBasicMarquee
import com.viperplayer.presentation.theme.ViPERPlayerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun MiniPlayer(
    onMiniPlayerClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    // Use separate flows for optimal performance - only recompose what changed
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()
    val isLiked by viewModel.isLiked.collectAsStateWithLifecycle()

    // Poll position
    var position by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (isActive) {
            position = viewModel.getCurrentPosition()
            delay(16) // ~60fps updates
        }
    }

    MiniPlayerContent(
        song = currentSong,
        isPlaying = isPlaying,
        duration = duration,
        position = position,
        isLiked = isLiked,
        onTogglePlayPause = { viewModel.togglePlayPause() },
        onSkipToNext = { viewModel.skipToNext() },
        onToggleLike = { viewModel.toggleLike() },
        onMiniPlayerClick = onMiniPlayerClick,
        modifier = modifier
    )
}

@Composable
private fun MiniPlayerProgressIndicator(
    position: Long,
    duration: Long,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    if (duration > 0) {
        var progress by remember { mutableFloatStateOf(0f) }
        
        // Track when we last synced with actual position (for discontinuity detection)
        var lastSyncedPosition by remember { mutableLongStateOf(0L) }
        var lastSyncTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
        var syncCounter by remember { mutableIntStateOf(0) }

        // 16ms loop for smooth 60fps updates
        LaunchedEffect(isPlaying, duration, position) {
            // Initialize from current position
            progress = (position.toFloat() / duration).coerceIn(0f, 1f)
            lastSyncedPosition = position
            lastSyncTime = System.currentTimeMillis()

            while (true) {
                delay(16) // ~60fps updates

                if (isPlaying && duration > 0) {
                    // Every ~250ms (every 16th frame), check for discontinuities (seeks)
                    syncCounter++
                    if (syncCounter >= 16) {
                        syncCounter = 0
                        val positionDiff = kotlin.math.abs(position - lastSyncedPosition)
                        val isDiscontinuity = positionDiff > 1000 // More than 1 second difference = seek

                        if (isDiscontinuity) {
                            // Seek detected - sync immediately
                            lastSyncedPosition = position
                            lastSyncTime = System.currentTimeMillis()
                            progress = (position.toFloat() / duration).coerceIn(0f, 1f)
                            continue
                        } else {
                            // Small drift - update tracking
                            lastSyncedPosition = position
                            lastSyncTime = System.currentTimeMillis()
                        }
                    }

                    // Calculate elapsed time and interpolate forward
                    val currentTime = System.currentTimeMillis()
                    val elapsed = (currentTime - lastSyncTime).toFloat()
                    val interpolatedPosition = lastSyncedPosition + elapsed
                    progress = (interpolatedPosition / duration).coerceIn(0f, 1f)
                } else {
                    // When paused, sync to exact position
                    progress = (position.toFloat() / duration).coerceIn(0f, 1f)
                    lastSyncedPosition = position
                    lastSyncTime = System.currentTimeMillis()
                }
            }
        }
        
        CircularProgressIndicator(
            progress = { progress },
            modifier = modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 3.dp,
            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    }
}

@Composable
fun MiniPlayerContent(
    song: Song?,
    isPlaying: Boolean,
    duration: Long,
    position: Long,
    isLiked: Boolean,
    onTogglePlayPause: () -> Unit,
    onSkipToNext: () -> Unit,
    onToggleLike: () -> Unit,
    onMiniPlayerClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pureBlack = false
    val useDarkTheme = isSystemInDarkTheme()

    val configuration = LocalConfiguration.current
    val isTabletLandscape = configuration.screenWidthDp >= 600 &&
            configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val overlayAlpha by animateFloatAsState(
        targetValue = if (isPlaying) 0.0f else 0.4f,
        animationSpec = spring(
            stiffness = Spring.StiffnessLow
        )
    )

    Box(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
            .padding(12.dp)
            .fillMaxWidth()
            .height(64.dp)
    ) {
        Row(
            modifier = Modifier
                .then(
                    if (isTabletLandscape) {
                        Modifier
                            .width(512.dp)
                            .align(Alignment.CenterEnd)
                    } else {
                        Modifier.fillMaxWidth()
                    }
                )
                .fillMaxHeight()
                .clip(CircleShape)
                .background(
                    color = if (pureBlack && useDarkTheme) Color.Black else MaterialTheme.colorScheme.surfaceContainer
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    shape = CircleShape
                )
                .clickable {
                    onMiniPlayerClick()
                }
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Play/Pause button with circular progress indicator (left side)
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                MiniPlayerProgressIndicator(
                    position = position,
                    duration = duration,
                    isPlaying = isPlaying
                )

                // Play/Pause button with thumbnail background
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp)
                        .clip(CircleShape)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            shape = CircleShape
                        )
                        .clickable {
                            onTogglePlayPause()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    // Thumbnail background
                    song?.artworkUrl?.let { thumbnailUrl ->
                        AsyncImage(
                            model = thumbnailUrl,
                            contentDescription = "Artwork",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                        )
                    }

                    // Semi-transparent overlay for better icon visibility
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                color = Color.Black.copy(alpha = overlayAlpha),
                            )
                    )

                    androidx.compose.animation.AnimatedVisibility(
                        visible = !isPlaying,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Song info - takes most space in the middle
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                AnimatedContent(
                    targetState = song?.title ?: "Unknown",
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "song_title"
                ) { targetState ->
                    Text(
                        text = targetState,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.infiniteBasicMarquee(),
                    )
                }

                val artistNames = song?.artists?.joinToString { it.name }
                AnimatedContent(
                    targetState = artistNames,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "song_artist"
                ) { targetState ->
                    if (!targetState.isNullOrBlank()) {
                        Text(
                            text = targetState,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            modifier = Modifier.infiniteBasicMarquee(),
                        )
                    }
                }
            }

            // Action buttons
            Row(
                modifier = Modifier.padding(end = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Favorite button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
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
                        )
                        .clickable {
                            onToggleLike()
                        }
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = if (isLiked) "Unlike" else "Like",
                        tint = if (isLiked)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Next button
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
                            onSkipToNext()
                        }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
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
            MiniPlayerContent(
                song = Song(
                    id = MediaId("test", "1"),
                    title = "Sample Song",
                    artists = listOf(
                        Artist(
                            id = MediaId("test", "artist1"),
                            name = "Sample Artist"
                        )
                    )
                ),
                isPlaying = false,
                duration = 180000L,
                position = 50000L,
                isLiked = false,
                onTogglePlayPause = {},
                onSkipToNext = {},
                onToggleLike = {},
                onMiniPlayerClick = {}
            )
        }
    }
}

