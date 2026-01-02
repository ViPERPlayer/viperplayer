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
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.viperplayer.domain.model.MediaId
import com.viperplayer.presentation.ktx.bottom
import com.viperplayer.presentation.ktx.with

@Composable
fun LibraryScreen(
    rootPadding: PaddingValues,
    onNavigateToAlbum: (MediaId) -> Unit = {},
    onNavigateToArtist: (MediaId) -> Unit = {},
    onNavigateToPlaylist: (MediaId) -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    val tabs = listOf("Songs", "Albums", "Artists", "Playlists")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(rootPadding.with(bottom = 0.dp))
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
//                                SongItem(
//                                    song = song,
//                                    onClick = { viewModel.playSong(song) }
//                                )
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
//                                AlbumItem(
//                                    album = album,
//                                    onClick = { onNavigateToAlbum(album.id.toString()) }
//                                )
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
//                                ArtistItem(
//                                    artist = artist,
//                                    onClick = { onNavigateToArtist(artist.id.toString()) }
//                                )
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
//                                PlaylistItem(
//                                    playlist = playlist,
//                                    onClick = { onNavigateToPlaylist(playlist.id.toString()) }
//                                )
                            }
                        }
                    }
                }
            }
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

