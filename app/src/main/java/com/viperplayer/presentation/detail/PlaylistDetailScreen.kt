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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.viperplayer.R
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.MediaItem
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.Song
import com.viperplayer.presentation.common.CollapsingArtworkScaffold
import com.viperplayer.presentation.common.ListItem
import com.viperplayer.presentation.common.ListItemLeadingArtwork
import com.viperplayer.presentation.common.ListItemTrailingWithDuration
import com.viperplayer.presentation.common.MediaItemOptionsSheetHost
import com.viperplayer.presentation.common.rememberMediaItemOptionsController
import com.viperplayer.presentation.search.model.ItemBadge
import com.viperplayer.presentation.search.model.SearchItem
import com.viperplayer.presentation.theme.ViPERPlayerTheme

@Composable
fun PlaylistDetailScreen(
    rootPadding: PaddingValues,
    onNavigateBack: () -> Unit,
    onNavigateToArtist: (Artist) -> Unit,
    onNavigateToAlbum: (Album) -> Unit,
    viewModel: PlaylistDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()

    PlaylistDetailScreenContent(
        rootPadding = rootPadding,
        uiState = uiState,
        currentSong = currentSong,
        isPlaying = isPlaying,
        onNavigateBack = onNavigateBack,
        onRefresh = viewModel::refresh,
        onPlayAll = viewModel::playAll,
        onShuffle = viewModel::shuffle,
        onPlaySong = viewModel::playSong,
        onPlayNext = viewModel::playNext,
        onAddToQueue = viewModel::addToQueue,
        onToggleLike = { /* TODO: Implement toggle like */ },
        onNavigateToArtist = onNavigateToArtist,
        onNavigateToAlbum = onNavigateToAlbum,
    )
}

@Composable
private fun PlaylistDetailScreenContent(
    rootPadding: PaddingValues,
    uiState: PlaylistDetailUiState,
    currentSong: Song?,
    isPlaying: Boolean,
    onNavigateBack: () -> Unit,
    onRefresh: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onPlaySong: (Song) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onToggleLike: (Song) -> Unit,
    onNavigateToArtist: (Artist) -> Unit,
    onNavigateToAlbum: (Album) -> Unit,
) {
    val optionsController = rememberMediaItemOptionsController()

    val playlist = when (uiState) {
        is PlaylistDetailUiState.Success -> uiState.playlist
        is PlaylistDetailUiState.Loading -> uiState.initialPlaylist
        else -> null
    }
    val title = playlist?.name ?: "Playlist"

    CollapsingArtworkScaffold(
        artworkUrl = playlist?.artworkUrl,
        title = title,
        onNavigateBack = onNavigateBack,
        actions = {
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_refresh))
            }
        },
    ) { contentPadding ->
        when (uiState) {
            is PlaylistDetailUiState.Loading -> {
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

            is PlaylistDetailUiState.Error -> {
                com.viperplayer.presentation.common.ErrorState(
                    message = uiState.message,
                    onRetry = onRefresh,
                    modifier = Modifier
                        .padding(contentPadding)
                        .padding(rootPadding),
                )
            }

            is PlaylistDetailUiState.Success -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                    contentPadding = rootPadding
                ) {
                    // Playlist metadata header (no artwork — artwork is in the collapsing app bar)
                    item {
                        PlaylistMetadataHeader(
                            playlist = uiState.playlist,
                            songCount = uiState.songs.size,
                            onPlayAll = onPlayAll,
                            onShuffle = onShuffle
                        )
                    }

                    // Songs list
                    if (uiState.songs.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.playlist_no_songs),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        itemsIndexed(uiState.songs, key = { index, song -> "${song.id}-$index" }) { _, song ->
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
                                        onMoreClick = { optionsController.show(song) }
                                    )
                                },
                                onClick = if (song.isPlayable) {
                                    { onPlaySong(song) }
                                } else null,
                                onLongClick = { optionsController.show(song) },
                                onPlayNext = if (song.isPlayable) {
                                    { onPlayNext(song) }
                                } else null,
                                onAddToQueue = if (song.isPlayable) {
                                    { onAddToQueue(song) }
                                } else null,
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
        MediaItemOptionsSheetHost(
            controller = optionsController,
            onPlay = { if (it is Song) onPlaySong(it) },
            onPlayNext = { if (it is Song) onPlayNext(it) },
            onAddToQueue = { if (it is Song) onAddToQueue(it) },
            onLike = { if (it is Song) onToggleLike(it) },
            onViewArtist = onNavigateToArtist,
            onViewAlbum = onNavigateToAlbum,
        )
    }
}

/**
 * Metadata header for the playlist (below the collapsing app bar).
 * Shows description, owner/song count, and action buttons. Left-aligned.
 */
@Composable
private fun PlaylistMetadataHeader(
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
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
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
                text = pluralStringResource(R.plurals.song_count, songCount, songCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onPlayAll,
                modifier = Modifier.weight(1f),
                enabled = songCount > 0
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.action_play))
            }
            OutlinedButton(
                onClick = onShuffle,
                modifier = Modifier.weight(1f),
                enabled = songCount > 0
            ) {
                Icon(Icons.Default.Shuffle, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.action_shuffle))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PlaylistDetailScreenPreview() {
    val playlist = Playlist(
        id = MediaId("plugin", "playlist1"),
        name = "Rock Classics",
        ownerName = "ViPER Player",
        artworkUrl = "https://raw.githubusercontent.com/coil-kt/coil/master/logo.png",
        description = "The best rock songs of all time."
    )

    val songs = listOf(
        Song(
            id = MediaId("plugin", "song1"),
            title = "Bohemian Rhapsody",
            artists = emptyList(), // Simplified for preview
            album = null,
            durationMs = 354000,
            trackNumber = 1,
            discNumber = 1
        ),
        Song(
            id = MediaId("plugin", "song2"),
            title = "Stairway to Heaven",
            artists = emptyList(),
            album = null,
            durationMs = 482000,
            trackNumber = 2,
            discNumber = 1,
            isExplicit = true
        )
    )

    ViPERPlayerTheme {
        PlaylistDetailScreenContent(
            rootPadding = PaddingValues(0.dp),
            uiState = PlaylistDetailUiState.Success(playlist, songs),
            currentSong = songs[0],
            isPlaying = false,
            onNavigateBack = {},
            onRefresh = {},
            onPlayAll = {},
            onShuffle = {},
            onPlaySong = {},
            onPlayNext = {},
            onAddToQueue = {},
            onToggleLike = {},
            onNavigateToArtist = {},
            onNavigateToAlbum = {},
        )
    }
}
