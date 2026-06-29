package com.viperplayer.presentation.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.PlaybackContext
import com.viperplayer.domain.model.RepeatMode
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.repository.AudioFormat
import com.viperplayer.presentation.common.ListItem
import com.viperplayer.presentation.ktx.infiniteBasicMarquee
import com.viperplayer.presentation.search.model.SearchItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.sin

/**
 * Formats milliseconds to MM:SS format.
 */
private fun formatDuration(millis: Long): String {
    val safe = millis.coerceAtLeast(0)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(safe)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(safe) % 60
    return "%d:%02d".format(minutes, seconds)
}

/**
 * Full-screen "Now Playing" player — Material 3 Expressive, direction B.
 *
 * The album artwork is painted full-bleed as both the background and (via the app's existing
 * MaterialKolor theming) the source of the color scheme. Chrome floats over the art on scrims:
 * a context chip, the title + animated like, a wavy seek bar, the morphing transport cluster,
 * and an output / queue bar. Shown as a [ModalBottomSheet] from the mini-player.
 */
@Composable
fun PlayerScreen(
    onNavigateToArtist: (Artist) -> Unit,
    onNavigateToAlbum: (Album) -> Unit,
    onCollapse: () -> Unit = {},
    contentWindowInsets: WindowInsets = BottomSheetDefaults.windowInsets,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    val queue by viewModel.queue.collectAsStateWithLifecycle()

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
    var showLyrics by remember { mutableStateOf(false) }
    var showListenTogether by remember { mutableStateOf(false) }
    var showShareInvite by remember { mutableStateOf(false) }
    var showQr by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    val song = currentSong
    if (song == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
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

    val isPlaying = playbackState.isPlaying

    // Artwork pager over the queue: swipe to preview/switch tracks. Falls back to the single song
    // when the queue is empty so there's always exactly one page.
    val pagerSongs = if (queue.isEmpty()) listOf(song) else queue
    val currentIndex = pagerSongs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = currentIndex) { pagerSongs.size }
    // External track change (skip buttons / auto-advance) -> animate the pager to it.
    LaunchedEffect(currentIndex) {
        if (currentIndex != pagerState.currentPage) pagerState.animateScrollToPage(currentIndex)
    }
    // User settled on a different page -> play that track. Guarded against the index it's already on
    // so the programmatic scroll above never loops back into another play call.
    LaunchedEffect(pagerState, pagerSongs) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            if (page != currentIndex && page in pagerSongs.indices) {
                viewModel.playFromQueue(page)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Full-bleed artwork (also the seed for the dynamic theme), over a gradient placeholder.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    )
                )
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                AsyncImage(
                    model = pagerSongs[page].artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Top scrim for status-bar / context legibility.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(220.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent)))
        )
        // Bottom scrim behind the controls cluster.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(560.dp)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.28f to Color.Black.copy(alpha = 0.15f),
                        0.58f to Color.Black.copy(alpha = 0.62f),
                        1f to Color.Black.copy(alpha = 0.86f)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(contentWindowInsets)
        ) {
            // Top bar: collapse · context chip · overflow
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCollapse) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Collapse player",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                ContextChip(context = playbackState.playbackContext)

                Box {
                    IconButton(onClick = { showOverflowMenu = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "More options",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false }
                    ) {
                        song.artists.firstOrNull()?.let { artist ->
                            DropdownMenuItem(
                                text = { Text("View artist") },
                                leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    onNavigateToArtist(artist)
                                }
                            )
                        }
                        song.album?.let { album ->
                            DropdownMenuItem(
                                text = { Text("Go to album") },
                                leadingIcon = { Icon(Icons.Filled.Album, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    onNavigateToAlbum(album)
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Song details") },
                            leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                showDetailsBottomSheet = true
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Bottom cluster
            Column(modifier = Modifier.padding(horizontal = 26.dp, vertical = 26.dp)) {
                // Current lyric line — only when the track actually has lyrics.
                lyrics?.let { lyricsData ->
                    LyricLine(
                        lyrics = lyricsData,
                        positionMs = { currentPosition },
                        onClick = { showLyrics = true },
                        modifier = Modifier.padding(bottom = 18.dp)
                    )
                }

                // Title + artist/album + like
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom
                ) {
                    AnimatedContent(
                        targetState = song,
                        transitionSpec = {
                            (fadeIn(tween(280)) + slideInVertically(tween(280)) { it / 4 }) togetherWith
                                (fadeOut(tween(160)) + slideOutVertically(tween(160)) { -it / 4 })
                        },
                        label = "trackInfo",
                        modifier = Modifier.weight(1f)
                    ) { current ->
                        Column {
                            Text(
                                text = current.title,
                                color = Color.White,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.6).sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.infiniteBasicMarquee()
                            )
                            val subtitle = listOfNotNull(current.artistNames, current.album?.name)
                                .joinToString(" · ")
                            if (subtitle.isNotEmpty()) {
                                Text(
                                    text = subtitle,
                                    color = Color.White.copy(alpha = 0.82f),
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .padding(top = 5.dp)
                                        .infiniteBasicMarquee()
                                )
                            }
                        }
                    }
                    ConnectedLikeButton(viewModel = viewModel)
                }

                Spacer(modifier = Modifier.height(18.dp))

                WavySeekBar(
                    position = { currentPosition },
                    duration = duration,
                    isPlaying = isPlaying,
                    onSeek = { viewModel.seekTo(it) }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Transport cluster
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ToggleIconButton(
                        icon = Icons.Filled.Shuffle,
                        contentDescription = if (playbackState.shuffleEnabled) "Shuffle on" else "Shuffle off",
                        active = playbackState.shuffleEnabled,
                        onClick = { viewModel.toggleShuffle() }
                    )
                    SkipPill(
                        icon = Icons.Filled.SkipPrevious,
                        contentDescription = "Previous",
                        onClick = { viewModel.skipToPrevious() }
                    )
                    MorphPlayButton(
                        isPlaying = isPlaying,
                        onClick = { viewModel.togglePlayPause() }
                    )
                    SkipPill(
                        icon = Icons.Filled.SkipNext,
                        contentDescription = "Next",
                        onClick = { viewModel.skipToNext() }
                    )
                    ToggleIconButton(
                        icon = if (playbackState.repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        contentDescription = when (playbackState.repeatMode) {
                            RepeatMode.OFF -> "Repeat off"
                            RepeatMode.ONE -> "Repeat one"
                            RepeatMode.ALL -> "Repeat all"
                        },
                        active = playbackState.repeatMode != RepeatMode.OFF,
                        onClick = { viewModel.cycleRepeatMode() }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Output + queue bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color.White.copy(alpha = 0.14f))
                            .clickable { showListenTogether = true }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Speaker,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "This phone",
                                color = Color.White,
                                fontSize = 15.sp,
                                lineHeight = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Output device",
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 12.sp,
                                lineHeight = 13.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color.White.copy(alpha = 0.16f))
                            .clickable { showQueueBottomSheet = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = "Queue",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }

    if (showDetailsBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showDetailsBottomSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            SongDetailsBottomSheet(
                song = song,
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
            QueueSheet(
                viewModel = viewModel,
                currentSong = song,
                onDismiss = { showQueueBottomSheet = false }
            )
        }
    }

    if (showLyrics) {
        ModalBottomSheet(
            onDismissRequest = { showLyrics = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            LyricsSheet(
                viewModel = viewModel,
                onSeek = { viewModel.seekTo(it) },
                onDismiss = { showLyrics = false }
            )
        }
    }

    // Listen-together / devices / share / QR — independent sheets that layer: opening a nested one
    // doesn't close the one beneath, so dismissing it reveals the previous sheet.
    PlayerSocialSheets(
        song = song,
        showListenTogether = showListenTogether,
        showShareInvite = showShareInvite,
        showQr = showQr,
        onShowListenTogether = { showListenTogether = it },
        onShowShareInvite = { showShareInvite = it },
        onShowQr = { showQr = it }
    )
}

/**
 * Translucent source chip ("Playing from …"). Display-only; navigation lives in the overflow menu.
 */
@Composable
private fun ContextChip(
    context: PlaybackContext?,
    modifier: Modifier = Modifier
) {
    val (icon: ImageVector, label: String) = when (context) {
        is PlaybackContext.Album -> Icons.Filled.Album to context.name
        is PlaybackContext.Artist -> Icons.Filled.Person to context.name
        is PlaybackContext.Playlist -> Icons.AutoMirrored.Filled.QueueMusic to context.name
        is PlaybackContext.Search -> Icons.Filled.Search to "Search"
        null -> Icons.Filled.MusicNote to "Now playing"
    }
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.16f))
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 150.dp)
        )
    }
}

