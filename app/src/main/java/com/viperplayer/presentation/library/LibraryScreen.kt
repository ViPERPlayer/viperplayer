package com.viperplayer.presentation.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.MediaItem
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.Song
import com.viperplayer.presentation.common.ListItem
import com.viperplayer.presentation.common.MediaItemOptionsBottomSheet
import com.viperplayer.presentation.ktx.bottom
import com.viperplayer.presentation.ktx.with
import com.viperplayer.presentation.search.model.ItemBadge
import com.viperplayer.presentation.search.model.SearchItem

@Composable
fun LibraryScreen(
    rootPadding: PaddingValues,
    onNavigateToAlbum: (MediaId) -> Unit = {},
    onNavigateToArtist: (MediaId) -> Unit = {},
    onNavigateToPlaylist: (MediaId) -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    
    var selectedMediaItem by remember { mutableStateOf<MediaItem?>(null) }
    
    val tabs = listOf("Songs", "Albums", "Artists", "Playlists")

    Scaffold(
        modifier = Modifier.padding(rootPadding.with(bottom = 0.dp))
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Library",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )

                IconButton(onClick = { viewModel.refresh() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
            }

            // Tab row
            PrimaryTabRow(
                selectedTabIndex = uiState.selectedTab.ordinal
            ) {
                LibraryTab.entries.forEachIndexed { index, tab ->
                    Tab(
                        selected = uiState.selectedTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = { Text(tabs[index]) }
                    )
                }
            }

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .padding(rootPadding.bottom())
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                when (uiState.selectedTab) {
                    LibraryTab.SONGS -> {
                        if (uiState.songs.isEmpty()) {
                            EmptyLibraryContent("No songs in your library")
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = rootPadding.bottom()
                            ) {
                                items(uiState.songs) { song ->
                                    ListItem(
                                        type = SearchItem.Type.SONG,
                                        title = song.title,
                                        badges = if (song.isExplicit) listOf(ItemBadge.EXPLICIT) else emptyList(),
                                        subtitle = song.artistNames,
                                        artworkUrl = song.artworkUrl,
                                        isActive = currentSong?.id == song.id,
                                        isPlaying = currentSong?.id == song.id && isPlaying,
                                        onClick = if (song.isPlayable) { { viewModel.playSong(song) } } else null,
                                        onMoreClick = { selectedMediaItem = song },
                                        onLongClick = { selectedMediaItem = song },
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
                    LibraryTab.ALBUMS -> {
                        if (uiState.albums.isEmpty()) {
                            EmptyLibraryContent("No albums in your library")
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = rootPadding.bottom()
                            ) {
                                items(uiState.albums) { album ->
                                    ListItem(
                                        type = SearchItem.Type.ALBUM,
                                        title = album.name,
                                        badges = emptyList(),
                                        subtitle = album.artists.joinToString { it.name }.takeIf { it.isNotEmpty() },
                                        artworkUrl = album.artworkUrl,
                                        isActive = false,
                                        isPlaying = false,
                                        onClick = { onNavigateToAlbum(album.id) },
                                        onMoreClick = { selectedMediaItem = album },
                                        onLongClick = { selectedMediaItem = album },
                                        modifier = Modifier
                                            .animateItem()
                                            .fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                    LibraryTab.ARTISTS -> {
                        if (uiState.artists.isEmpty()) {
                            EmptyLibraryContent("No artists in your library")
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = rootPadding.bottom()
                            ) {
                                items(uiState.artists) { artist ->
                                    ListItem(
                                        type = SearchItem.Type.ARTIST,
                                        title = artist.name,
                                        badges = emptyList(),
                                        subtitle = null,
                                        artworkUrl = artist.imageUrl,
                                        isActive = false,
                                        isPlaying = false,
                                        onClick = { onNavigateToArtist(artist.id) },
                                        onMoreClick = { selectedMediaItem = artist },
                                        onLongClick = { selectedMediaItem = artist },
                                        modifier = Modifier
                                            .animateItem()
                                            .fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                    LibraryTab.PLAYLISTS -> {
                        if (uiState.playlists.isEmpty()) {
                            EmptyLibraryContent("No playlists in your library")
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = rootPadding.bottom()
                            ) {
                                items(uiState.playlists) { playlist ->
                                    ListItem(
                                        type = SearchItem.Type.PLAYLIST,
                                        title = playlist.name,
                                        badges = emptyList(),
                                        subtitle = playlist.ownerName?.let { owner ->
                                            "$owner • ${playlist.songCount} ${if (playlist.songCount == 1) "song" else "songs"}"
                                        } ?: "${playlist.songCount} ${if (playlist.songCount == 1) "song" else "songs"}",
                                        artworkUrl = playlist.artworkUrl,
                                        isActive = false,
                                        isPlaying = false,
                                        onClick = { onNavigateToPlaylist(playlist.id) },
                                        onMoreClick = { selectedMediaItem = playlist },
                                        onLongClick = { selectedMediaItem = playlist },
                                        modifier = Modifier
                                            .animateItem()
                                            .fillMaxWidth()
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
            MediaItemOptionsBottomSheet(
                item = item,
                onDismiss = { selectedMediaItem = null },
                    onPlay = {
                        when (item) {
                            is Song -> viewModel.playSong(item)
                            is Album -> viewModel.playAlbum(item)
                            is Playlist -> viewModel.playPlaylist(item)
                            else -> {}
                        }
                        selectedMediaItem = null
                    },
                    onPlayNext = {
                        when (item) {
                            is Song -> viewModel.playNext(item)
                            is Playlist -> viewModel.playPlaylistNext(item)
                            else -> {}
                        }
                        selectedMediaItem = null
                    },
                    onAddToQueue = {
                        when (item) {
                            is Song -> viewModel.addToQueue(item)
                            is Album -> viewModel.addAlbumToQueue(item)
                            is Playlist -> viewModel.addPlaylistToQueue(item)
                            else -> {}
                        }
                        selectedMediaItem = null
                    },
                    onShuffle = {
                        when (item) {
                            is Album -> viewModel.shuffleAlbum(item)
                            is Playlist -> viewModel.shufflePlaylist(item)
                            else -> {}
                        }
                        selectedMediaItem = null
                    },
                    onStartRadio = {
                        // TODO: Implement start radio
                        selectedMediaItem = null
                    },
                    onLike = {
                        when (item) {
                            is Song -> viewModel.toggleLike(item)
                            is Playlist -> viewModel.togglePlaylistLike(item)
                            else -> {}
                        }
                        selectedMediaItem = null
                    },
                    onDownload = {
                        when (item) {
                            is Song -> viewModel.downloadSong(item)
                            else -> {}
                        }
                        selectedMediaItem = null
                    },
                onShare = {
                    // TODO: Implement share
                    selectedMediaItem = null
                },
                onViewArtist = { artistId ->
                    onNavigateToArtist(artistId)
                    selectedMediaItem = null
                },
                onViewAlbum = { albumId ->
                    onNavigateToAlbum(albumId)
                    selectedMediaItem = null
                },
                onViewDetails = {
                    // TODO: Show details (plugin name, etc.)
                    selectedMediaItem = null
                }
            )
        }
    }
}

@Composable
// TODO: hoist modifier for root padding
fun EmptyLibraryContent(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.LibraryMusic,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

