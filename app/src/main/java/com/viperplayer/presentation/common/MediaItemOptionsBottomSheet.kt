package com.viperplayer.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.viperplayer.R
import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.MediaItem
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.model.navigableAlbum
import com.viperplayer.domain.model.navigableArtist
import com.viperplayer.domain.model.toEntity
import com.viperplayer.presentation.common.components.InsetDivider
import com.viperplayer.presentation.common.components.SurfaceCard
import com.viperplayer.presentation.player.PlayerViewModel

private const val HeaderArtworkCorner = 14
private const val TileCorner = 16
private const val LikeButtonSize = 44
private const val NavThumbSize = 30

/**
 * Material 3 expressive bottom sheet showing options for a media item (mockup 6a): a rich header,
 * a row of quick-action tiles for the primary four actions, and grouped cards for the rest.
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
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        when (item) {
            is Song -> {
                SongHeader(
                    song = item,
                    // Live state for the playing song; the item's own stored flag otherwise
                    // (a non-playing track was always shown unliked).
                    isLiked = if (item.id == currentSong?.id) isLiked else item.isLiked,
                    onLike = onLike
                )
                QuickActionRow(
                    QuickAction(Icons.Filled.SkipNext, stringResource(R.string.action_play_next), onPlayNext),
                    QuickAction(Icons.Filled.Add, stringResource(R.string.action_add_to_queue), onAddToQueue),
                    QuickAction(Icons.AutoMirrored.Filled.PlaylistAdd, stringResource(R.string.action_add_to_playlist), onAddToPlaylist),
                    QuickAction(Icons.Filled.Share, stringResource(R.string.action_share), onShare),
                )
                SongGroupedCards(
                    song = item,
                    onStartRadio = onStartRadio,
                    onDownload = onDownload,
                    onViewArtist = item.navigableArtist()?.toEntity()?.let { artist -> { onViewArtist(artist) } },
                    onViewAlbum = item.navigableAlbum()?.let { album -> { onViewAlbum(album) } },
                    onViewDetails = onViewDetails
                )
            }

            is Album -> {
                AlbumHeader(album = item)
                QuickActionRow(
                    QuickAction(Icons.Filled.PlayArrow, stringResource(R.string.action_play), onPlay),
                    QuickAction(Icons.Filled.Shuffle, stringResource(R.string.action_shuffle), onShuffle),
                    QuickAction(Icons.Filled.Add, stringResource(R.string.action_add_to_queue), onAddToQueue),
                    QuickAction(Icons.AutoMirrored.Filled.PlaylistAdd, stringResource(R.string.action_add_to_playlist), onAddToPlaylist),
                )
                AlbumGroupedCards(
                    onStartRadio = onStartRadio,
                    onShare = onShare,
                    onViewArtist = item.navigableArtist()?.toEntity()?.let { artist -> { onViewArtist(artist) } },
                    onViewDetails = onViewDetails
                )
            }

            is Artist -> {
                ArtistHeader(artist = item)
                QuickActionRow(
                    QuickAction(Icons.Filled.Radio, stringResource(R.string.action_start_radio), onStartRadio),
                    QuickAction(Icons.Filled.Share, stringResource(R.string.action_share), onShare),
                )
                SurfaceCard {
                    NavigationRow(
                        title = stringResource(R.string.action_details),
                        leadingIcon = Icons.Filled.Info,
                        onClick = onViewDetails
                    )
                }
            }

            is Playlist -> {
                PlaylistHeader(
                    playlist = item,
                    // No live liked-state source exists for playlists (isLiked tracks the current
                    // song); the original playlist Like action likewise always rendered unliked.
                    isLiked = false,
                    onLike = onLike
                )
                QuickActionRow(
                    QuickAction(Icons.Filled.PlayArrow, stringResource(R.string.action_play), onPlay),
                    QuickAction(Icons.Filled.Shuffle, stringResource(R.string.action_shuffle), onShuffle),
                    QuickAction(Icons.Filled.Add, stringResource(R.string.action_add_to_queue), onAddToQueue),
                    QuickAction(Icons.AutoMirrored.Filled.PlaylistAdd, stringResource(R.string.action_add_to_playlist), onAddToPlaylist),
                )
                PlaylistGroupedCards(
                    onStartRadio = onStartRadio,
                    onPlayNext = onPlayNext,
                    onShare = onShare
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

// ---------------------------------------------------------------------------------------------
// Headers
// ---------------------------------------------------------------------------------------------

@Composable
private fun SongHeader(
    song: Song,
    isLiked: Boolean,
    onLike: () -> Unit
) {
    HeaderScaffold(
        artworkUrl = song.artworkUrl,
        contentDescription = song.title,
        circular = false,
        title = song.title,
        explicit = song.isExplicit,
        subtitle = songSubtitle(song),
        trailing = { LikeButton(isLiked = isLiked, onLike = onLike) }
    )
}

@Composable
private fun AlbumHeader(album: Album) {
    HeaderScaffold(
        artworkUrl = album.artworkUrl,
        contentDescription = album.name,
        circular = false,
        title = album.name,
        explicit = false,
        subtitle = album.artistName
    )
}

@Composable
private fun ArtistHeader(artist: Artist) {
    HeaderScaffold(
        artworkUrl = artist.imageUrl,
        contentDescription = artist.name,
        circular = true,
        title = artist.name,
        explicit = false,
        subtitle = null
    )
}

@Composable
private fun PlaylistHeader(
    playlist: Playlist,
    isLiked: Boolean,
    onLike: () -> Unit
) {
    HeaderScaffold(
        artworkUrl = playlist.artworkUrl,
        contentDescription = playlist.name,
        circular = false,
        title = playlist.name,
        explicit = false,
        subtitle = playlist.ownerName,
        trailing = { LikeButton(isLiked = isLiked, onLike = onLike) }
    )
}

@Composable
private fun HeaderScaffold(
    artworkUrl: String?,
    contentDescription: String,
    circular: Boolean,
    title: String,
    explicit: Boolean,
    subtitle: String?,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = artworkUrl,
            contentDescription = contentDescription,
            modifier = Modifier
                .size(64.dp)
                .clip(if (circular) CircleShape else RoundedCornerShape(HeaderArtworkCorner.dp)),
            contentScale = ContentScale.Crop
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (explicit) {
                    ExplicitBadge()
                }
            }
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        trailing?.invoke()
    }
}

@Composable
private fun ExplicitBadge() {
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "E",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LikeButton(
    isLiked: Boolean,
    onLike: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(LikeButtonSize.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(onClick = onLike),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = stringResource(R.string.action_like),
            tint = if (isLiked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Builds the Song subtitle "artist · duration · format" from available fields, dropping any missing. */
