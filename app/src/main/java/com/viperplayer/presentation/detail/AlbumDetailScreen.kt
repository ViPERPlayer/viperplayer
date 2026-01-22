package com.viperplayer.presentation.detail

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.MediaItem
import com.viperplayer.domain.model.Song
import com.viperplayer.presentation.common.ListItem
import com.viperplayer.presentation.common.MediaItemOptionsBottomSheet
import com.viperplayer.presentation.common.ViperScaffold
import com.viperplayer.presentation.search.model.ItemBadge
import com.viperplayer.presentation.search.model.SearchItem

/**
 * Sealed class to represent items in the album song list (disc headers or songs).
 */
private sealed class DiscItem {
    data class HeaderItem(val discNumber: Int) : DiscItem()
    data class SongItem(val song: Song) : DiscItem()
}

@Composable
fun AlbumDetailScreen(
    rootPadding: PaddingValues,
    onNavigateBack: () -> Unit,
    onNavigateToArtist: (MediaId) -> Unit = {},
    viewModel: AlbumDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    
    var selectedMediaItem by remember { mutableStateOf<MediaItem?>(null) }

    ViperScaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = when (val state = uiState) {
                            is AlbumDetailUiState.Success -> state.album.name
                            else -> "Album"
                        },
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { contentPadding ->
        when (val state = uiState) {
            is AlbumDetailUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                        .padding(rootPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is AlbumDetailUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                        .padding(rootPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Button(onClick = { viewModel.refresh() }) {
                            Text("Retry")
                        }
                    }
                }
            }
            is AlbumDetailUiState.Success -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                    contentPadding = rootPadding
                ) {
                    // Album header
                    item {
                        AlbumHeader(
                            album = state.album,
                            songCount = state.songs.size,
                            onPlayAlbum = { viewModel.playAlbum() },
                            onShuffle = { viewModel.shuffle() },
                            onArtistClick = { artistId ->
                                onNavigateToArtist(artistId)
                            }
                        )
                    }

                    // Songs list
                    if (state.songs.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No songs in this album",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        // Group songs by disc number and check if we have multiple discs
                        val sortedSongs = state.songs.sortedWith(compareBy(
                            { it.discNumber ?: 1 },
                            { it.trackNumber ?: 0 }
                        ))
                        val songsByDisc = sortedSongs.groupBy { it.discNumber ?: 1 }
                        val hasMultipleDiscs = songsByDisc.size > 1
                        
                        if (hasMultipleDiscs) {
                            // Create a list of items (disc headers + songs) for the LazyColumn
                            val items = buildList {
                                songsByDisc.toSortedMap().forEach { (discNumber, songs) ->
                                    add(DiscItem.HeaderItem(discNumber))
                                    songs.forEach { song ->
                                        add(DiscItem.SongItem(song))
                                    }
                                }
                            }
                            
                            items(
                                items = items,
                                key = { item ->
                                    when (item) {
                                        is DiscItem.HeaderItem -> "disc_${item.discNumber}"
                                        is DiscItem.SongItem -> item.song.id.toString()
                                    }
                                }
                            ) { item ->
                                when (item) {
                                    is DiscItem.HeaderItem -> {
                                        DiscHeader(
                                            discNumber = item.discNumber,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                    is DiscItem.SongItem -> {
                                        val song = item.song
                                        ListItem(
                                            type = SearchItem.Type.SONG,
                                            title = song.title,
                                            badges = if (song.isExplicit) listOf(ItemBadge.EXPLICIT) else emptyList(),
                                            subtitle = song.artistNames,
                                            trackNumber = song.trackNumber,
                                            durationMs = song.durationMs,
                                            isActive = currentSong?.id == song.id,
                                            isPlaying = currentSong?.id == song.id && isPlaying,
                                            onClick = { viewModel.playSong(song) },
                                            onMoreClick = { selectedMediaItem = song },
                                            onLongClick = { selectedMediaItem = song },
                                            onPlayNext = { viewModel.playNext(song) },
                                            onAddToQueue = { viewModel.addToQueue(song) },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        } else {
                            // Single disc or no disc numbers - display normally
                            items(
                                items = sortedSongs,
                                key = { song -> song.id.toString() }
                            ) { song ->
                                ListItem(
                                    type = SearchItem.Type.SONG,
                                    title = song.title,
                                    badges = if (song.isExplicit) listOf(ItemBadge.EXPLICIT) else emptyList(),
                                    subtitle = song.artistNames,
                                    trackNumber = song.trackNumber,
                                    durationMs = song.durationMs,
                                    isActive = currentSong?.id == song.id,
                                    isPlaying = currentSong?.id == song.id && isPlaying,
                                    onClick = { viewModel.playSong(song) },
                                    onMoreClick = { selectedMediaItem = song },
                                    onLongClick = { selectedMediaItem = song },
                                    onPlayNext = { viewModel.playNext(song) },
                                    onAddToQueue = { viewModel.addToQueue(song) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // Media item options bottom sheet
        selectedMediaItem?.let { item ->
            ModalBottomSheet(
                onDismissRequest = { selectedMediaItem = null },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                dragHandle = { BottomSheetDefaults.DragHandle() }
            ) {
                MediaItemOptionsBottomSheet(
                    item = item,
                    onDismiss = { selectedMediaItem = null },
                    onPlay = {
                        when (item) {
                            is Song -> viewModel.playSong(item)
                            else -> {}
                        }
                        selectedMediaItem = null
                    },
                    onPlayNext = {
                        // TODO: Implement play next
                        selectedMediaItem = null
                    },
                    onAddToQueue = {
                        // TODO: Implement add to queue
                        selectedMediaItem = null
                    },
                    onLike = {
                        // TODO: Implement toggle like
                        selectedMediaItem = null
                    },
                    onViewArtist = { artistId ->
                        onNavigateToArtist(artistId)
                        selectedMediaItem = null
                    },
                    onViewAlbum = { albumId ->
                        // Already on album detail screen
                        selectedMediaItem = null
                    }
                )
            }
        }
    }
}

@Composable
private fun AlbumHeader(
    album: Album,
    songCount: Int,
    onPlayAlbum: () -> Unit,
    onShuffle: () -> Unit,
    onArtistClick: (MediaId) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Album artwork
        AsyncImage(
            model = album.artworkUrl,
            contentDescription = album.name,
            modifier = Modifier
                .size(200.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )

        // Album name
        Text(
            text = album.name,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        // Artists (clickable)
        if (album.artists.isNotEmpty()) {
            var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

            val annotatedString = remember(album.artists) {
                buildAnnotatedString {
                    album.artists.forEachIndexed { index, artist ->
                        val startIndex = length
                        append(artist.name)
                        val endIndex = length

                        addStringAnnotation(
                            tag = "artist",
                            annotation = index.toString(),
                            start = startIndex,
                            end = endIndex
                        )

                        if (index < album.artists.size - 1) {
                            append(", ")
                        }
                    }
                }
            }
            
            Text(
                text = annotatedString,
                modifier = Modifier
                    .pointerInput(textLayoutResult, annotatedString) {
                        detectTapGestures { position ->
                            textLayoutResult?.let {
                                val offset = it.getOffsetForPosition(position)
                                annotatedString
                                    .getStringAnnotations(
                                        tag = "artist",
                                        start = offset,
                                        end = offset
                                    )
                                    .firstOrNull()
                                    ?.let { annotation ->
                                        val artist = album.artists[annotation.item.toInt()]
                                        onArtistClick(artist.id)
                                    }
                            }
                        }
                    },
                textAlign = TextAlign.Center,
                onTextLayout = { textLayoutResult = it },
                style = MaterialTheme.typography.titleMedium
            )
        }

        // Album info
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            album.releaseYear?.let { year ->
                Text(
                    text = year.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = album.type.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "•",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "$songCount ${if (songCount == 1) "song" else "songs"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Action buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onPlayAlbum,
                modifier = Modifier.weight(1f),
                enabled = songCount > 0
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Play")
            }
            OutlinedButton(
                onClick = onShuffle,
                modifier = Modifier.weight(1f),
                enabled = songCount > 0
            ) {
                Icon(Icons.Default.Shuffle, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Shuffle")
            }
        }
    }
}

@Composable
private fun DiscHeader(
    discNumber: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = "Disc $discNumber",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

