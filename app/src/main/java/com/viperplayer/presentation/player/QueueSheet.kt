package com.viperplayer.presentation.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.viperplayer.R
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.player.PlayerQueueLogic
import com.viperplayer.domain.player.PlayerQueueLogic.movedItem
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Queue bottom sheet — reads live state from the [PlayerViewModel] and forwards its own gesture
 * callbacks (play / remove / reorder / save) back through it. All rendering + interaction lives in
 * the stateless [QueueSheetContent] below so it can be exercised in Compose UI tests without a
 * MediaController.
 */
@Composable
fun QueueSheet(
    viewModel: PlayerViewModel,
    currentSong: Song?,
    onDismiss: () -> Unit
) {
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()

    QueueSheetContent(
        queue = queue,
        currentSongId = currentSong?.id,
        isPlaying = isPlaying,
        onPlay = viewModel::playFromQueue,
        onRemove = viewModel::removeFromQueue,
        onReorder = viewModel::reorderQueue,
        onSaveAsPlaylist = viewModel::saveQueueAsPlaylist,
    )
}

/**
 * Stateless queue list: a single reorderable list with the now-playing row highlighted. Drag the
 * handle to reorder (each hover-swap updates a live working copy; the net move commits once on drop
 * via [onReorder], preserving the MediaController's atomic move contract). Tap a row to play it,
 * swipe a row sideways OR tap × to remove it, and the footer saves the queue as a local playlist.
 */
@Composable
fun QueueSheetContent(
    queue: List<Song>,
    currentSongId: MediaId?,
    isPlaying: Boolean,
    onPlay: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onReorder: (Int, Int) -> Unit,
    onSaveAsPlaylist: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rowHeight = 64.dp
    var showSaveDialog by remember { mutableStateOf(false) }

    // Working copy so a drag reorders live without waiting for the player round-trip. Re-seeded from
    // the player whenever the queue changes (add / remove / the committed reorder coming back).
    var localQueue by remember(queue) { mutableStateOf(queue) }
    // Stable per-row keys that disambiguate a track appearing more than once in the queue (e.g. after
    // "duplicate in queue"), so the LazyColumn / reorderable keys stay unique. Recomputed when the
    // working copy changes. Occurrence numbering follows list order, which is what the library keys
    // on; the net move committed to the player is tracked by index below, independent of these keys.
    val keys = remember(localQueue) { PlayerQueueLogic.queueKeys(localQueue) { it.id.toString() } }

    // The now-playing row to highlight. Seeded from the player's current song, then updated
    // optimistically as the user reorders / removes rows so the highlight tracks instantly instead of
    // waiting for the player's queue to round-trip back. Re-seeded whenever the player state changes.
    var highlightIndex by remember(queue, currentSongId) {
        mutableStateOf(queue.indexOfFirst { it.id == currentSongId })
    }

    // The index where the current drag began and where it currently sits. The library's onMove fires
    // for every hover-swap; we snapshot the origin once and follow the destination, then commit the
    // net start→end move on drop — matching the MediaController's atomic move(from, to) contract.
    var dragFromIndex by remember { mutableStateOf<Int?>(null) }
    var dragToIndex by remember { mutableStateOf<Int?>(null) }

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        if (from.index in localQueue.indices && to.index in localQueue.indices) {
            highlightIndex = PlayerQueueLogic.currentIndexAfterMove(
                highlightIndex, from.index, to.index, localQueue.size
            )
            localQueue = localQueue.movedItem(from.index, to.index)
            if (dragFromIndex == null) dragFromIndex = from.index
            dragToIndex = to.index
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.action_queue),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = pluralStringResource(R.plurals.song_count, localQueue.size, localQueue.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (localQueue.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.queue_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .testTag("queueList")
            ) {
                itemsIndexed(localQueue, key = { index, _ -> keys[index] }) { index, song ->
                    ReorderableItem(reorderableState, key = keys[index]) { isDragging ->
                        SwipeableQueueRow(
                            index = index,
                            song = song,
                            isCurrent = index == highlightIndex,
                            isPlaying = isPlaying,
                            isDragging = isDragging,
                            rowHeight = rowHeight,
                            onPlay = { onPlay(index) },
                            onRemove = {
                                // Optimistically drop the row + shift the highlight so the list reacts
                                // immediately; the player's queue re-emission re-seeds both.
                                val removal = PlayerQueueLogic.removalResult(
                                    highlightIndex, index, localQueue.size
                                )
                                localQueue = localQueue.toMutableList().apply { removeAt(index) }
                                highlightIndex = removal.newCurrentIndex ?: -1
                                onRemove(index)
                            },
                            dragHandle = {
                                Icon(
                                    imageVector = Icons.Filled.DragHandle,
                                    contentDescription = stringResource(R.string.queue_drag_reorder),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .testTag("queueDragHandle_$index")
                                        .size(40.dp)
                                        .padding(8.dp)
                                        .draggableHandle(
                                            onDragStopped = {
                                                val from = dragFromIndex
                                                val to = dragToIndex
                                                if (from != null && to != null && from != to) {
                                                    onReorder(from, to)
                                                }
                                                dragFromIndex = null
                                                dragToIndex = null
                                            },
                                        )
                                )
                            }
                        )
                    }
                }
            }
        }

        if (localQueue.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(100))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable { showSaveDialog = true }
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.PlaylistAdd,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(R.string.queue_save_as_playlist),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }

    if (showSaveDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text(stringResource(R.string.queue_save_as_playlist)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.queue_playlist_name)) }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        onSaveAsPlaylist(name)
                        showSaveDialog = false
                    }
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}

/**
 * A [QueueRow] wrapped in a [SwipeToDismissBox] so a horizontal swipe (either direction) removes the
 * track from the queue, revealing a delete-tinted background as it swipes.
 */
@Composable
private fun SwipeableQueueRow(
    index: Int,
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isDragging: Boolean,
    rowHeight: Dp,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    dragHandle: @Composable () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) {
                onRemove()
                true
            } else {
                false
            }
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        modifier = Modifier.testTag("queueSwipe_$index"),
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    ) {
        QueueRow(
            song = song,
            index = index,
            isCurrent = isCurrent,
            isPlaying = isPlaying,
            isDragging = isDragging,
            rowHeight = rowHeight,
            onPlay = onPlay,
            onRemove = onRemove,
            dragHandle = dragHandle,
        )
    }
}

@Composable
private fun QueueRow(
    song: Song,
    index: Int,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isDragging: Boolean,
    rowHeight: Dp,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    dragHandle: @Composable () -> Unit,
) {
    val background = when {
        isDragging -> MaterialTheme.colorScheme.surfaceContainerHighest
        isCurrent -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        else -> MaterialTheme.colorScheme.surface
    }
    val elevation = if (isDragging) 8.dp else 0.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .shadow(elevation)
            .background(background)
            .clickable(onClick = onPlay)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = song.artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(
                text = song.title,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val artists = song.artists.joinToString { it.name }
            if (artists.isNotEmpty()) {
                Text(
                    text = artists,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        if (isCurrent && isPlaying) {
            Icon(
                imageVector = Icons.Filled.GraphicEq,
                contentDescription = stringResource(R.string.queue_now_playing),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier.testTag("queueRemove_$index")
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.queue_remove),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        dragHandle()
    }
}
