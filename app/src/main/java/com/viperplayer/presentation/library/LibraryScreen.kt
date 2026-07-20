package com.viperplayer.presentation.library

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viperplayer.R
import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.model.SortOption
import com.viperplayer.domain.model.SortOrder
import com.viperplayer.domain.model.SortView
import com.viperplayer.presentation.common.AddToPlaylistSheetHost
import com.viperplayer.presentation.common.ListItem
import com.viperplayer.presentation.common.ListItemLeadingArtwork
import com.viperplayer.presentation.common.MediaItemOptionsSheetHost
import com.viperplayer.presentation.common.SortMenu
import com.viperplayer.presentation.common.labelRes
import com.viperplayer.presentation.common.rememberAddToPlaylistController
import com.viperplayer.presentation.common.rememberMediaItemOptionsController
import com.viperplayer.presentation.common.revealOnAppear
import com.viperplayer.presentation.common.components.SectionLabel
import com.viperplayer.presentation.common.components.SelectableChip
import com.viperplayer.presentation.ktx.bottom
import com.viperplayer.presentation.ktx.plus
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

/** Corner radius for the pinned Liked / Downloads / Following shortcut tiles. */
private val ShortcutTileCorner = 16.dp

/**
 * Library screen (mockup 3a — "flat & fast"): a collapsed toolbar (title + search + overflow), a
 * filter-chip row in place of the old tab bar, pinned Liked / Downloads / Following tiles and a
 * count + sort header, all scrolling above the list. Behavior, navigation callbacks and the per-tab
 * sort/import logic are unchanged from the previous version — this is a presentation-only restyle.
 *
 * [onNavigateToSearch] is additive (defaults to a no-op) so the toolbar's search affordance can be
 * wired from the navigation graph; Search is also reachable from the bottom bar.
 */
@Composable
fun LibraryScreen(
    rootPadding: PaddingValues,
    onNavigateToAlbum: (Album) -> Unit,
    onNavigateToArtist: (Artist) -> Unit,
    onNavigateToPlaylist: (Playlist) -> Unit,
    onNavigateToDownloads: () -> Unit = {},
    onNavigateToFollowing: () -> Unit = {},
    onNavigateToCustomizeTabs: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
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
            LibraryToolbar(
                showImport = uiState.selectedTab == LibraryTab.PLAYLISTS,
                onSearch = onNavigateToSearch,
                onImport = {
                    importLauncher.launch(
                        arrayOf(
                            "audio/x-mpegurl",
                            "audio/mpegurl",
                            "application/vnd.apple.mpegurl",
                            "*/*"
                        )
                    )
                },
                onCustomizeTabs = onNavigateToCustomizeTabs,
                onRefresh = { viewModel.refresh() },
            )

            // Filter chips replace the old tab row — only the visible tabs, in the user's configured
            // order. The selected chip fills (secondaryContainer + check); the rest are outlined.
            LibraryFilterChips(
                tabs = uiState.visibleTabs,
                selected = uiState.selectedTab,
                onSelect = { viewModel.selectTab(it) },
            )

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
                val listContentPadding = PaddingValues(top = 8.dp) + rootPadding.bottom()
                when (uiState.selectedTab) {
                    LibraryTab.SONGS -> {
                        if (uiState.songs.isEmpty()) {
                            EmptyLibraryContent(stringResource(R.string.library_empty_songs))
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = listContentPadding
                            ) {
                                item(key = "songs_header") {
                                    LibraryListHeader(
                                        countLabel = pluralStringResource(
                                            R.plurals.library_song_count,
                                            uiState.songs.size,
                                            uiState.songs.size
                                        ),
                                        sort = uiState.songsSort,
                                        sortOptions = SONG_SORT_OPTIONS,
                                        onOrderChange = { viewModel.setSortOrder(SortView.LIBRARY_SONGS, it) },
                                        onLiked = { viewModel.selectTab(LibraryTab.PLAYLISTS) },
                                        onNavigateToDownloads = onNavigateToDownloads,
                                        onNavigateToFollowing = onNavigateToFollowing,
                                    )
                                }
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
                            EmptyLibraryContent(stringResource(R.string.library_empty_albums))
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = listContentPadding
                            ) {
                                item(key = "albums_header") {
                                    LibraryListHeader(
                                        countLabel = pluralStringResource(
                                            R.plurals.library_album_count,
                                            uiState.albums.size,
                                            uiState.albums.size
                                        ),
                                        sort = uiState.albumsSort,
                                        sortOptions = ALBUM_SORT_OPTIONS,
                                        onOrderChange = { viewModel.setSortOrder(SortView.LIBRARY_ALBUMS, it) },
                                        onLiked = { viewModel.selectTab(LibraryTab.PLAYLISTS) },
                                        onNavigateToDownloads = onNavigateToDownloads,
                                        onNavigateToFollowing = onNavigateToFollowing,
                                    )
                                }
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
                            EmptyLibraryContent(stringResource(R.string.library_empty_artists))
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = listContentPadding
                            ) {
                                item(key = "artists_header") {
                                    LibraryListHeader(
                                        countLabel = pluralStringResource(
                                            R.plurals.library_artist_count,
                                            uiState.artists.size,
                                            uiState.artists.size
                                        ),
                                        sort = uiState.artistsSort,
                                        sortOptions = ARTIST_SORT_OPTIONS,
                                        onOrderChange = { viewModel.setSortOrder(SortView.LIBRARY_ARTISTS, it) },
                                        useNameLabel = true,
                                        onLiked = { viewModel.selectTab(LibraryTab.PLAYLISTS) },
                                        onNavigateToDownloads = onNavigateToDownloads,
                                        onNavigateToFollowing = onNavigateToFollowing,
                                    )
                                }
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
                            EmptyLibraryContent(stringResource(R.string.library_empty_playlists))
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = listContentPadding
                            ) {
                                item(key = "playlists_header") {
                                    LibraryListHeader(
                                        countLabel = pluralStringResource(
                                            R.plurals.library_playlist_count,
                                            uiState.playlists.size,
                                            uiState.playlists.size
                                        ),
                                        sort = uiState.playlistsSort,
                                        sortOptions = PLAYLIST_SORT_OPTIONS,
                                        onOrderChange = { viewModel.setSortOrder(SortView.LIBRARY_PLAYLISTS, it) },
                                        useNameLabel = true,
                                        onLiked = { viewModel.selectTab(LibraryTab.PLAYLISTS) },
                                        onNavigateToDownloads = onNavigateToDownloads,
                                        onNavigateToFollowing = onNavigateToFollowing,
                                    )
                                }
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
                                            "$owner • ${
                                                pluralStringResource(
                                                    R.plurals.song_count,
                                                    playlist.songCount,
                                                    playlist.songCount
                                                )
                                            }"
                                        }
                                            ?: pluralStringResource(
                                                R.plurals.song_count,
                                                playlist.songCount,
                                                playlist.songCount
                                            ),
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

/**
 * The collapsed top toolbar: the "Library" title, a search affordance, and an overflow menu holding
 * the low-frequency actions (import playlist — only on the Playlists tab — plus Customize tabs and
 * Refresh). Frequently-used shortcuts (Liked/Downloads/Following) live in the pinned tiles instead.
 */
@Composable
private fun LibraryToolbar(
    showImport: Boolean,
    onSearch: () -> Unit,
    onImport: () -> Unit,
    onCustomizeTabs: () -> Unit,
    onRefresh: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.library_title),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        IconButton(onClick = onSearch) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = stringResource(R.string.library_search),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = stringResource(R.string.library_more_options),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                if (showImport) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.playlist_import)) },
                        leadingIcon = {
                            Icon(Icons.Filled.FileDownload, contentDescription = null)
                        },
                        onClick = {
                            menuExpanded = false
                            onImport()
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.library_customize_tabs)) },
                    leadingIcon = {
                        Icon(Icons.Rounded.Tune, contentDescription = null)
                    },
                    onClick = {
                        menuExpanded = false
                        onCustomizeTabs()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_refresh)) },
                    leadingIcon = {
                        Icon(Icons.Rounded.Refresh, contentDescription = null)
                    },
                    onClick = {
                        menuExpanded = false
                        onRefresh()
                    },
                )
            }
        }
    }
}

