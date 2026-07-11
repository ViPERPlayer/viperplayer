package com.viperplayer.presentation.player

import android.content.Context
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.foundation.interaction.DragInteraction
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
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.viperplayer.R
import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.PlaybackContext
import com.viperplayer.domain.model.isNavigable
import com.viperplayer.domain.model.navigableAlbum
import com.viperplayer.domain.model.navigableArtist
import com.viperplayer.domain.model.toEntity
import com.viperplayer.domain.model.RepeatMode
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.repository.AudioFormat
import com.viperplayer.presentation.common.ListItem
import com.viperplayer.presentation.ktx.formatDuration
import com.viperplayer.presentation.ktx.infiniteBasicMarquee
import com.viperplayer.presentation.search.model.SearchItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.sin

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
    onNavigateToSongInfo: (Song) -> Unit = {},
    onNavigateToJoinSession: () -> Unit = {},
    onCollapse: () -> Unit = {},
    contentWindowInsets: WindowInsets = BottomSheetDefaults.windowInsets,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    val queue by viewModel.queue.collectAsStateWithLifecycle()

    // Poll position. Reset to 0 only when the song actually changes; while on the same song, ignore
    // transient 0 readings (the media controller briefly reports 0 as it re-syncs when the app returns
    // to the foreground) so the position doesn't visibly jump back to 0:00.
    var currentPosition by remember { mutableLongStateOf(0L) }
    LaunchedEffect(currentSong?.id) {
        currentPosition = 0L
        while (isActive) {
            val p = viewModel.getCurrentPosition()
            if (p > 0L) currentPosition = p
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
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val addedToLibraryMessage = stringResource(R.string.toast_added_to_library)
    val comingSoonMessage = stringResource(R.string.toast_coming_soon)
    val sleepTimerMinutes by viewModel.sleepTimerMinutes.collectAsStateWithLifecycle()

    val song = currentSong
    if (song == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.player_no_song),
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
    // currentSong, queue and pager position are three independent flows that settle at different
    // times; read the index live so the settle handler below never compares against a stale value.
    val liveCurrentIndex = rememberUpdatedState(currentIndex)
    val pagerState = rememberPagerState(initialPage = currentIndex) { pagerSongs.size }
    // External track change (skip buttons / auto-advance) -> move the pager to match. Instant, not
    // animated: on open the queue arrives after the current song, and animating across the loading
    // gap looked like the player was flipping through tracks.
    LaunchedEffect(currentIndex) {
        if (currentIndex != pagerState.currentPage && !pagerState.isScrollInProgress) {
            pagerState.scrollToPage(currentIndex)
        }
    }
    // Change the track ONLY on a genuine user swipe. Playback must be driven by intent, not by page
    // position — a programmatic scroll (above) and a queue re-emission both move settledPage without
    // any drag, and reacting to those caused random song changes when the player opened.
    LaunchedEffect(pagerState) {
        var userDragged = false
        launch {
            pagerState.interactionSource.interactions.collect { interaction ->
                if (interaction is DragInteraction.Start) userDragged = true
            }
        }
        snapshotFlow { pagerState.isScrollInProgress }
            .filter { scrolling -> !scrolling } // wait until the pager has settled
            .collect {
                if (!userDragged) return@collect
                userDragged = false
                val page = pagerState.currentPage
                if (page != liveCurrentIndex.value && page in pagerSongs.indices) {
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
                        contentDescription = stringResource(R.string.player_collapse),
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                ContextChip(context = playbackState.playbackContext)

                Box {
                    IconButton(onClick = { showOverflowMenu = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.player_more_options),
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false },
                        shape = RoundedCornerShape(22.dp),
                    ) {
                        song.navigableArtist()?.toEntity()?.let { artist ->
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_view_artist)) },
                                leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    onNavigateToArtist(artist)
                                }
                            )
                        }
                        song.navigableAlbum()?.let { album ->
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.player_go_to_album)) },
                                leadingIcon = { Icon(Icons.Filled.Album, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    onNavigateToAlbum(album)
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.song_info_title)) },
                            leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                onNavigateToSongInfo(song)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_song_radio)) },
                            leadingIcon = { Icon(Icons.Filled.Sensors, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                viewModel.startSongRadio()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_add_to_library)) },
                            leadingIcon = { Icon(Icons.Filled.LibraryAdd, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                viewModel.addCurrentSongToLibrary()
                                Toast.makeText(context, addedToLibraryMessage, Toast.LENGTH_SHORT).show()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_add_to_playlist)) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                // No add-to-existing-playlist backend yet — mocked, prepared for wiring.
                                Toast.makeText(context, comingSoonMessage, Toast.LENGTH_SHORT).show()
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_sleep_timer)) },
                            leadingIcon = { Icon(Icons.Filled.Bedtime, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                showSleepTimerDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_share)) },
                            leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                shareSong(context, song)
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
                            ArtistAlbumSubtitle(
                                song = current,
                                onArtistClick = onNavigateToArtist,
                                modifier = Modifier.padding(top = 5.dp)
                            )
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
                        contentDescription = stringResource(R.string.action_previous),
                        onClick = { viewModel.skipToPrevious() }
                    )
                    MorphPlayButton(
                        isPlaying = isPlaying,
                        onClick = { viewModel.togglePlayPause() }
                    )
                    SkipPill(
                        icon = Icons.Filled.SkipNext,
                        contentDescription = stringResource(R.string.action_next),
                        onClick = { viewModel.skipToNext() }
                    )
                    ToggleIconButton(
                        icon = if (playbackState.repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        contentDescription = when (playbackState.repeatMode) {
                            RepeatMode.OFF -> stringResource(R.string.player_repeat_off)
                            RepeatMode.ONE -> stringResource(R.string.player_repeat_one)
                            RepeatMode.ALL -> stringResource(R.string.player_repeat_all)
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
                                text = stringResource(R.string.player_output_this_phone),
                                color = Color.White,
                                fontSize = 15.sp,
                                lineHeight = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = stringResource(R.string.player_output_device),
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
                            contentDescription = stringResource(R.string.action_queue),
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
        onShowQr = { showQr = it },
        onJoinSession = {
            showListenTogether = false
            onNavigateToJoinSession()
        },
    )

    if (showSleepTimerDialog) {
        SleepTimerDialog(
            activeMinutes = sleepTimerMinutes,
            onSelect = {
                viewModel.setSleepTimer(it)
                showSleepTimerDialog = false
            },
            onDismiss = { showSleepTimerDialog = false },
        )
    }
}

/**
 * The artist(s) + album subtitle under the title. Each artist name is its own tap target that
 * navigates to that specific artist; artists with no navigable target (null id) render as plain,
 * inert text. Navigable artists are not tinted — a subtle weight is their only affordance, matching
 * the rest of the subtitle color. The album name (when present) is appended after a " · " separator
 * as a non-interactive suffix. Wraps to multiple lines when the byline is long.
 */
@Composable
private fun ArtistAlbumSubtitle(
    song: Song,
    onArtistClick: (Artist) -> Unit,
    modifier: Modifier = Modifier
) {
    val artists = song.artists
    val albumName = song.album?.name
    if (artists.isEmpty() && albumName.isNullOrEmpty()) return

    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    // Build the line once per song, tagging each navigable artist span with its index so a tap can be
    // mapped back to the artist under the finger.
    val annotated = remember(artists, albumName) {
        buildAnnotatedString {
            artists.forEachIndexed { index, artist ->
                if (index > 0) append(", ")
                if (artist.isNavigable()) {
                    pushStringAnnotation(tag = ARTIST_TAG, annotation = index.toString())
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                        append(artist.name)
                    }
                    pop()
                } else {
                    append(artist.name)
                }
            }
            if (!albumName.isNullOrEmpty()) {
                if (artists.isNotEmpty()) append(" · ")
                append(albumName)
            }
        }
    }

    val hasNavigableArtist = artists.any { it.isNavigable() }
    val tapModifier = if (hasNavigableArtist) {
        Modifier.pointerInput(annotated) {
            detectTapGestures { position ->
                val layout = textLayoutResult ?: return@detectTapGestures
                val offset = layout.getOffsetForPosition(position)
                annotated.getStringAnnotations(ARTIST_TAG, offset, offset)
                    .firstOrNull()
                    ?.let { artists[it.item.toInt()].toEntity()?.let(onArtistClick) }
            }
        }
    } else {
        Modifier
    }

    Text(
        text = annotated,
        color = Color.White.copy(alpha = 0.82f),
        fontSize = 17.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { textLayoutResult = it },
        modifier = modifier.then(tapModifier)
    )
}

