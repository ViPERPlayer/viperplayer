package com.viperplayer.presentation.library

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viperplayer.R
import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.Genre
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.MediaItem
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
import com.viperplayer.presentation.search.model.SearchItem
import kotlinx.coroutines.launch

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
 * End padding reserved on list rows so their content clears the pinned A-Z rail when it is shown. The
 * rail is only present for an alphabetically-sorted list, so this inset is applied conditionally.
 */
private val RailReservedWidth = 18.dp

/**
 * Library screen (mockup 3a — "flat & fast"): a collapsed toolbar (title + search + overflow) and a
 * filter-chip row in place of the old tab bar.
 *
 * **Default (no chip selected):** a single unified feed of ALL library items — songs, albums, artists
 * and playlists interleaved — ordered by most-recently-played (newest first). Tapping a chip filters to
 * just that type (with its own sort menu); tapping the selected chip again deselects it, returning to
 * the unified feed. All list-building and recency logic lives in [LibraryViewModel]; this screen only
 * renders state and forwards events.
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
    onNavigateToGenre: (Genre) -> Unit = {},
    onNavigateToDownloads: () -> Unit = {},
    onNavigateToFollowing: () -> Unit = {},
    onNavigateToCustomizeTabs: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onViewDetails: (MediaItem) -> Unit = {},
    onMoreLikeThis: (Song) -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val shortcutCounts by viewModel.shortcutCounts.collectAsStateWithLifecycle()
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
                searchQuery = uiState.searchQuery,
                onSearchQueryChange = { viewModel.onSearchQueryChange(it) },
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
            // order. A chip fills (secondaryContainer + check) only when it is the selected type; with
            // no selection (the unified default) every chip is outlined. Chips are toggleable.
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
            } else if (uiState.searchQuery.isNotBlank() && isCurrentTabFilteredEmpty(uiState)) {
                // The in-place filter matched nothing on the current tab — a distinct no-results state
                // (not the "your library is empty" copy) so the user knows to broaden the query.
                EmptyLibraryContent(
                    stringResource(R.string.library_search_no_results, uiState.searchQuery)
                )
            } else {
                val listContentPadding = PaddingValues(top = 8.dp) + rootPadding.bottom()
                when (uiState.selectedTab) {
                    // Default: unified, recency-sorted, all-types feed. No A-Z rail (a rail is
                    // meaningless without alphabetical order) and no per-type sort control.
                    null -> UnifiedLibraryList(
                        items = uiState.unified,
                        shortcutCounts = shortcutCounts,
                        currentSongId = currentSong?.id,
                        isPlaying = isPlaying,
                        contentPadding = listContentPadding,
                        onPlaySong = { viewModel.playSong(it) },
                        onSongMore = { optionsController.show(it) },
                        onSongPlayNext = { viewModel.playNext(it) },
                        onSongAddToQueue = { viewModel.addToQueue(it) },
                        onNavigateToAlbum = onNavigateToAlbum,
                        onAlbumMore = { optionsController.show(it) },
                        onNavigateToArtist = onNavigateToArtist,
                        onArtistMore = { optionsController.show(it) },
                        onNavigateToPlaylist = onNavigateToPlaylist,
                        onPlaylistMore = { optionsController.show(it) },
                        onLiked = { viewModel.selectTab(LibraryTab.PLAYLISTS) },
                        onNavigateToDownloads = onNavigateToDownloads,
                        onNavigateToFollowing = onNavigateToFollowing,
                    )

                    LibraryTab.SONGS -> {
                        if (uiState.songs.isEmpty()) {
                            EmptyLibraryContent(stringResource(R.string.library_empty_songs))
                        } else {
                            SongsLibraryList(
                                songs = uiState.songs,
                                shortcutCounts = shortcutCounts,
                                currentSongId = currentSong?.id,
                                isPlaying = isPlaying,
                                sort = uiState.songsSort,
                                contentPadding = listContentPadding,
                                onOrderChange = { viewModel.setSortOrder(SortView.LIBRARY_SONGS, it) },
                                onPlaySong = { viewModel.playSong(it) },
                                onSongMore = { optionsController.show(it) },
                                onSongPlayNext = { viewModel.playNext(it) },
                                onSongAddToQueue = { viewModel.addToQueue(it) },
                                onLiked = { viewModel.selectTab(LibraryTab.PLAYLISTS) },
                                onNavigateToDownloads = onNavigateToDownloads,
                                onNavigateToFollowing = onNavigateToFollowing,
                            )
                        }
                    }

                    LibraryTab.ALBUMS -> {
                        if (uiState.albums.isEmpty()) {
                            EmptyLibraryContent(stringResource(R.string.library_empty_albums))
                        } else {
                            AlbumsLibraryList(
                                albums = uiState.albums,
                                shortcutCounts = shortcutCounts,
                                sort = uiState.albumsSort,
                                contentPadding = listContentPadding,
                                onOrderChange = { viewModel.setSortOrder(SortView.LIBRARY_ALBUMS, it) },
                                onNavigateToAlbum = onNavigateToAlbum,
                                onAlbumMore = { optionsController.show(it) },
                                onLiked = { viewModel.selectTab(LibraryTab.PLAYLISTS) },
                                onNavigateToDownloads = onNavigateToDownloads,
                                onNavigateToFollowing = onNavigateToFollowing,
                            )
                        }
                    }

                    LibraryTab.ARTISTS -> {
                        if (uiState.artists.isEmpty()) {
                            EmptyLibraryContent(stringResource(R.string.library_empty_artists))
                        } else {
                            ArtistsLibraryList(
                                artists = uiState.artists,
                                shortcutCounts = shortcutCounts,
                                sort = uiState.artistsSort,
                                contentPadding = listContentPadding,
                                onOrderChange = { viewModel.setSortOrder(SortView.LIBRARY_ARTISTS, it) },
                                onNavigateToArtist = onNavigateToArtist,
                                onArtistMore = { optionsController.show(it) },
                                onLiked = { viewModel.selectTab(LibraryTab.PLAYLISTS) },
                                onNavigateToDownloads = onNavigateToDownloads,
                                onNavigateToFollowing = onNavigateToFollowing,
                            )
                        }
                    }

                    LibraryTab.GENRES -> {
                        if (uiState.genres.isEmpty()) {
                            EmptyLibraryContent(stringResource(R.string.library_empty_genres))
                        } else {
                            GenresLibraryList(
                                genres = uiState.genres,
                                shortcutCounts = shortcutCounts,
                                contentPadding = listContentPadding,
                                onNavigateToGenre = onNavigateToGenre,
                                onLiked = { viewModel.selectTab(LibraryTab.PLAYLISTS) },
                                onNavigateToDownloads = onNavigateToDownloads,
                                onNavigateToFollowing = onNavigateToFollowing,
                            )
                        }
                    }

                    LibraryTab.PLAYLISTS -> {
                        if (uiState.playlists.isEmpty() && uiState.autoPlaylists.isEmpty()) {
                            EmptyLibraryContent(stringResource(R.string.library_empty_playlists))
                        } else {
                            PlaylistsLibraryList(
                                playlists = uiState.playlists,
                                autoPlaylists = uiState.autoPlaylists,
                                shortcutCounts = shortcutCounts,
                                sort = uiState.playlistsSort,
                                contentPadding = listContentPadding,
                                onOrderChange = { viewModel.setSortOrder(SortView.LIBRARY_PLAYLISTS, it) },
                                onNavigateToPlaylist = onNavigateToPlaylist,
                                onPlaylistMore = { optionsController.show(it) },
                                onLiked = { viewModel.selectTab(LibraryTab.PLAYLISTS) },
                                onNavigateToDownloads = onNavigateToDownloads,
                                onNavigateToFollowing = onNavigateToFollowing,
                            )
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
            onViewDetails = onViewDetails,
            onMoreLikeThis = { if (it is Song) onMoreLikeThis(it) },
        )

        // Add-to-playlist picker for a song's options sheet (existing playlists + create new).
        AddToPlaylistSheetHost(controller = addToPlaylistController)
    }
}

/**
 * True when the currently-selected tab's already-filtered list is empty. Read only to decide whether to
 * show the "no results" state for a non-blank filter query; which list is relevant is a pure function of
 * the selected tab, so this carries no data logic (the filtering itself happened in the ViewModel).
 */
