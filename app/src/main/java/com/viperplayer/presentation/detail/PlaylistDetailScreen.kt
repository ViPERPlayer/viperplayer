package com.viperplayer.presentation.detail

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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.viperplayer.domain.model.MediaItem
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.Song
import com.viperplayer.presentation.common.ListItem
import com.viperplayer.presentation.common.ListItemLeadingArtwork
import com.viperplayer.presentation.common.ListItemTrailingWithDuration
import com.viperplayer.presentation.common.MediaItemOptionsBottomSheet
import com.viperplayer.presentation.common.ViperScaffold
import com.viperplayer.presentation.search.model.ItemBadge
import com.viperplayer.presentation.search.model.SearchItem

@Composable
fun PlaylistDetailScreen(
    rootPadding: PaddingValues,
    onNavigateBack: () -> Unit,
    viewModel: PlaylistDetailViewModel = hiltViewModel()
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
                        when (val state = uiState) {
                            is PlaylistDetailUiState.Success -> state.playlist.name
                            else -> "Playlist"
                        }
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
            is PlaylistDetailUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is PlaylistDetailUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
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
            is PlaylistDetailUiState.Success -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = contentPadding
                ) {
                    // Playlist header
                    item {
                        PlaylistHeader(
                            playlist = state.playlist,
                            songCount = state.songs.size,
                            onPlayAll = { viewModel.playAll() },
                            onShuffle = { viewModel.shuffle() }
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
                                    text = "No songs in this playlist",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(state.songs) { song ->
                            ListItem(
                                title = song.title,
                                badges = if (song.isExplicit) listOf(ItemBadge.EXPLICIT) else emptyList(),
                                subtitle = song.artistNames,
                                isActive = currentSong?.id == song.id,
                                leadingContent = {
                                    ListItemLeadingArtwork(
                                        artworkUrl = song.artworkUrl,
                                        type = SearchItem.Type.SONG,
                                        isActive = currentSong?.id == song.id,
                                        isPlaying = currentSong?.id == song.id && isPlaying
                                    )
                                },
                                trailingContent = {
                                    ListItemTrailingWithDuration(
                                        durationMs = song.durationMs,
                                        onMoreClick = { selectedMediaItem = song }
                                    )
                                },
                                onClick = if (song.isPlayable) { { viewModel.playSong(song) } } else null,
                                onLongClick = { selectedMediaItem = song },
                                onPlayNext = if (song.isPlayable) { { viewModel.playNext(song) } } else null,
                                onAddToQueue = if (song.isPlayable) { { viewModel.addToQueue(song) } } else null,
                                modifier = Modifier
                                    .animateItem()
                                    .fillMaxWidth()
                                    .then(
                                        if (!song.isPlayable) {
                                            Modifier.alpha(0.5f)
                                        } else {
                                            Modifier
                                        }
                                    )
                            )
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
                        // TODO: Navigate to artist
                        selectedMediaItem = null
                    },
                    onViewAlbum = { albumId ->
                        // TODO: Navigate to album
                        selectedMediaItem = null
                    }
                )
            }
        }
    }
}

@Composable
private fun PlaylistHeader(
    playlist: Playlist,
    songCount: Int,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Playlist artwork
        AsyncImage(
            model = playlist.artworkUrl,
            contentDescription = playlist.name,
            modifier = Modifier
                .size(200.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )

        // Playlist name
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        // Description
        playlist.description?.let { description ->
            if (description.isNotBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 3
                )
            }
        }

        // Owner and song count
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            playlist.ownerName?.let { owner ->
                Text(
                    text = owner,
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
                onClick = onPlayAll,
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






