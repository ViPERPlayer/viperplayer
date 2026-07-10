package com.viperplayer.presentation.common

import androidx.compose.foundation.clickable
import androidx.compose.ui.res.stringResource
import com.viperplayer.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.MediaItem
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.model.navigableAlbum
import com.viperplayer.domain.model.navigableArtist
import com.viperplayer.presentation.player.PlayerViewModel

/**
 * Material 3 expressive bottom sheet showing options for a media item.
 */
@Composable
fun MediaItemOptionsBottomSheet(
    item: MediaItem,
    onDismiss: () -> Unit,
    onPlay: () -> Unit = {},
    onPlayNext: () -> Unit = {},
    onAddToQueue: () -> Unit = {},
    onAddToPlaylist: () -> Unit = {},
    onShuffle: () -> Unit = {},
    onStartRadio: () -> Unit = {},
    onLike: () -> Unit = {},
    onDownload: () -> Unit = {},
    onShare: () -> Unit = {},
    onViewArtist: (Artist) -> Unit = {},
    onViewAlbum: (Album) -> Unit = {},
    onViewDetails: () -> Unit = {},
    playerViewModel: PlayerViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val currentSong by playerViewModel.currentSong.collectAsStateWithLifecycle()
    val isLiked by playerViewModel.isLiked.collectAsStateWithLifecycle()

    // Content only: the caller owns the ModalBottomSheet, so this can't nest a second sheet.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
            // Header
            when (item) {
                is Song -> {
                    SongHeader(
                        song = item,
                        // Live state for the playing song; the item's own stored flag otherwise
                        // (a non-playing track was always shown unliked).
                        isLiked = if (item.id == currentSong?.id) isLiked else item.isLiked,
                        onLike = onLike
                    )
                }

                is Album -> {
                    AlbumHeader(album = item)
                }

                is Artist -> {
                    ArtistHeader(artist = item)
                }

                is Playlist -> {
                    PlaylistHeader(playlist = item)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Actions
            when (item) {
                is Song -> {
                    SongActions(
                        song = item,
                        onPlayNext = onPlayNext,
                        onAddToQueue = onAddToQueue,
                        onAddToPlaylist = onAddToPlaylist,
                        onStartRadio = onStartRadio,
                        onDownload = onDownload,
                        onShare = onShare,
                        onViewArtist = item.navigableArtist()?.let { artist -> { onViewArtist(artist) } },
                        onViewAlbum = item.navigableAlbum()?.let { album -> { onViewAlbum(album) } },
                        onViewDetails = onViewDetails
                    )
                }

                is Album -> {
                    AlbumActions(
                        album = item,
                        onPlay = onPlay,
                        onShuffle = onShuffle,
                        onStartRadio = onStartRadio,
                        onAddToQueue = onAddToQueue,
                        onAddToPlaylist = onAddToPlaylist,
                        onShare = onShare,
                        onViewArtist = item.navigableArtist()?.let { artist -> { onViewArtist(artist) } },
                        onViewDetails = onViewDetails
                    )
                }

                is Artist -> {
                    ArtistActions(
                        artist = item,
                        onStartRadio = onStartRadio,
                        onShare = onShare,
                        onViewDetails = onViewDetails
                    )
                }

                is Playlist -> {
                    PlaylistActions(
                        playlist = item,
                        onPlay = onPlay,
                        onShuffle = onShuffle,
                        onStartRadio = onStartRadio,
                        onLike = onLike,
                        onPlayNext = onPlayNext,
                        onAddToQueue = onAddToQueue,
                        onAddToPlaylist = onAddToPlaylist,
                        onShare = onShare
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
}

@Composable
private fun SongHeader(
    song: Song,
    isLiked: Boolean,
    onLike: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = song.artworkUrl,
            contentDescription = song.title,
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = song.artistNames ?: "Unknown Artist",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        IconButton(onClick = onLike) {
            Icon(
                imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = if (isLiked) "Unlike" else "Like",
                tint = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AlbumHeader(album: Album) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = album.artworkUrl,
            contentDescription = album.name,
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = album.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = album.artistName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ArtistHeader(artist: Artist) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = artist.imageUrl,
            contentDescription = artist.name,
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = artist.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun PlaylistHeader(playlist: Playlist) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = playlist.artworkUrl,
            contentDescription = playlist.name,
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            if (playlist.ownerName != null) {
                Text(
                    text = playlist.ownerName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SongActions(
    song: Song,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onStartRadio: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onViewArtist: (() -> Unit)?,
    onViewAlbum: (() -> Unit)?,
    onViewDetails: () -> Unit
) {
    ActionItem(
        icon = Icons.Filled.Radio,
        label = stringResource(R.string.action_start_radio),
        onClick = onStartRadio
    )
    ActionItem(
        icon = Icons.Filled.PlayArrow,
        label = stringResource(R.string.action_play_next),
        onClick = onPlayNext
    )
    ActionItem(
        icon = Icons.Filled.Add,
        label = stringResource(R.string.action_add_to_queue),
        onClick = onAddToQueue
    )
    ActionItem(
        icon = Icons.AutoMirrored.Filled.PlaylistAdd,
        label = stringResource(R.string.action_add_to_playlist),
        onClick = onAddToPlaylist
    )
    ActionItem(
        icon = Icons.Filled.Share,
        label = stringResource(R.string.action_share),
        onClick = onShare
    )
    ActionItem(
        icon = Icons.Filled.Download,
        label = stringResource(R.string.action_download),
        onClick = onDownload
    )
    onViewArtist?.let {
        ActionItem(
            icon = Icons.Filled.Person,
            label = stringResource(R.string.action_view_artist),
            onClick = it
        )
    }
    onViewAlbum?.let {
        ActionItem(
            icon = Icons.Filled.Album,
            label = stringResource(R.string.action_view_album),
            onClick = it
        )
    }
    ActionItem(
        icon = Icons.Filled.Info,
        label = stringResource(R.string.action_details),
        onClick = onViewDetails
    )
}

@Composable
private fun AlbumActions(
    album: Album,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onStartRadio: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onShare: () -> Unit,
    onViewArtist: (() -> Unit)?,
    onViewDetails: () -> Unit
) {
    ActionItem(
        icon = Icons.Filled.PlayArrow,
        label = stringResource(R.string.action_play),
        onClick = onPlay
    )
    ActionItem(
        icon = Icons.Filled.Shuffle,
        label = stringResource(R.string.action_shuffle),
        onClick = onShuffle
    )
    ActionItem(
        icon = Icons.Filled.Radio,
        label = stringResource(R.string.action_start_radio),
        onClick = onStartRadio
    )
    ActionItem(
        icon = Icons.Filled.Add,
        label = stringResource(R.string.action_add_to_queue),
        onClick = onAddToQueue
    )
    ActionItem(
        icon = Icons.AutoMirrored.Filled.PlaylistAdd,
        label = stringResource(R.string.action_add_to_playlist),
        onClick = onAddToPlaylist
    )
    ActionItem(
        icon = Icons.Filled.Share,
        label = stringResource(R.string.action_share),
        onClick = onShare
    )
    onViewArtist?.let {
        ActionItem(
            icon = Icons.Filled.Person,
            label = stringResource(R.string.action_view_artist),
            onClick = it
        )
    }
    ActionItem(
        icon = Icons.Filled.Info,
        label = stringResource(R.string.action_details),
        onClick = onViewDetails
    )
}

@Composable
private fun ArtistActions(
    artist: Artist,
    onStartRadio: () -> Unit,
    onShare: () -> Unit,
    onViewDetails: () -> Unit
) {
    ActionItem(
        icon = Icons.Filled.Radio,
        label = stringResource(R.string.action_start_radio),
        onClick = onStartRadio
    )
    ActionItem(
        icon = Icons.Filled.Share,
        label = stringResource(R.string.action_share),
        onClick = onShare
    )
    ActionItem(
        icon = Icons.Filled.Info,
        label = stringResource(R.string.action_details),
        onClick = onViewDetails
    )
}

@Composable
private fun PlaylistActions(
    playlist: Playlist,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onStartRadio: () -> Unit,
    onLike: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onShare: () -> Unit
) {
    ActionItem(
        icon = Icons.Filled.PlayArrow,
        label = stringResource(R.string.action_play),
        onClick = onPlay
    )
    ActionItem(
        icon = Icons.Filled.Shuffle,
        label = stringResource(R.string.action_shuffle),
        onClick = onShuffle
    )
    ActionItem(
        icon = Icons.Filled.Radio,
        label = stringResource(R.string.action_start_radio),
        onClick = onStartRadio
    )
    ActionItem(
        icon = Icons.Filled.FavoriteBorder,
        label = stringResource(R.string.action_like),
        onClick = onLike
    )
    ActionItem(
        icon = Icons.Filled.PlayArrow,
        label = stringResource(R.string.action_play_next),
        onClick = onPlayNext
    )
    ActionItem(
        icon = Icons.Filled.Add,
        label = stringResource(R.string.action_add_to_queue),
        onClick = onAddToQueue
    )
    ActionItem(
        icon = Icons.AutoMirrored.Filled.PlaylistAdd,
        label = stringResource(R.string.action_add_to_playlist),
        onClick = onAddToPlaylist
    )
    ActionItem(
        icon = Icons.Filled.Share,
        label = stringResource(R.string.action_share),
        onClick = onShare
    )
}

@Composable
private fun ActionItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        headlineContent = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge
            )
        },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