private fun isCurrentTabFilteredEmpty(uiState: LibraryUiState): Boolean = when (uiState.selectedTab) {
    null -> uiState.unified.isEmpty()
    LibraryTab.SONGS -> uiState.songs.isEmpty()
    LibraryTab.ALBUMS -> uiState.albums.isEmpty()
    LibraryTab.ARTISTS -> uiState.artists.isEmpty()
    LibraryTab.GENRES -> uiState.genres.isEmpty()
    // Auto-playlists (Recently Added / Most Played …) aren't name-filtered, so only claim "no results"
    // when both the filtered playlists and the auto section are empty.
    LibraryTab.PLAYLISTS -> uiState.playlists.isEmpty() && uiState.autoPlaylists.isEmpty()
}

/**
 * The default unified feed: songs/albums/artists/playlists interleaved by most-recently-played. Rows
 * tap to their natural destination (song → play, others → navigate). No A-Z rail (unordered
 * alphabetically) and no sort control — the recency order is fixed.
 */
@Composable
private fun UnifiedLibraryList(
    items: List<LibraryFeedItem>,
    shortcutCounts: LibraryShortcutCounts,
    currentSongId: MediaId?,
    isPlaying: Boolean,
    contentPadding: PaddingValues,
    onPlaySong: (Song) -> Unit,
    onSongMore: (Song) -> Unit,
    onSongPlayNext: (Song) -> Unit,
    onSongAddToQueue: (Song) -> Unit,
    onNavigateToAlbum: (Album) -> Unit,
    onAlbumMore: (Album) -> Unit,
    onNavigateToArtist: (Artist) -> Unit,
    onArtistMore: (Artist) -> Unit,
    onNavigateToPlaylist: (Playlist) -> Unit,
    onPlaylistMore: (Playlist) -> Unit,
    onLiked: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToFollowing: () -> Unit,
) {
    if (items.isEmpty()) {
        EmptyLibraryContent(stringResource(R.string.library_empty_all))
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        item(key = "unified_header") {
            LibraryListHeader(
                countLabel = pluralStringResource(
                    R.plurals.library_item_count,
                    items.size,
                    items.size
                ),
                shortcutCounts = shortcutCounts,
                sort = null,
                sortOptions = emptyList(),
                onOrderChange = {},
                onLiked = onLiked,
                onNavigateToDownloads = onNavigateToDownloads,
                onNavigateToFollowing = onNavigateToFollowing,
            )
        }
        itemsIndexed(items, key = { _, item -> item.stableKey }) { index, item ->
            val rowModifier = Modifier
                .animateItem()
                .revealOnAppear(index)
                .fillMaxWidth()
            when (item) {
                is LibraryFeedItem.SongItem -> {
                    val song = item.song
                    SongRow(
                        song = song,
                        isActive = currentSongId == song.id,
                        isPlaying = currentSongId == song.id && isPlaying,
                        onPlay = onPlaySong,
                        onMore = onSongMore,
                        onPlayNext = onSongPlayNext,
                        onAddToQueue = onSongAddToQueue,
                        modifier = rowModifier,
                    )
                }
                is LibraryFeedItem.AlbumItem -> AlbumRow(
                    album = item.album,
                    onClick = onNavigateToAlbum,
                    onMore = onAlbumMore,
                    modifier = rowModifier,
                )
                is LibraryFeedItem.ArtistItem -> ArtistRow(
                    artist = item.artist,
                    onClick = onNavigateToArtist,
                    onMore = onArtistMore,
                    modifier = rowModifier,
                )
                is LibraryFeedItem.PlaylistItem -> PlaylistRow(
                    playlist = item.playlist,
                    onClick = onNavigateToPlaylist,
                    onMore = onPlaylistMore,
                    modifier = rowModifier,
                )
            }
        }
    }
}