/**
 * Reads the like state in its own scope so toggling it recomposes ONLY this button — not the whole
 * player around it.
 */
@Composable
private fun ConnectedLikeButton(viewModel: PlayerViewModel) {
    val isLiked by viewModel.isLiked.collectAsStateWithLifecycle()
    LikeButton(isLiked = isLiked, onClick = viewModel::toggleLike)
}

/**
 * Like toggle: Favorite glyph, white → primary, with a press-scale (0.82) bounce.
 */
@Composable
private fun LikeButton(
    isLiked: Boolean,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.82f else 1f, spring(), label = "likeScale")
    val tint by animateColorAsState(
        if (isLiked) MaterialTheme.colorScheme.primary else Color.White,
        label = "likeTint"
    )
    Box(
        modifier = Modifier
            .size(48.dp)
            .scale(scale)
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = if (isLiked) "Remove from liked" else "Add to liked",
            tint = tint,
            modifier = Modifier.size(30.dp)
        )
    }
}

/**
 * Shuffle / repeat icon button: primary when active, white @ 85% when off.
 */
@Composable
private fun ToggleIconButton(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.size(46.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (active) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(25.dp)
        )
    }
}

/**
 * Tonal skip pill: 70×58dp, white @ 16%, corner morphs 24→30dp + scales 0.92 on press.
 */
