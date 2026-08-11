package com.viperplayer.presentation.common

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.viperplayer.R
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.Song
import com.viperplayer.presentation.theme.Spacing

/**
 * Holds the [Song] whose "Add to playlist" picker is showing. Drive it with [show]/[dismiss] from a
 * screen; render the sheet once with [AddToPlaylistSheetHost].
 */
@Stable
class AddToPlaylistController {
    var song by mutableStateOf<Song?>(null)
        private set

    fun show(song: Song) {
        this.song = song
    }

    fun dismiss() {
        song = null
    }
}

@Composable
fun rememberAddToPlaylistController(): AddToPlaylistController =
    remember { AddToPlaylistController() }

/**
 * Hosts the add-to-playlist picker bottom sheet. When [controller] has a song, shows a sheet listing
 * the user's local playlists plus a "New playlist" entry; picking either adds the song and shows a
 * confirmation toast. All persistence goes through [AddToPlaylistViewModel] — this only renders.
 */
@Composable
fun AddToPlaylistSheetHost(
    controller: AddToPlaylistController,
    viewModel: AddToPlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current

    // Collect results from a location that stays composed regardless of controller.song, so the
    // confirmation/failure toast still arrives after the sheet has already been dismissed (the add
    // completes asynchronously, well after dismiss() clears controller.song).
    LaunchedEffect(viewModel) {
        viewModel.results.collect { result ->
            val message = if (result.success) {
                context.getString(R.string.add_to_playlist_added, result.playlistName)
            } else {
                context.getString(R.string.add_to_playlist_failed, result.playlistName)
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    val song = controller.song ?: return
    AddToPlaylistSheet(
        song = song,
        viewModel = viewModel,
        onDismiss = controller::dismiss,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddToPlaylistSheet(
    song: Song,
    viewModel: AddToPlaylistViewModel,
    onDismiss: () -> Unit,
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        AddToPlaylistSheetContent(
            playlists = playlists,
            onNewPlaylist = { showCreateDialog = true },
            onPickPlaylist = { playlist ->
                viewModel.addToExisting(playlist, song)
                onDismiss()
            },
        )
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name ->
                showCreateDialog = false
                viewModel.createAndAdd(name, song)
                onDismiss()
            }
        )
    }
}

/**
 * Stateless body of the add-to-playlist picker: a "New playlist" entry followed by the user's
 * existing local playlists (or an empty hint). Hoisted out of [AddToPlaylistSheetHost] so it can be
 * rendered/tested without Hilt or a live [ModalBottomSheet].
 */
@Composable
fun AddToPlaylistSheetContent(
    playlists: List<Playlist>,
    onNewPlaylist: () -> Unit,
    onPickPlaylist: (Playlist) -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = Spacing.lg)) {
        Text(
            text = stringResource(R.string.add_to_playlist_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = Spacing.xxl, vertical = Spacing.sm)
        )

        // "New playlist" entry — always first.
        ListItem(
            headlineContent = { Text(stringResource(R.string.add_to_playlist_new)) },
            leadingContent = {
                Icon(Icons.Filled.Add, contentDescription = null)
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onNewPlaylist)
        )

        if (playlists.isEmpty()) {
            Text(
                text = stringResource(R.string.add_to_playlist_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.xxl, vertical = Spacing.lg)
            )
        } else {
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                items(playlists, key = { it.id.toString() }) { playlist ->
                    PlaylistPickerRow(
                        playlist = playlist,
                        onClick = { onPickPlaylist(playlist) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistPickerRow(
    playlist: Playlist,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                text = playlist.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(pluralStringResource(R.plurals.song_count, playlist.songCount, playlist.songCount))
        },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(Spacing.sm)),
                contentAlignment = Alignment.Center
            ) {
                if (playlist.artworkUrl != null) {
                    AsyncImage(
                        model = playlist.artworkUrl,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp)
                    )
                } else {
                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null)
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    )
}

@Composable
private fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.create_playlist_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.create_playlist_name_hint)) }
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name) },
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(R.string.action_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