/** The Songs tab: a per-type list with a sort control and (when alphabetically sorted) an A-Z rail. */
@Composable
private fun SongsLibraryList(
    songs: List<Song>,
    shortcutCounts: LibraryShortcutCounts,
    currentSongId: MediaId?,
    isPlaying: Boolean,
    sort: SortOrder,
    contentPadding: PaddingValues,
    onOrderChange: (SortOrder) -> Unit,
    onPlaySong: (Song) -> Unit,
    onSongMore: (Song) -> Unit,
    onSongPlayNext: (Song) -> Unit,
    onSongAddToQueue: (Song) -> Unit,
    onLiked: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToFollowing: () -> Unit,
) {
    TypeLibraryList(
        itemCount = songs.size,
        countLabel = pluralStringResource(R.plurals.library_song_count, songs.size, songs.size),
        shortcutCounts = shortcutCounts,
        sort = sort,
        sortOptions = SONG_SORT_OPTIONS,
        useNameLabel = false,
        letterAt = { songs[it].title.railLetter() },
        contentPadding = contentPadding,
        onOrderChange = onOrderChange,
        onLiked = onLiked,
        onNavigateToDownloads = onNavigateToDownloads,
        onNavigateToFollowing = onNavigateToFollowing,
    ) { rowEndPadding ->
        itemsIndexed(songs, key = { index, song -> "${song.id}-$index" }) { index, song ->
            SongRow(
                song = song,
                isActive = currentSongId == song.id,
                isPlaying = currentSongId == song.id && isPlaying,
                onPlay = onPlaySong,
                onMore = onSongMore,
                onPlayNext = onSongPlayNext,
                onAddToQueue = onSongAddToQueue,
                modifier = Modifier
                    .animateItem()
                    .revealOnAppear(index)
                    .fillMaxWidth()
                    .padding(end = rowEndPadding),
            )
        }
    }
}