private fun songSubtitle(song: Song): String? {
    val parts = buildList {
        song.artistNames?.let { add(it) }
        formatDuration(song.durationMs)?.let { add(it) }
        // No format/quality field is available on the domain Song model, so it is always dropped.
    }
    return parts.joinToString(" · ").takeIf { it.isNotEmpty() }
}

/** Formats a millisecond duration as m:ss (or h:mm:ss), or null when unknown/zero. */
private fun formatDuration(durationMs: Long?): String? {
    val ms = durationMs ?: return null
    if (ms <= 0L) return null
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

// ---------------------------------------------------------------------------------------------
// Quick-action tiles
// ---------------------------------------------------------------------------------------------

private data class QuickAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit
)

@Composable
private fun QuickActionRow(vararg actions: QuickAction) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        actions.forEach { action ->
            QuickActionTile(
                action = action,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun QuickActionTile(
    action: QuickAction,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(TileCorner.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = action.onClick)
            .defaultMinSize(minHeight = 64.dp)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)
    ) {
        Icon(
            imageVector = action.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = action.label,
            fontSize = 11.5.sp,
            lineHeight = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Grouped cards
// ---------------------------------------------------------------------------------------------

@Composable
private fun SongGroupedCards(
    song: Song,
    onStartRadio: () -> Unit,
    onDownload: () -> Unit,
    onViewArtist: (() -> Unit)?,
    onViewAlbum: (() -> Unit)?,
    onViewDetails: () -> Unit
) {
    // Utility card: Start radio + Download.
    SurfaceCard {
        NavigationRow(
            title = stringResource(R.string.action_start_radio),
            leadingIcon = Icons.Filled.Radio,
            onClick = onStartRadio,
            chevron = false
        )
        InsetDivider()
        NavigationRow(
            title = stringResource(R.string.action_download),
            leadingIcon = Icons.Filled.Download,
            onClick = onDownload,
            chevron = false,
            // Download size string when known, else no trailing. The domain Song exposes no byte
            // size, so no trailing size is shown today.
            trailingText = null
        )
    }

    // Navigation card: only the rows that exist for this song.
    val navigableArtist = song.navigableArtist()
    val navigableAlbum = song.navigableAlbum()
    SurfaceCard {
        if (onViewArtist != null && navigableArtist != null) {
            NavigationRow(
                title = stringResource(R.string.action_view_artist),
                thumbUrl = null,
                thumbCircular = true,
                onClick = onViewArtist,
                trailingText = navigableArtist.name
            )
        }
        if (onViewAlbum != null && navigableAlbum != null) {
            if (onViewArtist != null && navigableArtist != null) InsetDivider()
            NavigationRow(
                title = stringResource(R.string.action_view_album),
                thumbUrl = navigableAlbum.artworkUrl,
                thumbCircular = false,
                onClick = onViewAlbum,
                trailingText = navigableAlbum.name
            )
        }
        if ((onViewArtist != null && navigableArtist != null) || (onViewAlbum != null && navigableAlbum != null)) {
            InsetDivider()
        }
        NavigationRow(
            title = stringResource(R.string.action_details),
            leadingIcon = Icons.Filled.Info,
            onClick = onViewDetails
        )
    }
}

@Composable
private fun AlbumGroupedCards(
    onStartRadio: () -> Unit,
    onShare: () -> Unit,
    onViewArtist: (() -> Unit)?,
    onViewDetails: () -> Unit
) {
    // Utility card: Start radio + Share (the remaining non-tile actions).
    SurfaceCard {
        NavigationRow(
            title = stringResource(R.string.action_start_radio),
            leadingIcon = Icons.Filled.Radio,
            onClick = onStartRadio,
            chevron = false
        )
        InsetDivider()
        NavigationRow(
            title = stringResource(R.string.action_share),
            leadingIcon = Icons.Filled.Share,
            onClick = onShare,
            chevron = false
        )
    }

    // Navigation card: Go to artist (when linked) + Details.
    SurfaceCard {
        if (onViewArtist != null) {
            NavigationRow(
                title = stringResource(R.string.action_view_artist),
                thumbUrl = null,
                thumbCircular = true,
                onClick = onViewArtist
            )
            InsetDivider()
        }
        NavigationRow(
            title = stringResource(R.string.action_details),
            leadingIcon = Icons.Filled.Info,
            onClick = onViewDetails
        )
    }
}

@Composable
private fun PlaylistGroupedCards(
    onStartRadio: () -> Unit,
    onPlayNext: () -> Unit,
    onShare: () -> Unit
) {
    // Utility card: the remaining non-tile actions (Start radio, Play next, Share).
    SurfaceCard {
        NavigationRow(
            title = stringResource(R.string.action_start_radio),
            leadingIcon = Icons.Filled.Radio,
            onClick = onStartRadio,
            chevron = false
        )
        InsetDivider()
        NavigationRow(
            title = stringResource(R.string.action_play_next),
            leadingIcon = Icons.Filled.SkipNext,
            onClick = onPlayNext,
            chevron = false
        )
        InsetDivider()
        NavigationRow(
            title = stringResource(R.string.action_share),
            leadingIcon = Icons.Filled.Share,
            onClick = onShare,
            chevron = false
        )
    }
}

/**
 * A single grouped-card row. Leads with either an icon or a 30dp thumb, ends with an optional
 * trailing label and/or a [ChevronRight] (chevron shown for navigation rows).
 */
@Composable
private fun NavigationRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    thumbUrl: String? = null,
    thumbCircular: Boolean = false,
    trailingText: String? = null,
    chevron: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = 44.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            leadingIcon != null -> Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            else -> AsyncImage(
                model = thumbUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(NavThumbSize.dp)
                    .clip(if (thumbCircular) CircleShape else RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (trailingText != null) Modifier else Modifier.weight(1f)
        )
        if (trailingText != null) {
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = trailingText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
        if (chevron) {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
