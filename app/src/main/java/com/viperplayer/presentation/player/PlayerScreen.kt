package com.viperplayer.presentation.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSliderState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.viperplayer.domain.model.RepeatMode
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.repository.AudioFormat
import com.viperplayer.presentation.common.ListItem
import com.viperplayer.presentation.ktx.infiniteBasicMarquee
import com.viperplayer.presentation.search.model.SearchItem
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
    var showDetailsBottomSheet by remember { mutableStateOf(false) }

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
                IconButton(onClick = { showQueueBottomSheet = true }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = "Queue",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = { viewModel.toggleLike() }) {
                    Icon(
                        imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (isLiked) "Unlike" else "Like",
                        tint = if (isLiked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = { showDetailsBottomSheet = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "Song details",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Album artwork
        Card(
            modifier = Modifier
                .aspectRatio(1f)
                .weight(1f),
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
                    modifier = Modifier.infiniteBasicMarquee(),
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
                    modifier = Modifier.infiniteBasicMarquee(),
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

    if (showDetailsBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showDetailsBottomSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            SongDetailsBottomSheet(
                song = currentSong!!,
                onDismiss = { showDetailsBottomSheet = false },
                viewModel = viewModel
            )
        }
    }
    
    if (showQueueBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showQueueBottomSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            QueueBottomSheet(
                viewModel = viewModel,
                currentSong = currentSong,
                onDismiss = { showQueueBottomSheet = false }
            )
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

/**
 * Material 3 expressive bottom sheet showing song technical details.
 */
@Composable
fun SongDetailsBottomSheet(
    song: Song,
    onDismiss: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    // Get audio format from ExoPlayer on demand
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    var audioFormat by remember { mutableStateOf<AudioFormat?>(null) }
    
    LaunchedEffect(song.id, currentSong?.id) {
        // Only fetch if this is the currently playing song
        if (currentSong?.id == song.id) {
            audioFormat = viewModel.getAudioFormat()
        } else {
            audioFormat = null // Clear format if not the current song
        }
    }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header
        Text(
            text = "Song Details",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Divider()
        
        // Audio format details - from ExoPlayer on demand
        if (audioFormat != null && (audioFormat!!.sampleRate != null || audioFormat!!.bitDepth != null || 
            audioFormat!!.bitrate != null || audioFormat!!.channelCount != null)) {
            DetailSection(
                title = "Audio Format",
                icon = Icons.Filled.MusicNote
            ) {
                audioFormat!!.sampleRate?.let { sampleRate ->
                    DetailRow(
                        label = "Sample Rate",
                        value = "${sampleRate / 1000} kHz"
                    )
                }
                audioFormat!!.bitDepth?.let { bitDepth ->
                    DetailRow(
                        label = "Bit Depth",
                        value = "$bitDepth bit"
                    )
                }
                audioFormat!!.bitrate?.let { bitrate ->
                    DetailRow(
                        label = "Bitrate",
                        value = "$bitrate kbps"
                    )
                }
                audioFormat!!.channelCount?.let { channels ->
                    DetailRow(
                        label = "Channels",
                        value = when (channels) {
                            1 -> "Mono"
                            2 -> "Stereo"
                            else -> "$channels channels"
                        }
                    )
                }
            }
        }
        
        // ReplayGain details
        if (song.replayGainDb != null || song.peakAmplitude != null) {
            DetailSection(
                title = "Audio Normalization",
                icon = Icons.Filled.GraphicEq
            ) {
                song.replayGainDb?.let { replayGain ->
                    DetailRow(
                        label = "ReplayGain",
                        value = String.format("%.2f dB", replayGain)
                    )
                }
                song.peakAmplitude?.let { peak ->
                    DetailRow(
                        label = "Peak Amplitude",
                        value = String.format("%.4f", peak)
                    )
                }
            }
        }
        
        // Track info
        DetailSection(
            title = "Track Information",
            icon = Icons.Filled.Info
        ) {
            song.durationMs?.let { duration ->
                val minutes = TimeUnit.MILLISECONDS.toMinutes(duration)
                val seconds = TimeUnit.MILLISECONDS.toSeconds(duration) % 60
                DetailRow(
                    label = "Duration",
                    value = "${minutes}m ${seconds}s"
                )
            }
            song.trackNumber?.let { track ->
                DetailRow(
                    label = "Track Number",
                    value = track.toString()
                )
            }
            song.discNumber?.let { disc ->
                DetailRow(
                    label = "Disc Number",
                    value = disc.toString()
                )
            }
            song.album?.let { album ->
                DetailRow(
                    label = "Album",
                    value = album.name
                )
            }
        }
        
        // Show message if no details available
        if (audioFormat == null && song.replayGainDb == null && song.peakAmplitude == null) {
            Text(
                text = if (currentSong?.id == song.id) {
                    "No technical details available for this track"
                } else {
                    "Audio format details are only available for the currently playing track"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(vertical = 16.dp)
            )
        }
    }
}

@Composable
private fun DetailSection(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            },
            leadingContent = {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
            )
        )
        Column(
            modifier = Modifier.padding(start = 56.dp, end = 16.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            content()
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun QueueBottomSheet(
    viewModel: PlayerViewModel,
    currentSong: Song?,
    onDismiss: () -> Unit
) {
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    
    // Track which item is being dragged
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var draggedOverIndex by remember { mutableStateOf<Int?>(null) }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header
        Text(
            text = "Queue (${queue.size})",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Divider()
        
        if (queue.isEmpty()) {
            Text(
                text = "Queue is empty",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(vertical = 32.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(queue) { index, song ->
                    val isCurrentSong = currentSong?.id == song.id
                    val isDragged = draggedIndex == index
                    val isDraggedOver = draggedOverIndex == index
                    
                    QueueItem(
                        song = song,
                        index = index,
                        queueSize = queue.size,
                        isCurrentSong = isCurrentSong,
                        isDragged = isDragged,
                        isDraggedOver = isDraggedOver,
                        onDragStart = { draggedIndex = index },
                        onDragEnd = {
                            draggedIndex?.let { fromIndex ->
                                draggedOverIndex?.let { toIndex ->
                                    if (fromIndex != toIndex) {
                                        viewModel.reorderQueue(fromIndex, toIndex)
                                    }
                                }
                            }
                            draggedIndex = null
                            draggedOverIndex = null
                        },
                        onDragOver = { overIndex ->
                            draggedOverIndex = overIndex
                        },
                        onRemove = {
                            viewModel.removeFromQueue(index)
                        },
                        onDuplicate = {
                            viewModel.duplicateInQueue(index)
                        },
                        onPlay = {
                            viewModel.playFromQueue(index)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueItem(
    song: Song,
    index: Int,
    queueSize: Int,
    isCurrentSong: Boolean,
    isDragged: Boolean,
    isDraggedOver: Boolean,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onDragOver: (Int) -> Unit,
    onRemove: () -> Unit,
    onDuplicate: () -> Unit,
    onPlay: () -> Unit
) {
    val density = LocalDensity.current
    var itemHeight by remember { mutableStateOf(0.dp) }
    var cumulativeDrag by remember { mutableStateOf(0f) }
    var lastOverIndex by remember { mutableStateOf(index) }
    
    // Swipe state
    var swipeOffset by remember { mutableStateOf(0f) }
    var isSwipeInProgress by remember { mutableStateOf(false) }
    val swipeThreshold = 100.dp
    
    val animatedSwipeOffset by animateFloatAsState(
        targetValue = if (isSwipeInProgress) swipeOffset else 0f,
        animationSpec = tween(300),
        label = "swipe"
    )
    
    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Background actions (visible when swiped)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.CenterEnd)
                .background(
                    when {
                        animatedSwipeOffset < 0 -> MaterialTheme.colorScheme.errorContainer
                        animatedSwipeOffset > 0 -> MaterialTheme.colorScheme.primaryContainer
                        else -> androidx.compose.ui.graphics.Color.Transparent
                    }
                )
                .padding(horizontal = 16.dp),
            horizontalArrangement = if (animatedSwipeOffset < 0) Arrangement.Start else Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (animatedSwipeOffset < 0) {
                // Swipe left - delete
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Delete",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium
                )
            } else if (animatedSwipeOffset > 0) {
                // Swipe right - duplicate
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Play next",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Play next",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        
        // Main content
        ListItem(
            title = song.title,
            badges = emptyList(),
            subtitle = song.artists.joinToString { it.name },
            isActive = isCurrentSong,
            leadingContent = {
                com.viperplayer.presentation.common.ListItemLeadingArtwork(
                    artworkUrl = song.artworkUrl,
                    type = SearchItem.Type.SONG,
                    isActive = isCurrentSong,
                    isPlaying = false
                )
            },
            trailingContent = {
                Icon(
                    imageVector = Icons.Filled.DragHandle,
                    contentDescription = "Drag to reorder",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp)
                )
            },
            onClick = onPlay,
            modifier = Modifier
                .fillMaxWidth()
                .offset(x = with(density) { animatedSwipeOffset.dp })
                .background(
                    color = when {
                        isDragged -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        isDraggedOver -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                        isCurrentSong -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                        else -> MaterialTheme.colorScheme.surface
                    }
                )
                .pointerInput(index, queueSize, itemHeight) {
                    var isLongPress = false
                    var dragStartTime = 0L
                    val longPressTimeout = 300L // milliseconds
                    
                    detectDragGestures(
                        onDragStart = { offset ->
                            dragStartTime = System.currentTimeMillis()
                            isLongPress = false
                        },
                        onDragEnd = {
                            val thresholdPx = with(density) { swipeThreshold.toPx() }
                            
                            if (isLongPress) {
                                // Vertical drag for reordering ended
                                cumulativeDrag = 0f
                                lastOverIndex = index
                                onDragEnd()
                            } else {
                                // Horizontal swipe ended
                                when {
                                    swipeOffset < -thresholdPx -> {
                                        // Swipe left - delete
                                        onRemove()
                                        swipeOffset = 0f
                                    }
                                    swipeOffset > thresholdPx -> {
                                        // Swipe right - duplicate
                                        onDuplicate()
                                        swipeOffset = 0f
                                    }
                                    else -> {
                                        // Snap back
                                        swipeOffset = 0f
                                    }
                                }
                                isSwipeInProgress = false
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            
                            val elapsed = System.currentTimeMillis() - dragStartTime
                            
                            // Determine if this is a long press (for vertical reordering) or a swipe (horizontal)
                            if (!isLongPress) {
                                if (elapsed > longPressTimeout && kotlin.math.abs(dragAmount.y) > kotlin.math.abs(dragAmount.x) * 1.5f) {
                                    // Long press detected with vertical movement - start reordering
                                    isLongPress = true
                                    cumulativeDrag = 0f
                                    lastOverIndex = index
                                    isSwipeInProgress = false
                                    swipeOffset = 0f
                                    onDragStart()
                                } else if (kotlin.math.abs(dragAmount.x) > kotlin.math.abs(dragAmount.y) * 1.5f) {
                                    // Horizontal movement detected - start swiping
                                    if (!isSwipeInProgress) {
                                        isSwipeInProgress = true
                                    }
                                    swipeOffset = (swipeOffset + dragAmount.x).coerceIn(
                                        -with(density) { swipeThreshold.toPx() * 1.5f },
                                        with(density) { swipeThreshold.toPx() * 1.5f }
                                    )
                                }
                            } else {
                                // Long press is active - handle vertical reordering
                                val itemHeightPx = with(density) { itemHeight.toPx() }
                                if (itemHeightPx == 0f) return@detectDragGestures
                                
                                cumulativeDrag += dragAmount.y
                                
                                val threshold = itemHeightPx * 0.5f
                                val newOverIndex = when {
                                    cumulativeDrag < -threshold && index > 0 -> index - 1
                                    cumulativeDrag > threshold && index < queueSize - 1 -> index + 1
                                    else -> index
                                }
                                
                                if (newOverIndex != lastOverIndex) {
                                    onDragOver(newOverIndex)
                                    lastOverIndex = newOverIndex
                                    cumulativeDrag = 0f
                                }
                            }
                        }
                    )
                }
                .onSizeChanged { size ->
                    itemHeight = with(density) { size.height.toDp() }
                }
        )
    }
}
