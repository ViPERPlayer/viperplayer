package com.viperplayer.presentation.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.viperplayer.domain.model.Artist
import com.viperplayer.presentation.ktx.bottom
import com.viperplayer.presentation.ktx.with
import com.viperplayer.presentation.search.AlbumItem
import com.viperplayer.presentation.search.SongItem

@Composable
fun ArtistDetailScreen(
    rootPadding: PaddingValues,
    onNavigateBack: () -> Unit,
    onNavigateToAlbum: (String) -> Unit = {},
    viewModel: ArtistDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(rootPadding.with(bottom = 0.dp))
    ) {
        TopAppBar(
            title = { Text(uiState.artist?.name ?: "Artist") },
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

        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(rootPadding.bottom()),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.error != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(rootPadding.bottom()),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = uiState.error!!,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    Button(onClick = { viewModel.refresh() }) {
                        Text("Retry")
                    }
                }
            }
        } else {
            uiState.artist?.let { artist ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(rootPadding.bottom()),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    // Artist header
                    item {
                        ArtistHeader(
                            artist = artist,
                            onPlayAll = { viewModel.playAllSongs() }
                        )
                    }

                    // Tab selector
                    item {
                        PrimaryTabRow(
                            selectedTabIndex = uiState.selectedTab.ordinal
                        ) {
                            Tab(
                                selected = uiState.selectedTab == ArtistTab.SONGS,
                                onClick = { viewModel.selectTab(ArtistTab.SONGS) },
                                text = { Text("Songs") }
                            )
                            Tab(
                                selected = uiState.selectedTab == ArtistTab.ALBUMS,
                                onClick = { viewModel.selectTab(ArtistTab.ALBUMS) },
                                text = { Text("Albums") }
                            )
                        }
                    }

                    // Content based on selected tab
                    when (uiState.selectedTab) {
                        ArtistTab.SONGS -> {
                            if (uiState.topSongs.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "No songs available",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            } else {
                                items(uiState.topSongs) { song ->
                                    SongItem(
                                        song = song,
                                        onClick = { viewModel.playSong(song) }
                                    )
                                }
                            }
                        }
                        ArtistTab.ALBUMS -> {
                            if (uiState.albums.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "No albums available",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            } else {
                                items(uiState.albums) { album ->
                                    AlbumItem(
                                        album = album,
                                        onClick = { onNavigateToAlbum(album.id.toString()) }
                                    )
                                }
                            }
                        }
                    }
                }
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
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
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

        // Genres
        if (artist.genres.isNotEmpty()) {
            Text(
                text = artist.genres.joinToString(" • "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Follower count
        artist.followerCount?.let { count ->
            Text(
                text = "${formatFollowerCount(count)} followers",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Bio
        artist.bio?.let { bio ->
            Text(
                text = bio,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

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

private fun formatFollowerCount(count: Long): String {
    return when {
        count >= 1_000_000 -> "${count / 1_000_000}M"
        count >= 1_000 -> "${count / 1_000}K"
        else -> count.toString()
    }
}
