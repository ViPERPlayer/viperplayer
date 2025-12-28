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
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.viperplayer.presentation.theme.ViPERPlayerTheme

@Composable
fun MiniPlayer(
    onMiniPlayerClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    MiniPlayerContent(
        state = state,
        onTogglePlayPause = { viewModel.togglePlayPause() },
        onSkipToNext = { viewModel.skipToNext() },
        onToggleLike = { viewModel.toggleLike() },
        onMiniPlayerClick = onMiniPlayerClick,
        modifier = modifier
    )
}

@Composable
fun MiniPlayerContent(
    state: PlayerUiState,
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
        targetValue = if (state.isPlaying) 0.0f else 0.4f,
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
                // Circular progress indicator around the play button
                if (state.duration > 0) {
                    CircularProgressIndicator(
                        progress = { (state.position.toFloat() / state.duration).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp,
                        trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                }

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
                    state.thumbnailUrl?.let { thumbnailUrl ->
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
                        visible = !state.isPlaying,
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
                    targetState = state.title,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                ) { targetState ->
                    Text(
                        text = targetState,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                    )
                }

                state.artists.joinToString().let { joinedArtists ->
                    if (joinedArtists.isNotBlank()) {
                        AnimatedContent(
                            targetState = joinedArtists,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                        ) { targetState ->
                            Text(
                                text = targetState,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                            )
                        }
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
                            color = if (state.isLiked)
                                MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                            else
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            shape = CircleShape
                        )
                        .background(
                            color = if (state.isLiked)
                                MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                            else
                                Color.Transparent,
                        )
                        .clickable {
                            onToggleLike()
                        }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FavoriteBorder,
                        contentDescription = null,
                        tint = if (state.isLiked)
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
                state = PlayerUiState(),
                onTogglePlayPause = {},
                onSkipToNext = {},
                onToggleLike = {},
                onMiniPlayerClick = {}
            )
        }
    }
}