/**
 * The horizontally-scrollable filter-chip row that replaces the old [androidx.compose.material3.PrimaryTabRow].
 * Renders one [SelectableChip] per visible tab, in the user's configured order; the selected chip fills
 * and shows a check. Each chip keeps the `libraryTab_<TAB>` test tag the old Tab carried.
 */
@Composable
private fun LibraryFilterChips(
    tabs: List<LibraryTab>,
    selected: LibraryTab,
    onSelect: (LibraryTab) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tabs.forEach { tab ->
            SelectableChip(
                text = stringResource(tab.labelRes),
                selected = tab == selected,
                onClick = { onSelect(tab) },
                modifier = Modifier.testTag("libraryTab_${tab.name}"),
            )
        }
    }
}

/**
 * The scroll-away header shown above each tab's list: the pinned Liked / Downloads / Following tiles
 * followed by a "N items" count and the sort control. [SortMenu] carries all the sort logic (and its
 * `sortMenuButton` test tag); the current order's label is shown beside it to match the mockup.
 */
@Composable
private fun LibraryListHeader(
    countLabel: String,
    sort: SortOrder,
    sortOptions: List<SortOption>,
    onOrderChange: (SortOrder) -> Unit,
    onLiked: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToFollowing: () -> Unit,
    modifier: Modifier = Modifier,
    useNameLabel: Boolean = false,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ShortcutTile(
                icon = Icons.Rounded.Favorite,
                label = stringResource(R.string.library_liked),
                onClick = onLiked,
                modifier = Modifier.weight(1f),
            )
            ShortcutTile(
                icon = Icons.Rounded.Download,
                label = stringResource(R.string.downloads_title),
                onClick = onNavigateToDownloads,
                modifier = Modifier.weight(1f),
            )
            ShortcutTile(
                icon = Icons.Rounded.Group,
                label = stringResource(R.string.following_title),
                onClick = onNavigateToFollowing,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = countLabel,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
            // Current-order label sits next to the sort control (which renders the sort glyph and owns
            // the menu + all sort logic / test tag), matching the mockup's labelled sort affordance.
            Text(
                text = stringResource(sort.option.labelRes(useNameLabel)),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            SortMenu(
                current = sort,
                options = sortOptions,
                onOrderChange = onOrderChange,
                useNameLabel = useNameLabel,
            )
        }
    }
}

/**
 * A single pinned shortcut tile (Liked / Downloads / Following): a flat surfaceContainerHighest panel
 * with a primary-tinted filled icon and a label. Navigation-only — the counts in the mockup are not
 * available to the presentation layer without new data plumbing, so the tile shows just its label.
 */
@Composable
private fun ShortcutTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(ShortcutTileCorner),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
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
                imageVector = Icons.Rounded.LibraryMusic,
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
    SectionLabel(
        text = title,
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
