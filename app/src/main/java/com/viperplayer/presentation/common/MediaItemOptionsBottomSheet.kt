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
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
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
import com.viperplayer.presentation.player.PlayerViewModel

private const val HeaderArtworkCorner = 14
private const val TileCorner = 16
private const val CardCorner = 20
private const val LikeButtonSize = 44
private const val NavThumbSize = 30
private const val AlbumThumbCorner = 7

/** Divider inset (mockup: `margin-left: 50px`) and alpha (mockup: `rgba(73,69,79,0.55)`). */
private const val DividerStartInset = 50
private const val DividerAlpha = 0.55f

/**
 * Material 3 expressive bottom sheet showing options for a media item (mockup 6a): a rich header,
 * a row of quick-action tiles for the primary four actions, and grouped cards for the rest. Cards
 * and tiles sit one tone above the sheet background (surfaceContainerHigh over surfaceContainer).
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
        verticalArrangement = Arrangement.spacedBy(12.dp)
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
                    QuickAction(Icons.Rounded.SkipNext, stringResource(R.string.action_play_next), onPlayNext),
                    QuickAction(Icons.AutoMirrored.Rounded.QueueMusic, stringResource(R.string.options_tile_queue), onAddToQueue),
                    QuickAction(Icons.AutoMirrored.Rounded.PlaylistAdd, stringResource(R.string.options_tile_playlist), onAddToPlaylist),
                    QuickAction(Icons.Rounded.IosShare, stringResource(R.string.action_share), onShare),
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
                    QuickAction(Icons.Rounded.PlayArrow, stringResource(R.string.action_play), onPlay),
                    QuickAction(Icons.Rounded.Shuffle, stringResource(R.string.action_shuffle), onShuffle),
                    QuickAction(Icons.AutoMirrored.Rounded.QueueMusic, stringResource(R.string.options_tile_queue), onAddToQueue),
                    QuickAction(Icons.AutoMirrored.Rounded.PlaylistAdd, stringResource(R.string.options_tile_playlist), onAddToPlaylist),
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
                    QuickAction(Icons.Rounded.Radio, stringResource(R.string.action_start_radio), onStartRadio),
                    QuickAction(Icons.Rounded.IosShare, stringResource(R.string.action_share), onShare),
                )
                GroupedCard {
                    NavigationRow(
                        title = stringResource(R.string.action_details),
                        leadingIcon = Icons.Rounded.Info,
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
                    QuickAction(Icons.Rounded.PlayArrow, stringResource(R.string.action_play), onPlay),
                    QuickAction(Icons.Rounded.Shuffle, stringResource(R.string.action_shuffle), onShuffle),
                    QuickAction(Icons.AutoMirrored.Rounded.QueueMusic, stringResource(R.string.options_tile_queue), onAddToQueue),
                    QuickAction(Icons.AutoMirrored.Rounded.PlaylistAdd, stringResource(R.string.options_tile_playlist), onAddToPlaylist),
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
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
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
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
                    fontSize = 13.sp,
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
            .size(15.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "E",
            fontSize = 10.sp,
            lineHeight = 10.sp,
            fontWeight = FontWeight.Bold,
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
            imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            contentDescription = stringResource(R.string.action_like),
            tint = if (isLiked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(21.dp)
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
        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
            .padding(vertical = 13.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterVertically)
    ) {
        Icon(
            imageVector = action.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = action.label,
            fontSize = 11.5.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Grouped cards
// ---------------------------------------------------------------------------------------------

/**
 * A grouped card in the mockup's visual language: rounded panel filled with surfaceContainerHigh
 * (one tone above the sheet background), stacking rows separated by [RowDivider]s.
 */
@Composable
private fun GroupedCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CardCorner.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        content()
    }
}

/** A 1dp hairline between grouped-card rows: outlineVariant at ~55% alpha, inset 50dp from start. */
@Composable
private fun RowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = DividerStartInset.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = DividerAlpha))
    )
}

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
    GroupedCard {
        NavigationRow(
            title = stringResource(R.string.action_start_radio),
            leadingIcon = Icons.Rounded.Radio,
            onClick = onStartRadio,
            chevron = false
        )
        RowDivider()
        NavigationRow(
            title = stringResource(R.string.action_download),
            leadingIcon = Icons.Rounded.Download,
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
    val hasArtist = onViewArtist != null && navigableArtist != null
    val hasAlbum = onViewAlbum != null && navigableAlbum != null
    GroupedCard {
        if (hasArtist) {
            NavigationRow(
                title = stringResource(R.string.action_view_artist),
                // ArtistRef byline carries no image URL; the round thumb shows a placeholder.
                thumbUrl = null,
                thumbCircular = true,
                onClick = onViewArtist,
                trailingText = navigableArtist.name
            )
        }
        if (hasAlbum) {
            if (hasArtist) RowDivider()
            NavigationRow(
                title = stringResource(R.string.action_view_album),
                thumbUrl = navigableAlbum.artworkUrl,
                thumbCircular = false,
                onClick = onViewAlbum,
                trailingText = navigableAlbum.name
            )
        }
        if (hasArtist || hasAlbum) {
            RowDivider()
        }
        NavigationRow(
            title = stringResource(R.string.action_details),
            leadingIcon = Icons.Rounded.Info,
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
    GroupedCard {
        NavigationRow(
            title = stringResource(R.string.action_start_radio),
            leadingIcon = Icons.Rounded.Radio,
            onClick = onStartRadio,
            chevron = false
        )
        RowDivider()
        NavigationRow(
            title = stringResource(R.string.action_share),
            leadingIcon = Icons.Rounded.IosShare,
            onClick = onShare,
            chevron = false
        )
    }

    // Navigation card: Go to artist (when linked) + Details.
    GroupedCard {
        if (onViewArtist != null) {
            NavigationRow(
                title = stringResource(R.string.action_view_artist),
                thumbUrl = null,
                thumbCircular = true,
                onClick = onViewArtist
            )
            RowDivider()
        }
        NavigationRow(
            title = stringResource(R.string.action_details),
            leadingIcon = Icons.Rounded.Info,
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
    GroupedCard {
        NavigationRow(
            title = stringResource(R.string.action_start_radio),
            leadingIcon = Icons.Rounded.Radio,
            onClick = onStartRadio,
            chevron = false
        )
        RowDivider()
        NavigationRow(
            title = stringResource(R.string.action_play_next),
            leadingIcon = Icons.Rounded.SkipNext,
            onClick = onPlayNext,
            chevron = false
        )
        RowDivider()
        NavigationRow(
            title = stringResource(R.string.action_share),
            leadingIcon = Icons.Rounded.IosShare,
            onClick = onShare,
            chevron = false
        )
    }
}

/**
 * A single grouped-card row. Leads with either a 21dp icon or a 30dp artwork thumb, ends with an
 * optional trailing context label and/or a [ChevronRight] (chevron shown for navigation rows).
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
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            leadingIcon != null -> Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(21.dp)
            )
            else -> AsyncImage(
                model = thumbUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(NavThumbSize.dp)
                    .clip(if (thumbCircular) CircleShape else RoundedCornerShape(AlbumThumbCorner.dp)),
                contentScale = ContentScale.Crop
            )
        }
        Text(
            text = title,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (trailingText != null) Modifier else Modifier.weight(1f)
        )
        if (trailingText != null) {
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = trailingText,
                fontSize = 12.sp,
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
