package com.viperplayer.presentation.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.MediaId
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
fun ArtistDetailScreen(
    rootPadding: PaddingValues,
    onNavigateBack: () -> Unit,
    onNavigateToAlbum: (MediaId) -> Unit = {},
    onNavigateToPlaylist: (MediaId) -> Unit = {},
    onNavigateToArtist: (MediaId) -> Unit = {},
    viewModel: ArtistDetailViewModel = hiltViewModel()
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
                            is ArtistDetailUiState.Success -> state.artist.name
                            else -> "Artist"
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
            is ArtistDetailUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                        .padding(rootPadding),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator()
                }
            }
            is ArtistDetailUiState.Error -> {
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
            is ArtistDetailUiState.Success -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                    contentPadding = rootPadding
                ) {
                    val artist = state.artist
                    
                    // Artist header
                    item {
                        ArtistHeader(
                            artist = artist,
                            onPlayAll = { viewModel.playAllSongs() }
                        )
                    }
                    
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Top Songs section
                    if (artist.topSongs.isNotEmpty()) {
                        item {
                            SectionHeader("Top Songs")
                        }
                        items(
                            items = artist.topSongs,
                            key = { song -> song.id.toString() }
                        ) { song ->
                            ListItem(
                                title = song.title,
                                badges = if (song.isExplicit) listOf(ItemBadge.EXPLICIT) else emptyList(),
                                subtitle = song.artistNames,
                                isActive = currentSong?.id == song.id,
                                leadingContent = {
                                    ListItemLeadingArtwork(
                                        artworkUrl = song.artworkUrl ?: song.album?.artworkUrl,
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
                                onClick = { viewModel.playSong(song) },
                                onLongClick = { selectedMediaItem = song },
                                onPlayNext = { viewModel.playNext(song) },
                                onAddToQueue = { viewModel.addToQueue(song) },
                                modifier = Modifier
                                    .fillMaxWidth()
                            )
                        }
                    }

                    // Albums section
                    if (artist.albums.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        item {
                            SectionHeader("Albums")
                        }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(
                                    items = artist.albums,
                                    key = { album -> album.id.toString() }
                                ) { album ->
                                    AlbumCard(
                                        album = album,
                                        onClick = { onNavigateToAlbum(album.id) }
                                    )
                                }
                            }
                        }
                    }

                    // Playlists section
                    if (artist.playlists.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                        item {
                            SectionHeader("Playlists")
                        }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(
                                    items = artist.playlists,
                                    key = { playlist -> playlist.id.toString() }
                                ) { playlist ->
                                    PlaylistCard(
                                        playlist = playlist,
                                        onClick = { onNavigateToPlaylist(playlist.id) }
                                    )
                                }
                            }
                        }
                    }

                    // Featuring section
                    if (artist.featuring.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                        item {
                            SectionHeader("Featuring")
                        }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(
                                    items = artist.featuring,
                                    key = { playlist -> playlist.id.toString() }
                                ) { playlist ->
                                    PlaylistCard(
                                        playlist = playlist,
                                        onClick = { onNavigateToPlaylist(playlist.id) }
                                    )
                                }
                            }
                        }
                    }

                    // Appears On section
                    if (artist.appearsOn.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                        item {
                            SectionHeader("Appears On")
                        }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(
                                    items = artist.appearsOn.filter { it is Album || it is Playlist },
                                    key = { item -> item.id.toString() }
                                ) { item ->
                                    when (item) {
                                        is Album -> {
                                            AlbumCard(
                                                album = item,
                                                onClick = { onNavigateToAlbum(item.id) }
                                            )
                                        }
                                        is Playlist -> {
                                            PlaylistCard(
                                                playlist = item,
                                                onClick = { onNavigateToPlaylist(item.id) }
                                            )
                                        }
                                        else -> {}
                                    }
                                }
                            }
                        }
                    }

                    // Similar Artists section
                    if (artist.similarArtists.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                        item {
                            SectionHeader("Similar Artists")
                        }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(
                                    items = artist.similarArtists,
                                    key = { similarArtist -> similarArtist.id.toString() }
                                ) { similarArtist ->
                                    ArtistCard(
                                        artist = similarArtist,
                                        onClick = { onNavigateToArtist(similarArtist.id) }
                                    )
                                }
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
                        // Already on artist detail screen
                        selectedMediaItem = null
                    },
                    onViewAlbum = { albumId ->
                        onNavigateToAlbum(albumId)
                        selectedMediaItem = null
                    }
                )
            }
        }
    }
}

@Composable
private fun ArtistHeader(
    artist: Artist,
    onPlayAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Artist image
        AsyncImage(
            model = artist.imageUrl,
            contentDescription = artist.name,
            modifier = Modifier
                .size(200.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )

        // Artist name
        Text(
            text = artist.name,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )

        // Play all button
        Button(
            onClick = onPlayAll,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Play All")
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun AlbumCard(
    album: Album,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(140.dp)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = album.artworkUrl,
            contentDescription = album.name,
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = album.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = album.artists.joinToString { it.name }.takeIf { it.isNotEmpty() } ?: "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PlaylistCard(
    playlist: Playlist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(140.dp)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = playlist.artworkUrl,
            contentDescription = playlist.name,
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (playlist.ownerName != null) {
            Text(
                text = playlist.ownerName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ArtistCard(
    artist: Artist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(140.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = artist.imageUrl,
            contentDescription = artist.name,
            modifier = Modifier
                .size(140.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