/** The Albums tab. */
@Composable
private fun AlbumsLibraryList(
    albums: List<Album>,
    shortcutCounts: LibraryShortcutCounts,
    sort: SortOrder,
    contentPadding: PaddingValues,
    onOrderChange: (SortOrder) -> Unit,
    onNavigateToAlbum: (Album) -> Unit,
    onAlbumMore: (Album) -> Unit,
    onLiked: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToFollowing: () -> Unit,
) {
    TypeLibraryList(
        itemCount = albums.size,
        countLabel = pluralStringResource(R.plurals.library_album_count, albums.size, albums.size),
        shortcutCounts = shortcutCounts,
        sort = sort,
        sortOptions = ALBUM_SORT_OPTIONS,
        useNameLabel = false,
        letterAt = { albums[it].name.railLetter() },
        contentPadding = contentPadding,
        onOrderChange = onOrderChange,
        onLiked = onLiked,
        onNavigateToDownloads = onNavigateToDownloads,
        onNavigateToFollowing = onNavigateToFollowing,
    ) { rowEndPadding ->
        itemsIndexed(albums, key = { index, album -> "${album.id}-$index" }) { index, album ->
            AlbumRow(
                album = album,
                onClick = onNavigateToAlbum,
                onMore = onAlbumMore,
                modifier = Modifier
                    .animateItem()
                    .revealOnAppear(index)
                    .fillMaxWidth()
                    .padding(end = rowEndPadding),
            )
        }
    }
}

/** The Artists tab. */
@Composable
private fun ArtistsLibraryList(
    artists: List<Artist>,
    shortcutCounts: LibraryShortcutCounts,
    sort: SortOrder,
    contentPadding: PaddingValues,
    onOrderChange: (SortOrder) -> Unit,
    onNavigateToArtist: (Artist) -> Unit,
    onArtistMore: (Artist) -> Unit,
    onLiked: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToFollowing: () -> Unit,
) {
    TypeLibraryList(
        itemCount = artists.size,
        countLabel = pluralStringResource(R.plurals.library_artist_count, artists.size, artists.size),
        shortcutCounts = shortcutCounts,
        sort = sort,
        sortOptions = ARTIST_SORT_OPTIONS,
        useNameLabel = true,
        letterAt = { artists[it].name.railLetter() },
        contentPadding = contentPadding,
        onOrderChange = onOrderChange,
        onLiked = onLiked,
        onNavigateToDownloads = onNavigateToDownloads,
        onNavigateToFollowing = onNavigateToFollowing,
    ) { rowEndPadding ->
        itemsIndexed(artists, key = { index, artist -> "${artist.id}-$index" }) { index, artist ->
            ArtistRow(
                artist = artist,
                onClick = onNavigateToArtist,
                onMore = onArtistMore,
                modifier = Modifier
                    .animateItem()
                    .revealOnAppear(index)
                    .fillMaxWidth()
                    .padding(end = rowEndPadding),
            )
        }
    }
}

