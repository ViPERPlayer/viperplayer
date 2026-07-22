package com.viperplayer.presentation.detail

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.viperplayer.R
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.PluginPendingAction
import com.viperplayer.domain.model.MediaItem
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.model.SortOption
import com.viperplayer.domain.model.SortOrder
import com.viperplayer.presentation.common.AddToPlaylistSheetHost
import com.viperplayer.presentation.common.SortMenu
import com.viperplayer.presentation.common.rememberAddToPlaylistController
import com.viperplayer.presentation.common.CollapsingArtworkScaffold
import com.viperplayer.presentation.common.ErrorState
import com.viperplayer.presentation.plugins.PluginActionsViewModel
import com.viperplayer.presentation.plugins.rememberPluginActionResolver
import com.viperplayer.presentation.common.ListItem
import com.viperplayer.presentation.common.ListItemLeadingArtwork
import com.viperplayer.presentation.common.ListItemTrailingWithDuration
import com.viperplayer.presentation.common.MediaItemOptionsSheetHost
import com.viperplayer.presentation.common.rememberMediaItemOptionsController
import com.viperplayer.presentation.search.model.ItemBadge
import com.viperplayer.presentation.search.model.SearchItem
import com.viperplayer.presentation.theme.ViPERPlayerTheme
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

// Playlist song sort options. DEFAULT (insertion order) is first and selected initially.
private val PLAYLIST_SONG_SORT_OPTIONS = listOf(
    SortOption.DEFAULT,
    SortOption.TITLE,
    SortOption.ARTIST,
    SortOption.ALBUM,
    SortOption.DURATION,
)

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

    // If this screen's plugin needs the user to act (sign in, verify...), offer it on errors.
    val actionsViewModel: PluginActionsViewModel = hiltViewModel()
    val pendingActions by actionsViewModel.pendingActions.collectAsStateWithLifecycle()
    val pluginAction = pendingActions.firstOrNull { it.pluginId == viewModel.pluginId }
    val resolvePluginAction = rememberPluginActionResolver { actionsViewModel.refresh() }

    val context = LocalContext.current
    val exportSuccessMsg = stringResource(R.string.playlist_export_success)
    val exportFailureMsg = stringResource(R.string.playlist_export_failure)

    // SAF "create document" launcher — the Composable only hosts it; the ViewModel writes the file.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("audio/x-mpegurl")
    ) { uri ->
        if (uri != null) viewModel.exportToM3u(uri)
    }

    LaunchedEffect(Unit) {
        viewModel.exportEvents.collect { event ->
            val message = when (event) {
                ExportEvent.Success -> exportSuccessMsg
                ExportEvent.Failure -> exportFailureMsg
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    val addToPlaylistController = rememberAddToPlaylistController()

    PlaylistDetailScreenContent(
        rootPadding = rootPadding,
        uiState = uiState,
        currentSong = currentSong,
        isPlaying = isPlaying,
        isEditable = viewModel.isEditable,
        pluginAction = pluginAction,
        onResolvePluginAction = resolvePluginAction,
        onNavigateBack = onNavigateBack,
        onRefresh = viewModel::refresh,
        onSortOrderChange = viewModel::setSortOrder,
        onExport = { exportLauncher.launch(viewModel.suggestedExportFileName) },
        onPlayAll = viewModel::playAll,
        onShuffle = viewModel::shuffle,
        onPlaySong = viewModel::playSong,
        onPlayNext = viewModel::playNext,
        onAddToQueue = viewModel::addToQueue,
        onAddToPlaylist = { addToPlaylistController.show(it) },
        onRemoveSongAt = viewModel::removeSongAt,
        onMoveSong = viewModel::moveSong,
        onRename = viewModel::rename,
        onToggleLike = viewModel::toggleSongLike,
        onNavigateToArtist = onNavigateToArtist,
        onNavigateToAlbum = onNavigateToAlbum,
    )

    // Add-to-playlist picker for a song's options sheet (existing playlists + create new).
    AddToPlaylistSheetHost(controller = addToPlaylistController)
}

@Composable
internal fun PlaylistDetailScreenContent(
    rootPadding: PaddingValues,
    uiState: PlaylistDetailUiState,
    currentSong: Song?,
    isPlaying: Boolean,
    isEditable: Boolean = false,
    pluginAction: PluginPendingAction? = null,
    onResolvePluginAction: (PluginPendingAction) -> Unit = {},
    onNavigateBack: () -> Unit,
    onRefresh: () -> Unit,
    onSortOrderChange: (SortOrder) -> Unit = {},
    onExport: () -> Unit = {},
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onPlaySong: (Song) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit = {},
    onRemoveSongAt: (Int) -> Unit = {},
    onMoveSong: (Int, Int) -> Unit = { _, _ -> },
    onRename: (String) -> Unit = {},
    onToggleLike: (Song) -> Unit,
    onNavigateToArtist: (Artist) -> Unit,
    onNavigateToAlbum: (Album) -> Unit,
) {
    val optionsController = rememberMediaItemOptionsController()
    var editMode by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    val playlist = when (uiState) {
        is PlaylistDetailUiState.Success -> uiState.playlist
        is PlaylistDetailUiState.Loading -> uiState.initialPlaylist
        else -> null
    }
    val title = playlist?.name ?: stringResource(R.string.playlist_detail_fallback_title)

    CollapsingArtworkScaffold(
        artworkUrl = playlist?.artworkUrl,
        title = title,
        onNavigateBack = onNavigateBack,
        actions = {
            // Sorting is a view-mode concern only — hidden in edit mode, where insertion order is being
            // reordered directly. Only offer it when there are songs to sort.
            if (!editMode && uiState is PlaylistDetailUiState.Success && uiState.songs.isNotEmpty()) {
                SortMenu(
                    current = uiState.sortOrder,
                    options = PLAYLIST_SONG_SORT_OPTIONS,
                    onOrderChange = onSortOrderChange,
                )
            }
            if (isEditable && uiState is PlaylistDetailUiState.Success) {
                IconButton(onClick = { editMode = !editMode }) {
                    if (editMode) {
                        Icon(Icons.Default.Done, contentDescription = stringResource(R.string.action_done))
                    } else {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.action_edit))
                    }
                }
            }
            IconButton(onClick = onExport, enabled = (playlist?.songCount ?: 0) > 0 || playlist?.songs?.isNotEmpty() == true) {
                Icon(Icons.Default.FileUpload, contentDescription = stringResource(R.string.playlist_export_m3u))
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_refresh))
            }
            // Overflow menu: rename is only offered for editable local playlists.
            if (isEditable && uiState is PlaylistDetailUiState.Success) {
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.testTag("playlistOverflowMenu")
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.action_more)
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.playlist_rename)) },
                            leadingIcon = {
                                Icon(Icons.Default.Edit, contentDescription = null)
                            },
                            onClick = {
                                showMenu = false
                                showRenameDialog = true
                            },
                            modifier = Modifier.testTag("renamePlaylistMenuItem")
                        )
                    }
                }
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
                ErrorState(
                    message = uiState.message,
                    actionLabel = pluginAction?.title,
                    onAction = pluginAction?.let { action -> { onResolvePluginAction(action) } },
                    onRetry = onRefresh,
                    modifier = Modifier
                        .padding(contentPadding)
                        .padding(rootPadding),
                )
            }

            is PlaylistDetailUiState.Success -> {
                if (editMode) {
                    EditablePlaylistSongList(
                        songs = uiState.songs,
                        contentPadding = contentPadding,
                        rootPadding = rootPadding,
                        onMoveSong = onMoveSong,
                        onRemoveSongAt = onRemoveSongAt,
                        header = {
                            PlaylistMetadataHeader(
                                playlist = uiState.playlist,
                                songCount = uiState.songs.size,
                                onPlayAll = onPlayAll,
                                onShuffle = onShuffle
                            )
                        },
                    )
                } else {
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

                        // Songs list (view mode renders the chosen display order).
                        if (uiState.sortedSongs.isEmpty()) {
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
                            itemsIndexed(uiState.sortedSongs, key = { index, song -> "${song.id}-$index" }) { _, song ->
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
        }

        // Media item options bottom sheet
        MediaItemOptionsSheetHost(
            controller = optionsController,
            onPlay = { if (it is Song) onPlaySong(it) },
            onPlayNext = { if (it is Song) onPlayNext(it) },
            onAddToQueue = { if (it is Song) onAddToQueue(it) },
            onAddToPlaylist = { if (it is Song) onAddToPlaylist(it) },
            onLike = { if (it is Song) onToggleLike(it) },
            onViewArtist = onNavigateToArtist,
            onViewAlbum = onNavigateToAlbum,
        )

        // Rename dialog (only reachable for editable local playlists via the overflow menu).
        if (showRenameDialog) {
            RenamePlaylistDialog(
                currentName = playlist?.name.orEmpty(),
                onConfirm = { newName ->
                    onRename(newName)
                    showRenameDialog = false
                },
                onDismiss = { showRenameDialog = false },
            )
        }
    }
}

/**
 * Dialog for renaming a local playlist. Prefilled with [currentName]; the confirm button is disabled
 * while the field is blank, and confirming forwards the trimmed name to [onConfirm]. Pure UI — no
 * persistence happens here; the caller wires [onConfirm] to the ViewModel.
 */
@Composable
internal fun RenamePlaylistDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(currentName) }
    val isBlank = name.isBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.playlist_rename)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.playlist_rename_label)) },
                singleLine = true,
                isError = isBlank,
                supportingText = if (isBlank) {
                    { Text(stringResource(R.string.playlist_rename_error_blank)) }
                } else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("renamePlaylistField"),
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name.trim()) },
                enabled = !isBlank,
                modifier = Modifier.testTag("renamePlaylistConfirm"),
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/**
 * Reorderable + removable song list shown in a local playlist's edit mode. Backed by the
 * `sh.calvin.reorderable` library: drag the [Modifier.draggableHandle] on a row to move it (each
 * hover-swap updates a transient working copy so the move animates live), and the net move is
 * committed once on drop via [onMoveSong] — keeping the ViewModel/repo's single-transaction position
 * rewrite. Tapping × removes a row via [onRemoveSongAt].
 *
 * The [LazyColumn] carries a header item at list index 0, so the reorderable item indices are offset
 * by one relative to the song list; [HEADER_OFFSET] converts between the two.
 */