@Composable
private fun SkipPill(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val corner by animateDpAsState(if (pressed) 30.dp else 24.dp, label = "pillCorner")
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, label = "pillScale")
    Box(
        modifier = Modifier
            .size(width = 70.dp, height = 58.dp)
            .scale(scale)
            .clip(RoundedCornerShape(corner))
            .background(Color.White.copy(alpha = 0.16f))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(34.dp)
        )
    }
}

/**
 * Signature play/pause button: a 90dp primary box that morphs between a circle (paused) and a
 * squircle (playing) with a bouncy spring, plus a press-scale.
 */
@Composable
private fun MorphPlayButton(
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val corner by animateDpAsState(
        targetValue = if (isPlaying) 28.dp else 45.dp,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "playCorner"
    )
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "playScale"
    )
    val shape = RoundedCornerShape(corner)
    Box(
        modifier = Modifier
            .size(90.dp)
            .scale(scale)
            .shadow(14.dp, shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(46.dp)
        )
    }
}

/**
 * Wavy seek bar (§6.2). The played portion is a sine wave (animated while playing, flattened when
 * paused or dragging); the remaining portion is a straight line. The thumb is a vertical pill.
 * Tap or drag to scrub.
 */
@Composable
private fun WavySeekBar(
    position: () -> Long,
    duration: Long,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val remainingColor = Color.White.copy(alpha = 0.35f)
    val density = LocalDensity.current

    var dragFraction by remember { mutableStateOf<Float?>(null) }
    var trackWidthPx by remember { mutableFloatStateOf(1f) }

    val progress = if (duration > 0) (position().toFloat() / duration).coerceIn(0f, 1f) else 0f
    val fraction = dragFraction ?: progress

    val targetAmp = if (isPlaying && dragFraction == null) 1f else 0f
    val amp by animateFloatAsState(targetAmp, spring(stiffness = Spring.StiffnessLow), label = "waveAmp")
    val phase by rememberInfiniteTransition(label = "wave").animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "wavePhase"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .onSizeChanged { trackWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(duration) {
                    detectTapGestures { offset ->
                        if (duration > 0) {
                            val f = (offset.x / size.width).coerceIn(0f, 1f)
                            onSeek((f * duration).toLong())
                        }
                    }
                }
                .pointerInput(duration) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                        },
                        onHorizontalDrag = { change, _ ->
                            dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            dragFraction?.let { if (duration > 0) onSeek((it * duration).toLong()) }
                            dragFraction = null
                        },
                        onDragCancel = { dragFraction = null }
                    )
                }
        ) {
            val w = size.width
            val midY = size.height / 2f
            val playedX = (w * fraction).coerceIn(0f, w)
            val strokePx = with(density) { 4.dp.toPx() }
            val ampPx = with(density) { 5.dp.toPx() } * amp
            val wavelenPx = with(density) { 16.dp.toPx() }
            val stepPx = with(density) { 2.dp.toPx() }

            // Played portion: wave when animating, otherwise a straight segment.
            val path = Path().apply {
                moveTo(0f, midY)
                if (ampPx < 0.5f) {
                    lineTo(playedX, midY)
                } else {
                    var x = 0f
                    while (x <= playedX) {
                        val angle = (x / wavelenPx).toDouble() * 2.0 * PI + phase
                        lineTo(x, midY + ampPx * sin(angle).toFloat())
                        x += stepPx
                    }
                    val endAngle = (playedX / wavelenPx).toDouble() * 2.0 * PI + phase
                    lineTo(playedX, midY + ampPx * sin(endAngle).toFloat())
                }
            }
            drawPath(path, color = primary, style = Stroke(width = strokePx, cap = StrokeCap.Round))

            // Remaining portion.
            if (playedX < w) {
                drawLine(
                    color = remainingColor,
                    start = Offset(playedX, midY),
                    end = Offset(w, midY),
                    strokeWidth = strokePx,
                    cap = StrokeCap.Round
                )
            }

            // Thumb: vertical pill.
            val thumbW = with(density) { 5.dp.toPx() }
            val thumbH = with(density) { 22.dp.toPx() }
            drawRoundRect(
                color = primary,
                topLeft = Offset(playedX - thumbW / 2f, midY - thumbH / 2f),
                size = Size(thumbW, thumbH),
                cornerRadius = CornerRadius(thumbW / 2f, thumbW / 2f)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val shownPosition = if (dragFraction != null) (fraction * duration).toLong() else position()
            Text(
                text = formatDuration(shownPosition),
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = formatDuration(duration),
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
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

        HorizontalDivider()

        // Audio format details - from ExoPlayer on demand
        if (audioFormat != null && (audioFormat!!.sampleRate != null || audioFormat!!.bitDepth != null ||
                    audioFormat!!.bitrate != null || audioFormat!!.channelCount != null)
        ) {
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
