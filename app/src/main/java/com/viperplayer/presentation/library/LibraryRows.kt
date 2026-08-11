package com.viperplayer.presentation.library

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.rounded.AddToQueue
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.QueuePlayNext
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.viperplayer.R
import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.Song
import com.viperplayer.presentation.common.ListItem
import com.viperplayer.presentation.common.PlayingArtworkOverlay
import com.viperplayer.presentation.ktx.infiniteBasicMarquee
import com.viperplayer.presentation.search.model.SearchItem
import com.viperplayer.presentation.theme.Spacing
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Corner radius for the filled active-row container (mockup 3a). */
private val ActiveRowCorner = 14.dp

/** Row artwork thumbnail size + corner radius (mockup 3a bumps the radius from the shared 6dp). */
private val RowArtworkSize = 48.dp
private val RowArtworkCorner = 10.dp

/**
 * A Library song row built locally (not via the shared [ListItem]) so it can carry the mockup-3a
 * fidelity that the shared row doesn't: the now-playing highlight (filled [ActiveRowCorner]-rounded
 * `surfaceContainerHigh` container with a small horizontal inset, primary-tinted title, and a trailing
 * `primary` graphic_eq glyph before the more button) and the small rounded-square "E" explicit tag
 * after the title. The artwork ([SongRowArtwork], reusing the shared now-playing overlay) stays
 * visible — the eq lives beside the title, never over the thumbnail. Unplayable songs are dimmed. Keeps
 * the existing swipe-left → play-next / swipe-right → add-to-queue gestures.
 */
