package com.viperplayer.presentation.library

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.viperplayer.presentation.common.revealOnAppear
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.viperplayer.R
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.MediaItem
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.model.SortOption
import com.viperplayer.domain.model.SortView
import com.viperplayer.presentation.common.ListItem
import com.viperplayer.presentation.common.ListItemLeadingArtwork
import com.viperplayer.presentation.common.SortMenu
import com.viperplayer.presentation.common.AddToPlaylistSheetHost
import com.viperplayer.presentation.common.rememberAddToPlaylistController
import com.viperplayer.presentation.common.MediaItemOptionsSheetHost
import com.viperplayer.presentation.common.rememberMediaItemOptionsController
import com.viperplayer.presentation.ktx.bottom
import com.viperplayer.presentation.ktx.with
import com.viperplayer.presentation.search.model.ItemBadge
import com.viperplayer.presentation.search.model.SearchItem

// Per-tab sort options. DEFAULT is always first (selected initially → order unchanged). Only fields the
// domain models actually carry are offered, so every option produces a real ordering.
private val SONG_SORT_OPTIONS = listOf(
    SortOption.DEFAULT,
    SortOption.TITLE,
    SortOption.ARTIST,
    SortOption.ALBUM,
    SortOption.DURATION,
    SortOption.TRACK_NUMBER,
)
private val ALBUM_SORT_OPTIONS = listOf(
    SortOption.DEFAULT,
    SortOption.TITLE,
    SortOption.ALBUM_ARTIST,
    SortOption.YEAR,
)
private val ARTIST_SORT_OPTIONS = listOf(SortOption.DEFAULT, SortOption.TITLE)
private val PLAYLIST_SORT_OPTIONS = listOf(SortOption.DEFAULT, SortOption.TITLE)