/**
 * The Genres tab: the local library genres (each with its song count), ordered by name from the DAO.
 * Tapping a genre opens its detail screen. There is no sort control or A-Z rail — genres are a compact,
 * already-name-ordered browse index — so it passes `sort = null` to the shared header like the unified
 * feed does.
 */
@Composable
private fun GenresLibraryList(
    genres: List<Genre>,
    shortcutCounts: LibraryShortcutCounts,
    contentPadding: PaddingValues,
    onNavigateToGenre: (Genre) -> Unit,
    onLiked: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToFollowing: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        item(key = "genres_header") {
            LibraryListHeader(
                countLabel = pluralStringResource(
                    R.plurals.library_genre_count,
                    genres.size,
                    genres.size,
                ),
                shortcutCounts = shortcutCounts,
                sort = null,
                sortOptions = emptyList(),
                onOrderChange = {},
                onLiked = onLiked,
                onNavigateToDownloads = onNavigateToDownloads,
                onNavigateToFollowing = onNavigateToFollowing,
            )
        }
        itemsIndexed(genres, key = { _, genre -> genre.id }) { index, genre ->
            GenreRow(
                genre = genre,
                onClick = onNavigateToGenre,
                modifier = Modifier
                    .animateItem()
                    .revealOnAppear(index)
                    .fillMaxWidth(),
            )
        }
    }
}

/**
 * The Playlists tab: regular playlists (with a sort menu + A-Z rail when sorted by name) preceded by the
 * dynamic auto-playlists section. The auto section stays in its stable, meaningful order and does not
 * participate in the alphabet rail.
 */
@Composable
private fun PlaylistsLibraryList(
    playlists: List<Playlist>,
    autoPlaylists: List<Playlist>,
    shortcutCounts: LibraryShortcutCounts,
    sort: SortOrder,
    contentPadding: PaddingValues,
    onOrderChange: (SortOrder) -> Unit,
    onNavigateToPlaylist: (Playlist) -> Unit,
    onPlaylistMore: (Playlist) -> Unit,
    onLiked: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToFollowing: () -> Unit,
) {
    TypeLibraryList(
        itemCount = playlists.size,
        countLabel = pluralStringResource(R.plurals.library_playlist_count, playlists.size, playlists.size),
        shortcutCounts = shortcutCounts,
        sort = sort,
        sortOptions = PLAYLIST_SORT_OPTIONS,
        useNameLabel = true,
        // The rail only aligns with the regular playlists (they follow the auto section). Auto rows
        // aren't alphabetized, so disable the rail while any exist to avoid misaligned jumps.
        letterAt = if (autoPlaylists.isEmpty()) {
            { playlists[it].name.railLetter() }
        } else {
            null
        },
        contentPadding = contentPadding,
        onOrderChange = onOrderChange,
        onLiked = onLiked,
        onNavigateToDownloads = onNavigateToDownloads,
        onNavigateToFollowing = onNavigateToFollowing,
    ) { rowEndPadding ->
        // Dynamic auto-playlists (Recently Added / Most Played / …) in their own section, above the
        // regular playlists. Auto entries open the same PlaylistDetail screen (their MediaId is "auto").
        if (autoPlaylists.isNotEmpty()) {
            item(key = "auto_playlists_header") {
                LibrarySectionHeader(
                    title = stringResource(R.string.auto_playlists_section),
                    modifier = Modifier.animateItem()
                )
            }
            items(autoPlaylists, key = { playlist -> "auto-${playlist.id}" }) { playlist ->
                AutoPlaylistRow(
                    playlist = playlist,
                    onClick = onNavigateToPlaylist,
                    modifier = Modifier
                        .animateItem()
                        .fillMaxWidth()
                        .padding(end = rowEndPadding)
                )
            }
        }
        itemsIndexed(playlists, key = { index, playlist -> "${playlist.id}-$index" }) { index, playlist ->
            PlaylistRow(
                playlist = playlist,
                onClick = onNavigateToPlaylist,
                onMore = onPlaylistMore,
                modifier = Modifier
                    .animateItem()
                    .revealOnAppear(index)
                    .fillMaxWidth()
                    .padding(end = rowEndPadding),
            )
        }
    }
}

