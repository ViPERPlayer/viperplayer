package com.viperplayer.presentation.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.viperplayer.R
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.viperplayer.domain.model.Song

/** Moves an element from [from] to [to], returning a new list. */
private fun <T> List<T>.moved(from: Int, to: Int): List<T> =
    toMutableList().apply { add(to, removeAt(from)) }

/**
 * Queue bottom sheet — rebuilt: a single reorderable list with the now-playing row highlighted.
 * Drag the handle to reorder (committed once on drop), tap a row to play it, the × removes it, and
 * the footer saves the queue as a local playlist.
 */
@Composable
fun QueueSheet(
    viewModel: PlayerViewModel,
    currentSong: Song?,
    onDismiss: () -> Unit
) {
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()

    val density = LocalDensity.current
    val rowHeight = 64.dp
    val rowHeightPx = with(density) { rowHeight.toPx() }

    // Local working copy so a drag can reorder live without waiting on the player round-trip.
    var localQueue by remember { mutableStateOf(queue) }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var startIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var showSaveDialog by remember { mutableStateOf(false) }

    // Re-sync from the player whenever the queue changes and we're not mid-drag.
    LaunchedEffect(queue) {
        if (draggingIndex == null) localQueue = queue
    }

    fun commitReorder() {
        val from = startIndex
        val to = draggingIndex
        if (from != null && to != null && from != to) {
            viewModel.reorderQueue(from, to)
        }
        draggingIndex = null
        startIndex = null
        dragOffset = 0f
    }

    Column(
        modifier = Modifier
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
            LazyColumn(modifier = Modifier.heightIn(max = 460.dp)) {
                itemsIndexed(localQueue) { index, song ->
                    val isCurrent = currentSong?.id == song.id
                    val isDragging = draggingIndex == index
                    QueueRow(
                        song = song,
                        isCurrent = isCurrent,
                        isPlaying = isPlaying,
                        isDragging = isDragging,
                        rowHeight = rowHeight,
                        modifier = Modifier
                            .zIndex(if (isDragging) 1f else 0f)
                            .graphicsLayer {
                                translationY = if (isDragging) dragOffset else 0f
                                if (isDragging) {
                                    shadowElevation = with(density) { 8.dp.toPx() }
                                    scaleX = 1.02f
                                    scaleY = 1.02f
                                }
                            },
                        onPlay = { viewModel.playFromQueue(index) },
                        onRemove = { viewModel.removeFromQueue(index) },
                        dragHandleModifier = Modifier.pointerInput(localQueue.size) {
                            detectDragGestures(
                                onDragStart = {
                                    draggingIndex = index
                                    startIndex = index
                                    dragOffset = 0f
                                },
                                onDragEnd = { commitReorder() },
                                onDragCancel = { commitReorder() },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffset += dragAmount.y
                                    val cur = draggingIndex ?: return@detectDragGestures
                                    when {
                                        dragOffset > rowHeightPx / 2 && cur < localQueue.lastIndex -> {
                                            localQueue = localQueue.moved(cur, cur + 1)
                                            draggingIndex = cur + 1
                                            dragOffset -= rowHeightPx
                                        }

                                        dragOffset < -rowHeightPx / 2 && cur > 0 -> {
                                            localQueue = localQueue.moved(cur, cur - 1)
                                            draggingIndex = cur - 1
                                            dragOffset += rowHeightPx
                                        }
                                    }
                                }
                            )
                        }
                    )
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
                        viewModel.saveQueueAsPlaylist(name)
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

@Composable
private fun QueueRow(
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isDragging: Boolean,
    rowHeight: androidx.compose.ui.unit.Dp,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    dragHandleModifier: Modifier,
    modifier: Modifier = Modifier
) {
    val background = when {
        isDragging -> MaterialTheme.colorScheme.surfaceContainerHighest
        isCurrent -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        else -> MaterialTheme.colorScheme.surface
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(rowHeight)
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
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.queue_remove),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        Icon(
            imageVector = Icons.Filled.DragHandle,
            contentDescription = stringResource(R.string.queue_drag_reorder),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = dragHandleModifier
                .size(40.dp)
                .padding(8.dp)
        )
    }
}