@Composable
fun LibraryScreen(
    rootPadding: PaddingValues,
    onNavigateToAlbum: (Album) -> Unit,
    onNavigateToArtist: (Artist) -> Unit,
    onNavigateToPlaylist: (Playlist) -> Unit,
    onNavigateToDownloads: () -> Unit = {},
    onNavigateToFollowing: () -> Unit = {},
    onNavigateToCustomizeTabs: () -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()

    val optionsController = rememberMediaItemOptionsController()
    val addToPlaylistController = rememberAddToPlaylistController()

    val context = LocalContext.current
    val importFailureMsg = stringResource(R.string.playlist_import_failure)

    // SAF "open document" launcher — the Composable only hosts it; the ViewModel reads + imports.
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importPlaylist(uri)
    }

    LaunchedEffect(Unit) {
        viewModel.importEvents.collect { event ->
            val message = when (event) {
                is ImportEvent.Success ->
                    context.getString(R.string.playlist_import_success, event.imported, event.skipped)
                ImportEvent.Failure -> importFailureMsg
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        modifier = Modifier.padding(rootPadding.with(bottom = 0.dp))
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.library_title),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Per-tab sort menu — its options, current order, and target view follow the tab.
                    when (uiState.selectedTab) {
                        LibraryTab.SONGS -> SortMenu(
                            current = uiState.songsSort,
                            options = SONG_SORT_OPTIONS,
                            onOrderChange = { viewModel.setSortOrder(SortView.LIBRARY_SONGS, it) },
                        )
                        LibraryTab.ALBUMS -> SortMenu(
                            current = uiState.albumsSort,
                            options = ALBUM_SORT_OPTIONS,
                            onOrderChange = { viewModel.setSortOrder(SortView.LIBRARY_ALBUMS, it) },
                        )
                        LibraryTab.ARTISTS -> SortMenu(
                            current = uiState.artistsSort,
                            options = ARTIST_SORT_OPTIONS,
                            onOrderChange = { viewModel.setSortOrder(SortView.LIBRARY_ARTISTS, it) },
                            useNameLabel = true,
                        )
                        LibraryTab.PLAYLISTS -> SortMenu(
                            current = uiState.playlistsSort,
                            options = PLAYLIST_SORT_OPTIONS,
                            onOrderChange = { viewModel.setSortOrder(SortView.LIBRARY_PLAYLISTS, it) },
                            useNameLabel = true,
                        )
                    }
                    if (uiState.selectedTab == LibraryTab.PLAYLISTS) {
                        IconButton(onClick = {
                            importLauncher.launch(
                                arrayOf(
                                    "audio/x-mpegurl",
                                    "audio/mpegurl",
                                    "application/vnd.apple.mpegurl",
                                    "*/*"
                                )
                            )
                        }) {
                            Icon(
                                Icons.Default.FileDownload,
                                contentDescription = stringResource(R.string.playlist_import)
                            )
                        }
                    }
                    IconButton(onClick = onNavigateToFollowing) {
                        Icon(
                            Icons.Default.People,
                            contentDescription = stringResource(R.string.following_title)
                        )
                    }
                    IconButton(onClick = onNavigateToDownloads) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = stringResource(R.string.downloads_title)
                        )
                    }
                    IconButton(onClick = onNavigateToCustomizeTabs) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = stringResource(R.string.library_customize_tabs)
                        )
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            }

            // Tab row — only the visible tabs, in the user's configured order. The selected index is
            // clamped into range so a just-hidden selection never points past the end for a frame.
            val selectedIndex = uiState.visibleTabs.indexOf(uiState.selectedTab).coerceAtLeast(0)
            PrimaryTabRow(selectedTabIndex = selectedIndex) {
                uiState.visibleTabs.forEach { tab ->
                    Tab(
                        selected = uiState.selectedTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = { Text(stringResource(tab.labelRes)) },
                        modifier = Modifier.testTag("libraryTab_${tab.name}")
                    )
                }
            }

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .padding(rootPadding.bottom())
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator()
                }
            } else {
                when (uiState.selectedTab) {
                    LibraryTab.SONGS -> {
                        if (uiState.songs.isEmpty()) {
                            EmptyLibraryContent("No songs in your library")
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = rootPadding.bottom()
                            ) {
                                itemsIndexed(uiState.songs, key = { index, song -> "${song.id}-$index" }) { index, song ->
                                    ListItem(
                                        type = SearchItem.Type.SONG,
                                        title = song.title,
                                        badges = if (song.isExplicit) listOf(ItemBadge.EXPLICIT) else emptyList(),
                                        subtitle = song.artistNames,
                                        artworkUrl = song.artworkUrl,
                                        isActive = currentSong?.id == song.id,
                                        isPlaying = currentSong?.id == song.id && isPlaying,
                                        onClick = if (song.isPlayable) {
                                            { viewModel.playSong(song) }
                                        } else null,
                                        onMoreClick = { optionsController.show(song) },
                                        onLongClick = { optionsController.show(song) },
                                        onPlayNext = if (song.isPlayable) {
                                            { viewModel.playNext(song) }
                                        } else null,
                                        onAddToQueue = if (song.isPlayable) {
                                            { viewModel.addToQueue(song) }
                                        } else null,
                                        modifier = Modifier
                                            .animateItem().revealOnAppear(index)
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

                    LibraryTab.ALBUMS -> {
                        if (uiState.albums.isEmpty()) {
                            EmptyLibraryContent("No albums in your library")
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = rootPadding.bottom()
                            ) {
                                itemsIndexed(uiState.albums, key = { index, album -> "${album.id}-$index" }) { index, album ->
                                    ListItem(
                                        type = SearchItem.Type.ALBUM,
                                        title = album.name,
                                        badges = emptyList(),
                                        subtitle = album.artists.joinToString { it.name }
                                            .takeIf { it.isNotEmpty() },
                                        artworkUrl = album.artworkUrl,
                                        isActive = false,
                                        isPlaying = false,
                                        onClick = { onNavigateToAlbum(album) },
                                        onMoreClick = { optionsController.show(album) },
                                        onLongClick = { optionsController.show(album) },
                                        modifier = Modifier
                                            .animateItem().revealOnAppear(index)
                                            .fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }

                    LibraryTab.ARTISTS -> {
                        if (uiState.artists.isEmpty()) {
                            EmptyLibraryContent("No artists in your library")
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = rootPadding.bottom()
                            ) {
                                itemsIndexed(uiState.artists, key = { index, artist -> "${artist.id}-$index" }) { index, artist ->
                                    ListItem(
                                        type = SearchItem.Type.ARTIST,
                                        title = artist.name,
                                        badges = emptyList(),
                                        subtitle = null,
                                        artworkUrl = artist.imageUrl,
                                        isActive = false,
                                        isPlaying = false,
                                        onClick = { onNavigateToArtist(artist) },
                                        onMoreClick = { optionsController.show(artist) },
                                        onLongClick = { optionsController.show(artist) },
                                        modifier = Modifier
                                            .animateItem().revealOnAppear(index)
                                            .fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }

                    LibraryTab.PLAYLISTS -> {
                        if (uiState.playlists.isEmpty() && uiState.autoPlaylists.isEmpty()) {
                            EmptyLibraryContent("No playlists in your library")
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = rootPadding.bottom()
                            ) {
                                // Dynamic auto-playlists (Recently Added / Most Played / …) in their
                                // own section, above the regular playlists. Auto entries open the same
                                // PlaylistDetail screen (their MediaId's plugin is "auto").
                                if (uiState.autoPlaylists.isNotEmpty()) {
                                    item(key = "auto_playlists_header") {
                                        LibrarySectionHeader(
                                            title = stringResource(R.string.auto_playlists_section),
                                            modifier = Modifier.animateItem()
                                        )
                                    }
                                    items(
                                        uiState.autoPlaylists,
                                        key = { playlist -> "auto-${playlist.id}" }
                                    ) { playlist ->
                                        AutoPlaylistRow(
                                            playlist = playlist,
                                            onClick = onNavigateToPlaylist,
                                            modifier = Modifier
                                                .animateItem()
                                                .fillMaxWidth()
                                        )
                                    }
                                }
                                itemsIndexed(uiState.playlists, key = { index, playlist -> "${playlist.id}-$index" }) { index, playlist ->
                                    ListItem(
                                        type = SearchItem.Type.PLAYLIST,
                                        title = playlist.name,
                                        badges = emptyList(),
                                        subtitle = playlist.ownerName?.let { owner ->
                                            "$owner • ${playlist.songCount} ${if (playlist.songCount == 1) "song" else "songs"}"
                                        }
                                            ?: "${playlist.songCount} ${if (playlist.songCount == 1) "song" else "songs"}",
                                        artworkUrl = playlist.artworkUrl,
                                        isActive = false,
                                        isPlaying = false,
                                        onClick = { onNavigateToPlaylist(playlist) },
                                        onMoreClick = { optionsController.show(playlist) },
                                        onLongClick = { optionsController.show(playlist) },
                                        modifier = Modifier
                                            .animateItem().revealOnAppear(index)
                                            .fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Media item options bottom sheet
        MediaItemOptionsSheetHost(
            controller = optionsController,
            onPlay = {
                when (it) {
                    is Song -> viewModel.playSong(it)
                    is Album -> viewModel.playAlbum(it)
                    is Playlist -> viewModel.playPlaylist(it)
                    else -> {}
                }
            },
            onPlayNext = {
                when (it) {
                    is Song -> viewModel.playNext(it)
                    is Playlist -> viewModel.playPlaylistNext(it)
                    else -> {}
                }
            },
            onAddToQueue = {
                when (it) {
                    is Song -> viewModel.addToQueue(it)
                    is Album -> viewModel.addAlbumToQueue(it)
                    is Playlist -> viewModel.addPlaylistToQueue(it)
                    else -> {}
                }
            },
            onShuffle = {
                when (it) {
                    is Album -> viewModel.shuffleAlbum(it)
                    is Playlist -> viewModel.shufflePlaylist(it)
                    else -> {}
                }
            },
            onLike = {
                when (it) {
                    is Song -> viewModel.toggleLike(it)
                    is Playlist -> viewModel.togglePlaylistLike(it)
                    else -> {}
                }
            },
            onDownload = {
                when (it) {
                    is Song -> viewModel.downloadSong(it)
                    else -> {}
                }
            },
            onAddToPlaylist = { if (it is Song) addToPlaylistController.show(it) },
            onViewArtist = onNavigateToArtist,
            onViewAlbum = onNavigateToAlbum,
        )

        // Add-to-playlist picker for a song's options sheet (existing playlists + create new).
        AddToPlaylistSheetHost(controller = addToPlaylistController)
    }
}

@Composable
fun EmptyLibraryContent(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.LibraryMusic,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** A small section header (e.g. "Auto-playlists") for a list of library items. */
@Composable
fun LibrarySectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

/**
 * One row for a dynamic auto-playlist (Recently Added / Most Played / …). A base [ListItem] with no
 * trailing "more" button — auto-playlists are computed, not editable, so they have no per-item options.
 * Its subtitle is the playlist description, falling back to a localized song count. Extracted so both
 * the Library screen and its UI test render the exact production row.
 */
@Composable
fun AutoPlaylistRow(
    playlist: Playlist,
    onClick: (Playlist) -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        title = playlist.name,
        badges = emptyList(),
        subtitle = playlist.description
            ?: pluralStringResource(R.plurals.song_count, playlist.songCount, playlist.songCount),
        isActive = false,
        leadingContent = {
            ListItemLeadingArtwork(
                artworkUrl = playlist.artworkUrl,
                type = SearchItem.Type.PLAYLIST,
                isActive = false,
                isPlaying = false,
            )
        },
        trailingContent = {},
        onClick = { onClick(playlist) },
        modifier = modifier,
    )
}