/**
 * Shared per-type list scaffold: a LazyColumn with the pinned Liked/Downloads/Following tiles + count +
 * sort header, and an A-Z fast-scroll rail overlaid at the right edge when the list is alphabetically
 * sorted. The rail is shown iff [letterAt] is non-null and the current [sort] is by title/name; then
 * ~[RailReservedWidth] end padding is reserved on each row (passed to [content]) so text clears it.
 *
 * @param letterAt maps a data index (0-based, excluding the header) to its first-letter for the rail,
 *   or `null` to force the rail off (e.g. a mixed/auto section that isn't alphabetized).
 */
@Composable
private fun TypeLibraryList(
    itemCount: Int,
    countLabel: String,
    shortcutCounts: LibraryShortcutCounts,
    sort: SortOrder,
    sortOptions: List<SortOption>,
    useNameLabel: Boolean,
    letterAt: ((Int) -> Char)?,
    contentPadding: PaddingValues,
    onOrderChange: (SortOrder) -> Unit,
    onLiked: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToFollowing: () -> Unit,
    content: LazyListScope.(rowEndPadding: Dp) -> Unit,
) {
    val listState = rememberLazyListState()
    // The rail is meaningful only for an alphabetical order (by title/name). Recency / default / other
    // orders have no letter structure, so hide it.
    val railEnabled = letterAt != null && sort.option == SortOption.TITLE
    val rowEndPadding = if (railEnabled) RailReservedWidth else 0.dp

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            item(key = "type_header") {
                LibraryListHeader(
                    countLabel = countLabel,
                    shortcutCounts = shortcutCounts,
                    sort = sort,
                    sortOptions = sortOptions,
                    onOrderChange = onOrderChange,
                    useNameLabel = useNameLabel,
                    onLiked = onLiked,
                    onNavigateToDownloads = onNavigateToDownloads,
                    onNavigateToFollowing = onNavigateToFollowing,
                )
            }
            content(rowEndPadding)
        }

        if (railEnabled && itemCount > 0 && letterAt != null) {
            AlphabetRail(
                listState = listState,
                itemCount = itemCount,
                // +1 for the header item that precedes the data rows in the LazyColumn.
                dataStartIndex = 1,
                letterAt = letterAt,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(vertical = 8.dp, horizontal = 2.dp),
            )
        }
    }
}

/**
 * The pinned right-edge A-Z fast-scroll rail (mockup 3a). Renders the distinct first-letters present in
 * the list; the letter of the currently-visible section is highlighted as a filled secondaryContainer
 * circle. Tapping or dragging a letter scrolls the [listState] to that letter's first item.
 *
 * @param dataStartIndex the LazyColumn index of the first data row (data indices are offset by the
 *   preceding header item), so a rail letter for data index `i` scrolls to `dataStartIndex + i`.
 */