@Composable
fun SongRow(
    song: Song,
    isActive: Boolean,
    isPlaying: Boolean,
    onPlay: (Song) -> Unit,
    onMore: (Song) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Optional "Sounds like X" recommendation reason (P4), rendered as a caption under the artist line.
     * Null on non-recommendation surfaces (the default), so those rows are unchanged.
     */
    reason: String? = null,
    /**
     * Optional explicit-feedback callbacks (P4). When both are provided, a thumbs-up / thumbs-down pair
     * is shown before the more button; null (the default) hides them so ordinary song rows are unchanged.
     * [isDisliked] dims the row to confirm a thumbs-down.
     */
    onThumbsUp: ((Song) -> Unit)? = null,
    onThumbsDown: ((Song) -> Unit)? = null,
    isDisliked: Boolean = false,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val addedToQueueMessage = stringResource(R.string.toast_added_to_queue)
    val playingNextMessage = stringResource(R.string.toast_playing_next)

    val maxOffset = with(density) { 112.dp.toPx() }
    val threshold = maxOffset * 0.55f
    val offset = remember { Animatable(0f) }

    val draggableState = rememberDraggableState { delta ->
        scope.launch {
            val coerced = (offset.value + delta).coerceIn(-maxOffset, maxOffset)
            val wasPast = abs(offset.value) >= threshold
            val isPast = abs(coerced) >= threshold
            if (isPast != wasPast) {
                haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
            }
            offset.snapTo(coerced)
        }
    }

    val clickModifier = if (song.isPlayable) {
        Modifier.combinedClickable(onClick = { onPlay(song) }, onLongClick = { onMore(song) })
    } else {
        Modifier.combinedClickable(onClick = {}, onLongClick = { onMore(song) })
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            // The active row is inset + filled; give the whole swipe box the container so the fill and
            // the swipe reveals share the same rounded bounds.
            .then(
                if (isActive) {
                    Modifier
                        .padding(horizontal = Spacing.sm)
                        .clip(RoundedCornerShape(ActiveRowCorner))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                } else {
                    Modifier
                }
            )
            .height(IntrinsicSize.Min)
            .draggable(
                state = draggableState,
                orientation = Orientation.Horizontal,
                enabled = song.isPlayable,
                onDragStopped = {
                    scope.launch {
                        when {
                            offset.value < -threshold -> {
                                onPlayNext(song)
                                Toast.makeText(context, playingNextMessage, Toast.LENGTH_SHORT).show()
                            }
                            offset.value > threshold -> {
                                onAddToQueue(song)
                                Toast.makeText(context, addedToQueueMessage, Toast.LENGTH_SHORT).show()
                            }
                        }
                        offset.animateTo(0f)
                    }
                }
            )
    ) {
        val absOffsetDp = with(density) { abs(offset.value).toDp() }
        when {
            // Dragged left → reveal play-next on the trailing edge.
            offset.value < 0 -> SwipeAffordance(
                icon = Icons.Rounded.QueuePlayNext,
                contentDescription = stringResource(R.string.action_play_next),
                width = absOffsetDp,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
            // Dragged right → reveal add-to-queue on the leading edge.
            offset.value > 0 -> SwipeAffordance(
                icon = Icons.Rounded.AddToQueue,
                contentDescription = stringResource(R.string.action_add_to_queue),
                width = absOffsetDp,
                modifier = Modifier.align(Alignment.CenterStart),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(x = offset.value.roundToInt(), y = 0) }
                .then(clickModifier)
                // Content sits ~16dp from the screen edge. The active row's container already adds an
                // 8dp inset, so its inner padding is 8dp; the flat non-active row uses the full 16dp.
                .padding(horizontal = if (isActive) Spacing.sm else Spacing.lg, vertical = 7.dp)
                .then(if (!song.isPlayable || isDisliked) Modifier.alpha(0.5f) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            SongRowArtwork(
                artworkUrl = song.artworkUrl,
                isActive = isActive,
                isPlaying = isPlaying,
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = song.title,
                        modifier = Modifier.weight(1f, fill = false).infiniteBasicMarquee(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isActive) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    if (song.isExplicit) ExplicitTag()
                }
                song.artistNames?.let { artists ->
                    Text(
                        text = artists,
                        modifier = Modifier.infiniteBasicMarquee(),
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (reason != null) {
                    Text(
                        text = reason,
                        modifier = Modifier.infiniteBasicMarquee(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (onThumbsUp != null && onThumbsDown != null) {
                IconButton(onClick = { onThumbsUp(song) }) {
                    Icon(
                        imageVector = Icons.Rounded.ThumbUp,
                        contentDescription = stringResource(R.string.rec_feedback_thumbs_up),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { onThumbsDown(song) }) {
                    Icon(
                        imageVector = if (isDisliked) Icons.Rounded.ThumbDown else Icons.Outlined.ThumbDown,
                        contentDescription = stringResource(R.string.rec_feedback_thumbs_down),
                        tint = if (isDisliked) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            if (isActive) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(onClick = { onMore(song) }) {
                Icon(
                    Icons.Rounded.MoreVert,
                    contentDescription = stringResource(R.string.action_more),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The swipe-reveal affordance shown under a dragged song row (play-next trailing, queue leading). */
@Composable
private fun SwipeAffordance(
    icon: ImageVector,
    contentDescription: String,
    width: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(width)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.requiredSize(Spacing.xxl),
        )
    }
}

/**
 * The song row's leading artwork — a local thumbnail at [RowArtworkCorner] (10dp, up from the shared
 * row's 6dp per mockup 3a) with the shared now-playing overlay centered on top. Built here rather than
 * reusing the shared artwork so the Library rows can carry the larger radius without changing shared UI.
 */
@Composable
private fun SongRowArtwork(
    artworkUrl: String?,
    isActive: Boolean,
    isPlaying: Boolean,
) {
    Box(
        modifier = Modifier.size(RowArtworkSize),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = artworkUrl,
            contentDescription = stringResource(R.string.cd_artwork),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .clip(RoundedCornerShape(RowArtworkCorner)),
        )
        PlayingArtworkOverlay(isActive = isActive, isPlaying = isPlaying)
    }
}

/**
 * The small rounded-square "E" explicit tag (mockup 3a): a surfaceVariant-filled square with a bold
 * onSurfaceVariant "E", replacing the Explicit vector icon. Shown right after a song title.
 */
@Composable
private fun ExplicitTag() {
    Box(
        modifier = Modifier
            .size(15.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.library_explicit_short),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** An album row for the Library lists (navigates on tap). Uses the shared [ListItem]. */
@Composable
fun AlbumRow(
    album: Album,
    onClick: (Album) -> Unit,
    onMore: (Album) -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        type = SearchItem.Type.ALBUM,
        title = album.name,
        badges = emptyList(),
        subtitle = album.artists.joinToString { it.name }.takeIf { it.isNotEmpty() },
        artworkUrl = album.artworkUrl,
        isActive = false,
        isPlaying = false,
        onClick = { onClick(album) },
        onMoreClick = { onMore(album) },
        onLongClick = { onMore(album) },
        modifier = modifier,
    )
}

/** An artist row for the Library lists (navigates on tap). Uses the shared [ListItem]. */
@Composable
fun ArtistRow(
    artist: Artist,
    onClick: (Artist) -> Unit,
    onMore: (Artist) -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        type = SearchItem.Type.ARTIST,
        title = artist.name,
        badges = emptyList(),
        subtitle = null,
        artworkUrl = artist.imageUrl,
        isActive = false,
        isPlaying = false,
        onClick = { onClick(artist) },
        onMoreClick = { onMore(artist) },
        onLongClick = { onMore(artist) },
        modifier = modifier,
    )
}

/** A playlist row for the Library lists (navigates on tap). Uses the shared [ListItem]. */
@Composable
fun PlaylistRow(
    playlist: Playlist,
    onClick: (Playlist) -> Unit,
    onMore: (Playlist) -> Unit,
    modifier: Modifier = Modifier,
) {
    val countLabel = pluralStringResource(R.plurals.song_count, playlist.songCount, playlist.songCount)
    ListItem(
        type = SearchItem.Type.PLAYLIST,
        title = playlist.name,
        badges = emptyList(),
        subtitle = playlist.ownerName?.let { "$it • $countLabel" } ?: countLabel,
        artworkUrl = playlist.artworkUrl,
        isActive = false,
        isPlaying = false,
        onClick = { onClick(playlist) },
        onMoreClick = { onMore(playlist) },
        onLongClick = { onMore(playlist) },
        modifier = modifier,
    )
}