private const val ARTIST_TAG = "artist"

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
        is PlaybackContext.Search ->
            Icons.Filled.Search to context.query.ifBlank { stringResource(R.string.player_context_search) }
        PlaybackContext.Suggestions ->
            Icons.Filled.AutoAwesome to stringResource(R.string.player_context_suggestions)
        null -> Icons.Filled.MusicNote to stringResource(R.string.player_context_now_playing)
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
                text = formatDuration(shownPosition, placeholder = null),
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = formatDuration(duration, placeholder = null),
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
            text = stringResource(R.string.song_details),
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
                title = stringResource(R.string.song_detail_audio_format),
                icon = Icons.Filled.MusicNote
            ) {
                audioFormat!!.sampleRate?.let { sampleRate ->
                    DetailRow(
                        label = stringResource(R.string.song_detail_sample_rate),
                        value = "${sampleRate / 1000} kHz"
                    )
                }
                audioFormat!!.bitDepth?.let { bitDepth ->
                    DetailRow(
                        label = stringResource(R.string.song_detail_bit_depth),
                        value = "$bitDepth bit"
                    )
                }
                audioFormat!!.bitrate?.let { bitrate ->
                    DetailRow(
                        label = stringResource(R.string.song_detail_bitrate),
                        value = "$bitrate kbps"
                    )
                }
                audioFormat!!.channelCount?.let { channels ->
                    DetailRow(
                        label = stringResource(R.string.song_detail_channels),
                        value = when (channels) {
                            1 -> stringResource(R.string.channels_mono)
                            2 -> stringResource(R.string.channels_stereo)
                            else -> stringResource(R.string.song_detail_channel_count, channels)
                        }
                    )
                }
            }
        }

        // ReplayGain details
        if (song.replayGainDb != null || song.peakAmplitude != null) {
            DetailSection(
                title = stringResource(R.string.song_detail_audio_normalization),
                icon = Icons.Filled.GraphicEq
            ) {
                song.replayGainDb?.let { replayGain ->
                    DetailRow(
                        label = stringResource(R.string.song_detail_replaygain),
                        value = String.format("%.2f dB", replayGain)
                    )
                }
                song.peakAmplitude?.let { peak ->
                    DetailRow(
                        label = stringResource(R.string.song_detail_peak_amplitude),
                        value = String.format("%.4f", peak)
                    )
                }
            }
        }

        // Track info
        DetailSection(
            title = stringResource(R.string.song_detail_track_information),
            icon = Icons.Filled.Info
        ) {
            song.durationMs?.let { duration ->
                val minutes = TimeUnit.MILLISECONDS.toMinutes(duration)
                val seconds = TimeUnit.MILLISECONDS.toSeconds(duration) % 60
                DetailRow(
                    label = stringResource(R.string.song_detail_duration),
                    value = "${minutes}m ${seconds}s"
                )
            }
            song.trackNumber?.let { track ->
                DetailRow(
                    label = stringResource(R.string.song_detail_track_number),
                    value = track.toString()
                )
            }
            song.discNumber?.let { disc ->
                DetailRow(
                    label = stringResource(R.string.song_detail_disc_number),
                    value = disc.toString()
                )
            }
            song.album?.let { album ->
                DetailRow(
                    label = stringResource(R.string.song_detail_album),
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

/** Sleep-timer picker: choose a duration after which playback pauses (or turn it off). */
@Composable
private fun SleepTimerDialog(
    activeMinutes: Int?,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf(0, 15, 30, 45, 60)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_sleep_timer)) },
        text = {
            Column {
                options.forEach { minutes ->
                    val label = if (minutes == 0) {
                        stringResource(R.string.sleep_timer_off)
                    } else {
                        stringResource(R.string.sleep_timer_minutes, minutes)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(minutes) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = (activeMinutes ?: 0) == minutes,
                            onClick = { onSelect(minutes) },
                        )
                        Text(label, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

/** Fire the Android system share sheet for a track. */
private fun shareSong(context: Context, song: Song) {
    val text = buildString {
        append(song.title)
        song.artistNames?.let { append(" — $it") }
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.action_share)))
}