@Composable
private fun AlphabetRail(
    listState: LazyListState,
    itemCount: Int,
    dataStartIndex: Int,
    letterAt: (Int) -> Char,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()

    // First data index for each distinct letter, in list order (so the rail letters are in order and
    // each maps to where its section begins). Built from the actual data, so it reflects the real list.
    val letterToFirstIndex = remember(itemCount, letterAt) {
        val map = LinkedHashMap<Char, Int>()
        for (i in 0 until itemCount) {
            val c = letterAt(i)
            if (c !in map) map[c] = i
        }
        map
    }
    val letters = remember(letterToFirstIndex) { letterToFirstIndex.keys.toList() }
    if (letters.isEmpty()) return

    // The letter of the currently top-most visible data row — highlighted in the rail.
    val activeLetter by remember {
        derivedStateOf {
            val firstDataIndex = (listState.firstVisibleItemIndex - dataStartIndex).coerceIn(0, itemCount - 1)
            letterAt(firstDataIndex)
        }
    }

    var railHeightPx by remember { mutableStateOf(0) }
    val scrollToLetterAt: (Float) -> Unit = { y ->
        if (railHeightPx > 0 && letters.isNotEmpty()) {
            val fraction = (y / railHeightPx).coerceIn(0f, 0.9999f)
            val letter = letters[(fraction * letters.size).toInt().coerceIn(0, letters.size - 1)]
            letterToFirstIndex[letter]?.let { dataIndex ->
                scope.launch { listState.scrollToItem(dataStartIndex + dataIndex) }
            }
        }
    }

    val scrollDescription = stringResource(R.string.library_scroll_to_letter, activeLetter.toString())
    Column(
        modifier = modifier
            .width(20.dp)
            .onSizeChanged { railHeightPx = it.height }
            .testTag("libraryAlphabetRail")
            .semantics { contentDescription = scrollDescription }
            .pointerInput(letters, railHeightPx) {
                detectVerticalDragGestures(
                    onDragStart = { offset -> scrollToLetterAt(offset.y) },
                    onVerticalDrag = { change, _ -> scrollToLetterAt(change.position.y) },
                )
            }
            .pointerInput(letters, railHeightPx) {
                detectTapGestures { offset -> scrollToLetterAt(offset.y) }
            },
        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEach { letter ->
            val active = letter == activeLetter
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .then(
                        if (active) {
                            Modifier.background(
                                MaterialTheme.colorScheme.secondaryContainer,
                                RoundedCornerShape(8.dp)
                            )
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = letter.toString(),
                    color = if (active) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/**
 * First-letter of a display string for the A-Z rail: the first non-blank character upper-cased, or '#'
 * for names that don't start with a letter (digits, symbols, empty). Uses the no-arg `uppercaseChar()`
 * to avoid the NonObservableLocale lint (no `Locale.getDefault()` in composition).
 */
private fun String.railLetter(): Char {
    val c = trim().firstOrNull() ?: return '#'
    val upper = c.uppercaseChar()
    return if (upper.isLetter()) upper else '#'
}

/**
 * The collapsed top toolbar: the "Library" title, an in-place search affordance, and an overflow menu
 * holding the low-frequency actions (import playlist — only on the Playlists tab — plus Customize tabs
 * and Refresh). Frequently-used shortcuts (Liked/Downloads/Following) live in the pinned tiles instead.
 *
 * Tapping the search icon expands the title row into an inline filter field ([LibrarySearchField]) that
 * narrows the CURRENT tab's on-device list via [onSearchQueryChange] — it does not leave Library. The
 * global Search screen stays reachable from the bottom bar. The back/clear affordance empties the query
 * and collapses back to the title. All filtering lives in the ViewModel; this only forwards the text.
 */
@Composable
private fun LibraryToolbar(
    showImport: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onImport: () -> Unit,
    onCustomizeTabs: () -> Unit,
    onRefresh: () -> Unit,
) {
    var searchActive by remember { mutableStateOf(false) }
    // Keep the field open whenever there is a query (e.g. after a config change re-composes the row).
    val expanded = searchActive || searchQuery.isNotEmpty()

    if (expanded) {
        LibrarySearchField(
            query = searchQuery,
            onQueryChange = onSearchQueryChange,
            onClose = {
                onSearchQueryChange("")
                searchActive = false
            },
        )
        return
    }

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
        IconButton(onClick = { searchActive = true }) {
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
 * The inline library filter field the toolbar expands into. A back affordance closes it (clearing the
 * query via [onClose]); a trailing clear button empties a non-blank query while keeping the field open.
 * The field only forwards text to [onQueryChange]; the actual narrowing lives in the ViewModel. Auto-
 * focuses on first show so the keyboard opens without a second tap.
 */
@Composable
private fun LibrarySearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.library_search_close),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.library_search_hint)) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.library_search_clear),
                        )
                    }
                }
            },
        )
        Spacer(modifier = Modifier.width(4.dp))
    }
}