@Composable
private fun EditablePlaylistSongList(
    songs: List<Song>,
    contentPadding: PaddingValues,
    rootPadding: PaddingValues,
    onMoveSong: (Int, Int) -> Unit,
    onRemoveSongAt: (Int) -> Unit,
    header: @Composable () -> Unit,
) {
    // Working copy so a drag reorders live without waiting for the ViewModel round-trip. Re-seeded
    // whenever the source list changes (add / remove / the committed reorder coming back).
    var localSongs by remember(songs) { mutableStateOf(songs) }

    // The song index (into [localSongs]) where the current drag began; the net move start→end is what
    // we commit on drop, matching the ViewModel's atomic moveSong(from, to) contract.
    var dragStartSongIndex by remember { mutableStateOf<Int?>(null) }

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        // Library item indices include the header at index 0; convert to song-list indices.
        val fromSong = from.index - HEADER_OFFSET
        val toSong = to.index - HEADER_OFFSET
        if (fromSong in localSongs.indices && toSong in localSongs.indices) {
            localSongs = localSongs.toMutableList().apply { add(toSong, removeAt(fromSong)) }
        }
    }

    LazyColumn(
        state = lazyListState,
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = rootPadding
    ) {
        item { header() }

        itemsIndexed(localSongs, key = { _, song -> song.id.toString() }) { index, song ->
            ReorderableItem(reorderableState, key = song.id.toString()) { isDragging ->
                val elevation = if (isDragging) 8.dp else 0.dp
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .shadow(elevation)
                        .background(
                            if (isDragging) MaterialTheme.colorScheme.surfaceContainerHighest
                            else MaterialTheme.colorScheme.surface
                        )
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = stringResource(R.string.playlist_reorder_song),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .testTag("dragHandle_$index")
                            .draggableHandle(
                                onDragStarted = {
                                    dragStartSongIndex = localSongs.indexOfFirst {
                                        it.id.toString() == song.id.toString()
                                    }.takeIf { it >= 0 }
                                },
                                onDragStopped = {
                                    val from = dragStartSongIndex
                                    val to = localSongs.indexOfFirst {
                                        it.id.toString() == song.id.toString()
                                    }
                                    if (from != null && to >= 0 && from != to) {
                                        onMoveSong(from, to)
                                    }
                                    dragStartSongIndex = null
                                },
                            )
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = song.artistNames.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = { onRemoveSongAt(index) },
                        modifier = Modifier.testTag("removeSong_$index")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.playlist_remove_song),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** The playlist metadata header occupies LazyColumn item index 0, ahead of the song rows. */
private const val HEADER_OFFSET = 1

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
        id = MediaId.Plugin("plugin", "playlist1"),
        name = "Rock Classics",
        ownerName = "ViPER Player",
        artworkUrl = "https://raw.githubusercontent.com/coil-kt/coil/master/logo.png",
        description = "The best rock songs of all time."
    )

    val songs = listOf(
        Song(
            id = MediaId.Plugin("plugin", "song1"),
            title = "Bohemian Rhapsody",
            artists = emptyList(), // Simplified for preview
            album = null,
            durationMs = 354000,
            trackNumber = 1,
            discNumber = 1
        ),
        Song(
            id = MediaId.Plugin("plugin", "song2"),
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
