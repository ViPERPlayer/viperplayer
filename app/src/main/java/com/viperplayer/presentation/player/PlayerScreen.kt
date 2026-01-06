package com.viperplayer.presentation.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.viperplayer.domain.model.RepeatMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.concurrent.TimeUnit

/**
 * Formats milliseconds to MM:SS format.
 */
private fun formatDuration(millis: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
    return "%02d:%02d".format(minutes, seconds)
}

/**
 * Full-screen player UI.
 * Displayed when user taps the mini player or navigates to NowPlaying.
 */
@Composable
fun PlayerScreen(
    contentWindowInsets: WindowInsets = BottomSheetDefaults.windowInsets,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    // Use separate flows for optimal performance
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()
    val isLiked by viewModel.isLiked.collectAsStateWithLifecycle()

    // Poll position
    var currentPosition by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (isActive) {
            currentPosition = viewModel.getCurrentPosition()
            delay(16) // ~60fps updates
        }
    }

    var showQueueBottomSheet by remember { mutableStateOf(false) }

    // Don't show player if no song
    if (currentSong == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No song playing",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(contentWindowInsets)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Top bar with actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Now Playing",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { viewModel.toggleLike() }) {
                    Icon(
                        imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (isLiked) "Unlike" else "Like",
                        tint = if (isLiked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = { showQueueBottomSheet = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "More options",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Album artwork
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .weight(1f, fill = false),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            AsyncImage(
                model = currentSong!!.artworkUrl,
                contentDescription = "Album artwork",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Song info
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedContent(
                targetState = currentSong!!.title,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "song_title"
            ) { title ->
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            val artistNames = currentSong!!.artists.joinToString { it.name }
            AnimatedContent(
                targetState = artistNames,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "song_artist"
            ) { artists ->
                Text(
                    text = artists,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                    maxLines = 1
                )
            }
        }

        // Playback slider with
        PlayerProgressSlider(
            position = currentPosition,
            duration = duration,
            onSeek = { position ->
                viewModel.seekTo(position)
            }
        )

        // Playback controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Shuffle button
            FilledTonalIconButton(
                onClick = { viewModel.toggleShuffle() },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Shuffle,
                    contentDescription = "Shuffle",
                    modifier = Modifier.size(24.dp),
                    tint = if (playbackState.shuffleEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }

            // Previous button
            IconButton(
                onClick = { viewModel.skipToPrevious() },
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.SkipPrevious,
                    contentDescription = "Previous",
                    modifier = Modifier.size(32.dp)
                )
            }

            // Play/Pause button (larger, primary)
            FilledTonalIconButton(
                onClick = { viewModel.togglePlayPause() },
                modifier = Modifier.size(72.dp)
            ) {
                Icon(
                    imageVector = if (playbackState.isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(40.dp)
                )
            }

            // Next button
            IconButton(
                onClick = { viewModel.skipToNext() },
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.SkipNext,
                    contentDescription = "Next",
                    modifier = Modifier.size(32.dp)
                )
            }

            // Repeat button
            FilledTonalIconButton(
                onClick = { viewModel.cycleRepeatMode() },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Repeat,
                    contentDescription = when (playbackState.repeatMode) {
                        RepeatMode.OFF -> "Repeat off"
                        RepeatMode.ONE -> "Repeat one"
                        RepeatMode.ALL -> "Repeat all"
                    },
                    modifier = Modifier.size(24.dp),
                    tint = when (playbackState.repeatMode) {
                        RepeatMode.OFF -> MaterialTheme.colorScheme.onSurface
                        RepeatMode.ONE -> MaterialTheme.colorScheme.primary
                        RepeatMode.ALL -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showQueueBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showQueueBottomSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            dragHandle = null
        ) {
            QueueScreen()
        }
    }
}

/**
 * Isolated progress slider composable that only recomposes when position changes.
 * This prevents the entire PlayerScreen from recomposing.
 */
@Composable
private fun PlayerProgressSlider(
    position: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val sliderState = rememberSliderState()

    sliderState.onValueChangeFinished = {
        val newPosition = (sliderState.value * duration).toLong()
        onSeek(newPosition)
    }

    LaunchedEffect(sliderState.isDragging, position, duration) {
        if (!sliderState.isDragging && duration > 0) {
            sliderState.value = (position.toFloat() / duration).coerceIn(0f, 1f)
        }
    }
    
    Column(modifier = modifier.fillMaxWidth()) {
        Slider(
            state = sliderState,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )

        // Time display - read position from lambda
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDuration(position),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Text(
                text = formatDuration(duration),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun QueueScreen() {
    // TODO: Implement queue screen
    Text(
        text = "Queue",
        modifier = Modifier.padding(16.dp)
    )
}