/**
 * The horizontally-scrollable filter-chip row that replaces the old tab row. Renders one
 * [SelectableChip] per visible tab, in the user's configured order. A chip fills (secondaryContainer +
 * check) only when it is the [selected] type; with no selection (the unified default) every chip is
 * outlined. Each chip keeps the `libraryTab_<TAB>` test tag the old Tab carried.
 */
@Composable
private fun LibraryFilterChips(
    tabs: List<LibraryTab>,
    selected: LibraryTab?,
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
 * The scroll-away header shown above a list: the pinned Liked / Downloads / Following tiles followed by
 * a "N items" count and — when [sort] is non-null (a selected type) — the sort control. The unified
 * feed passes `sort = null`, hiding the sort control since its recency order is fixed.
 */
@Composable
private fun LibraryListHeader(
    countLabel: String,
    shortcutCounts: LibraryShortcutCounts,
    sort: SortOrder?,
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
                count = pluralStringResource(
                    R.plurals.library_song_count,
                    shortcutCounts.liked,
                    shortcutCounts.liked,
                ),
                onClick = onLiked,
                modifier = Modifier.weight(1f),
            )
            ShortcutTile(
                icon = Icons.Rounded.Download,
                label = stringResource(R.string.downloads_title),
                count = pluralStringResource(
                    R.plurals.library_song_count,
                    shortcutCounts.downloaded,
                    shortcutCounts.downloaded,
                ),
                onClick = onNavigateToDownloads,
                modifier = Modifier.weight(1f),
            )
            ShortcutTile(
                icon = Icons.Rounded.Group,
                label = stringResource(R.string.following_title),
                count = pluralStringResource(
                    R.plurals.library_artist_count,
                    shortcutCounts.following,
                    shortcutCounts.following,
                ),
                onClick = onNavigateToFollowing,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = countLabel,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
            if (sort != null) {
                SortPill(
                    sort = sort,
                    options = sortOptions,
                    onOrderChange = onOrderChange,
                    useNameLabel = useNameLabel,
                )
            }
        }
    }
}

/**
 * The sort affordance as one outlined pill (mockup 3a): the sort glyph and the current-order label
 * together inside a single outlined, [RoundedCornerShape] container, in primary content. The shared
 * [SortMenu] supplies the glyph (its icon is the pill's sort glyph) and owns all the menu logic, its
 * dropdown, and the `sortMenuButton` test tag — so tapping the glyph opens the menu. The label sits
 * beside it, reading as one control. Primary tint is provided to the [SortMenu] icon via
 * [LocalContentColor] so the whole pill is primary-coloured without changing the shared component.
 */
@Composable
private fun SortPill(
    sort: SortOrder,
    options: List<SortOption>,
    onOrderChange: (SortOrder) -> Unit,
    useNameLabel: Boolean,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                RoundedCornerShape(14.dp),
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.primary
        ) {
            SortMenu(
                current = sort,
                options = options,
                onOrderChange = onOrderChange,
                useNameLabel = useNameLabel,
                modifier = Modifier.size(36.dp),
            )
        }
        Text(
            text = stringResource(sort.option.labelRes(useNameLabel)),
            color = MaterialTheme.colorScheme.primary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(end = 12.dp),
        )
    }
}

/**
 * A single pinned shortcut tile (Liked / Downloads / Following): a flat surfaceContainerHighest panel
 * with a primary-tinted filled icon and a label. When [count] is non-null it renders as a second,
 * de-emphasised line under the label (e.g. "128 songs"); the caller resolves the pluralized, localized
 * string so this stays a pure renderer.
 */
@Composable
private fun ShortcutTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: String? = null,
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
            if (count != null) {
                Text(
                    text = count,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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

/**
 * One row for a library genre: a music-note tile, the genre name, and a localized song-count subtitle.
 * Uses the shared leading-content [ListItem] with no trailing "more" button — a genre has no per-item
 * options, only navigation to its detail screen on tap.
 */
@Composable
fun GenreRow(
    genre: Genre,
    onClick: (Genre) -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        title = genre.name,
        badges = emptyList(),
        subtitle = pluralStringResource(R.plurals.library_song_count, genre.songCount, genre.songCount),
        isActive = false,
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
        },
        trailingContent = {},
        onClick = { onClick(genre) },
        modifier = modifier,
    )
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
