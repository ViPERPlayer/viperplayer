package com.viperplayer.presentation.search

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.viperplayer.presentation.common.revealOnAppear
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AddToQueue
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryAddCheck
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueuePlayNext
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.viperplayer.R
import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.MediaItem
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.model.SearchFilter
import com.viperplayer.presentation.common.ErrorState
import com.viperplayer.presentation.common.ListItem
import com.viperplayer.presentation.common.PlayingArtworkOverlay
import com.viperplayer.presentation.common.AddToPlaylistSheetHost
import com.viperplayer.presentation.common.rememberAddToPlaylistController
import com.viperplayer.presentation.common.MediaItemOptionsSheetHost
import com.viperplayer.presentation.common.rememberMediaItemOptionsController
import com.viperplayer.presentation.common.OfflineBanner
import com.viperplayer.presentation.common.ViperScaffold
import com.viperplayer.presentation.common.components.SelectableChip
import com.viperplayer.presentation.common.components.SurfaceCard
import com.viperplayer.presentation.home.SuggestionsViewModel
import com.viperplayer.presentation.home.suggestionsSearchSurface
import com.viperplayer.presentation.ktx.bottom
import com.viperplayer.presentation.ktx.infiniteBasicMarquee
import com.viperplayer.presentation.search.model.ItemBadge
import com.viperplayer.presentation.search.model.SearchItem
import com.viperplayer.presentation.theme.Spacing
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@SuppressLint("AutoboxingStateCreation")
@Composable
fun SearchScreen(
    rootPadding: PaddingValues,
    onNavigateToAlbum: (Album) -> Unit,
    onNavigateToArtist: (Artist) -> Unit,
    onNavigateToPlaylist: (Playlist) -> Unit,
    onViewDetails: (MediaItem) -> Unit,
    onMoreLikeThis: (Song) -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel(),
    suggestionsViewModel: SuggestionsViewModel = hiltViewModel(),
) {
    val searchSuggestionsState by viewModel.searchSuggestionsState.collectAsStateWithLifecycle()
    val searchResultsState by viewModel.searchResultsState.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val lastSearchedQuery by viewModel.lastSearchedQuery.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val offlineMode by viewModel.offlineMode.collectAsStateWithLifecycle()
    // Shared on-device "Suggested for you" surface for the empty (pre-query) Search state.
    val suggestionsState by suggestionsViewModel.uiState.collectAsStateWithLifecycle()

    val optionsController = rememberMediaItemOptionsController()
    val addToPlaylistController = rememberAddToPlaylistController()
    val scope = rememberCoroutineScope()

    // Resolve a SearchItem to its domain media (suspending) and open the shared options sheet.
    val openOptions: (SearchItem) -> Unit = { searchItem ->
        scope.launch {
            val media = when (searchItem.type) {
                SearchItem.Type.SONG -> searchItem.song ?: viewModel.getSong(searchItem.id)
                SearchItem.Type.ALBUM -> viewModel.getAlbum(searchItem.id)
                SearchItem.Type.ARTIST -> viewModel.getArtist(searchItem.id)
                SearchItem.Type.PLAYLIST -> viewModel.getPlaylist(searchItem.id)
            }
            media?.let { optionsController.show(it) }
        }
    }

    // The item's natural activation: play a song, or navigate into an album/artist/playlist.
    val onItemActivated: (SearchItem) -> Unit = { item ->
        when (item.type) {
            SearchItem.Type.SONG -> viewModel.playSong(item.id)
            SearchItem.Type.ARTIST -> onNavigateToArtist(Artist(id = item.id, name = item.title))
            SearchItem.Type.ALBUM -> onNavigateToAlbum(Album(id = item.id, name = item.title))
            SearchItem.Type.PLAYLIST -> onNavigateToPlaylist(Playlist(id = item.id, name = item.title))
        }
    }

    ViperScaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.exclude(NavigationBarDefaults.windowInsets)
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            val searchBarState = rememberSearchBarState()
            val textFieldState = rememberTextFieldState()
            val scope = rememberCoroutineScope()

            LaunchedEffect(textFieldState.text) {
                val text = textFieldState.text.toString()
                viewModel.onQueryChange(text)
            }

            val inputField =
                @Composable {
                    SearchBarDefaults.InputField(
                        textFieldState = textFieldState,
                        searchBarState = searchBarState,
                        onSearch = {
                            textFieldState.setTextAndPlaceCursorAtEnd(it)
                            viewModel.performSearch(it)
                            scope.launch {
                                searchBarState.animateToCollapsed()
                            }
                        },
                        placeholder = {
                            Text(
                                modifier = Modifier.clearAndSetSemantics {}, text = stringResource(
                                    R.string.search_placeholder
                                )
                            )
                        },
                        leadingIcon = {
                            val onDismiss: () -> Unit = {
                                textFieldState.setTextAndPlaceCursorAtEnd(lastSearchedQuery)
                                scope.launch { searchBarState.animateToCollapsed() }
                            }

                            AnimatedContent(
                                targetState = searchBarState.currentValue == SearchBarValue.Expanded
                            ) { targetState ->
                                if (targetState) {
                                    IconButton(onClick = onDismiss) {
                                        Icon(
                                            Icons.AutoMirrored.Rounded.ArrowBack,
                                            contentDescription = stringResource(R.string.action_back)
                                        )
                                    }
                                } else {
                                    Icon(Icons.Rounded.Search, contentDescription = null)
                                }
                            }

                            BackHandler(
                                enabled = searchBarState.currentValue == SearchBarValue.Expanded,
                                onBack = onDismiss
                            )
                        },
                        trailingIcon = {
                            AnimatedVisibility(
                                visible = searchBarState.currentValue == SearchBarValue.Expanded && textFieldState.text.isNotEmpty()
                            ) {
                                IconButton(onClick = {
                                    textFieldState.clearText()
                                }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.action_clear)
                                    )
                                }
                            }
                        },
                    )
                }

            SearchBar(
                state = searchBarState,
                inputField = inputField,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg)
                    .padding(top = Spacing.sm)
            )

            // Offline mode is on: tell the user why remote search results are hidden.
            if (offlineMode) {
                OfflineBanner(
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                )
            }

            ExpandedFullScreenSearchBar(
                state = searchBarState,
                inputField = inputField
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // "Recent searches" header + Clear-all, shown only when the field is empty (so
                    // it's genuinely the recent list, not history filtered while typing).
                    if (textFieldState.text.isEmpty() && searchSuggestionsState.history.isNotEmpty()) {
                        item(key = "history_header") {
                            RecentSearchesHeader(
                                onClearAll = { viewModel.clearHistory() },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }

                    items(
                        items = searchSuggestionsState.history,
                        key = { "history_$it" }
                    ) { suggestion ->
                        HistoryListItem(
                            suggestion = suggestion,
                            onRemove = { viewModel.removeHistoryEntry(suggestion) },
                            onInsert = { textFieldState.setTextAndPlaceCursorAtEnd(suggestion) },
                            modifier = Modifier
                                .animateItem()
                                .clickable {
                                    textFieldState.setTextAndPlaceCursorAtEnd(suggestion)
                                    viewModel.performSearch(suggestion)
                                    scope.launch {
                                        searchBarState.animateToCollapsed()
                                    }
                                }
                                .fillMaxWidth()
                        )
                    }

                    items(
                        items = searchSuggestionsState.suggestions,
                        key = { "suggestion_$it" }
                    ) { suggestion ->
                        SuggestionListItem(
                            suggestion = suggestion,
                            onInsert = { textFieldState.setTextAndPlaceCursorAtEnd(suggestion) },
                            modifier = Modifier
                                .animateItem()
                                .clickable {
                                    textFieldState.setTextAndPlaceCursorAtEnd(suggestion)
                                    viewModel.performSearch(suggestion)
                                    scope.launch {
                                        searchBarState.animateToCollapsed()
                                    }
                                }
                                .fillMaxWidth()
                        )
                    }

                    // Pre-query "Suggested for you" (on-device, no login) — shown alongside/below the
                    // recent searches when the field is empty. Hidden as soon as the user types.
                    if (textFieldState.text.isEmpty()) {
                        suggestionsSearchSurface(
                            state = suggestionsState,
                            currentSongId = currentSong?.id,
                            isPlaying = isPlaying,
                            onPlay = { suggestionsViewModel.play(it) },
                            onEnable = { suggestionsViewModel.enable() },
                        )
                    }

                    if ((searchSuggestionsState.history.isNotEmpty() || searchSuggestionsState.suggestions.isNotEmpty()) && searchSuggestionsState.items.isNotEmpty()) {
                        item(
                            key = "items_divider"
                        ) {
                            HorizontalDivider(
                                modifier = Modifier.animateItem()
                            )
                        }
                    }

                    items(
                        items = searchSuggestionsState.items,
                        key = { "item_${it.id}" },
                        contentType = { it.type }
                    ) { item ->
                        ListItem(
                            type = item.type,
                            title = item.title,
                            badges = item.badges,
                            subtitle = item.subtitle,
                            artworkUrl = item.artworkUrl,
                            isActive = item.type == SearchItem.Type.SONG && item.id == currentSong?.id,
                            isPlaying = isPlaying,
                            onClick = { onItemActivated(item) },
                            onMoreClick = { openOptions(item) },
                            onLongClick = { openOptions(item) },
                            onPlayNext = if (item.type == SearchItem.Type.SONG) {
                                {
                                    scope.launch {
                                        val song = item.song ?: viewModel.getSong(item.id)
                                        if (song != null) {
                                            viewModel.playNext(song)
                                        }
                                    }
                                }
                            } else null,
                            onAddToQueue = if (item.type == SearchItem.Type.SONG) {
                                {
                                    scope.launch {
                                        val song = item.song ?: viewModel.getSong(item.id)
                                        if (song != null) {
                                            viewModel.addToQueue(song)
                                        }
                                    }
                                }
                            } else null,
                            modifier = Modifier
                                .animateItem()
                                .fillMaxWidth()
                        )
                    }
                }
            }

            if (searchResultsState !is SearchResultsState.Idle && searchResultsState !is SearchResultsState.Error) {
                SearchFilterRow(
                    selectedFilter = selectedFilter,
                    onFilterSelected = viewModel::onFilterSelected
                )
            }

            AnimatedContent(
                targetState = searchResultsState,
                label = "search_results",
                contentKey = { it::class }
            ) { state ->
                when (state) {
                    is SearchResultsState.Idle -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(rootPadding.bottom()),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(Spacing.lg))
                                Text(
                                    text = stringResource(R.string.search_for_music),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    is SearchResultsState.Searching -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(rootPadding.bottom()),
                            contentAlignment = Alignment.Center
                        ) {
                            LoadingIndicator()
                        }
                    }

                    is SearchResultsState.Empty -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(rootPadding.bottom()),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.search_no_results),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    is SearchResultsState.Results -> {
                        SearchResultsList(
                            items = state.items,
                            currentSongId = currentSong?.id,
                            isPlaying = isPlaying,
                            onItemActivated = onItemActivated,
                            onMore = openOptions,
                            onPlayNext = { item ->
                                scope.launch {
                                    val song = item.song ?: viewModel.getSong(item.id)
                                    if (song != null) viewModel.playNext(song)
                                }
                            },
                            onAddToQueue = { item ->
                                scope.launch {
                                    val song = item.song ?: viewModel.getSong(item.id)
                                    if (song != null) viewModel.addToQueue(song)
                                }
                            },
                            onLoadMore = { viewModel.loadMore() },
                            contentPadding = rootPadding.bottom(),
                        )
                    }

                    is SearchResultsState.Error -> {
                        ErrorState(
                            title = stringResource(R.string.action_error),
                            message = state.message,
                            modifier = Modifier.padding(rootPadding.bottom()),
                        )
                    }
                }
            }
        }

        // Media item options bottom sheet
        MediaItemOptionsSheetHost(
            controller = optionsController,
            onPlay = { scope.launch { if (it is Song) viewModel.playSong(it.id) } },
            onPlayNext = { scope.launch { if (it is Song) viewModel.playNext(it) } },
            onAddToQueue = { scope.launch { if (it is Song) viewModel.addToQueue(it) } },
            onAddToPlaylist = { if (it is Song) addToPlaylistController.show(it) },
            onLike = { scope.launch { if (it is Song) viewModel.toggleLike(it) } },
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
 * Grouped search results (mockup 4a): a "Top result" card, then per-type sections — song rows,
 * and horizontal carousels for albums/playlists, plus artist rows. Pagination is preserved via a
 * trailing sentinel that triggers [onLoadMore].
 *
 * Grouping is a pure rendering derivation of the already-loaded flat list; no data is fetched here.
 */
@Composable
private fun SearchResultsList(
    items: List<SearchItem>,
    currentSongId: MediaId?,
    isPlaying: Boolean,
    onItemActivated: (SearchItem) -> Unit,
    onMore: (SearchItem) -> Unit,
    onPlayNext: (SearchItem) -> Unit,
    onAddToQueue: (SearchItem) -> Unit,
    onLoadMore: () -> Unit,
    contentPadding: PaddingValues,
) {
    // The first result is the "top result"; the remainder is grouped by type into sections. Ordering
    // within each section mirrors the backend order (no re-sorting), so pagination stays coherent.
    val topResult = items.firstOrNull()
    val rest = if (items.isEmpty()) emptyList() else items.drop(1)
    val songs = rest.filter { it.type == SearchItem.Type.SONG }
    val albums = rest.filter { it.type == SearchItem.Type.ALBUM }
    val artists = rest.filter { it.type == SearchItem.Type.ARTIST }
    val playlists = rest.filter { it.type == SearchItem.Type.PLAYLIST }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        if (topResult != null) {
            item(key = "top_result") {
                ResultsSectionLabel(
                    text = stringResource(R.string.search_section_top_result),
                    modifier = Modifier.animateItem(),
                )
            }
            item(key = "top_result_card") {
                TopResultCard(
                    item = topResult,
                    isActive = topResult.type == SearchItem.Type.SONG && topResult.id == currentSongId,
                    isPlaying = isPlaying,
                    onClick = { onItemActivated(topResult) },
                    modifier = Modifier
                        .animateItem()
                        .padding(horizontal = Spacing.lg),
                )
            }
        }

        if (songs.isNotEmpty()) {
            item(key = "songs_header") {
                ResultsSectionLabel(text = stringResource(R.string.search_section_songs))
            }
            itemsIndexed(
                items = songs,
                key = { _, item -> "song_${item.id}" },
                contentType = { _, item -> item.type },
            ) { index, item ->
                SearchSongRow(
                    item = item,
                    isActive = item.id == currentSongId,
                    isPlaying = isPlaying,
                    onClick = { onItemActivated(item) },
                    onMore = { onMore(item) },
                    onPlayNext = { onPlayNext(item) },
                    onAddToQueue = { onAddToQueue(item) },
                    modifier = Modifier
                        .animateItem()
                        .fillMaxWidth()
                        .revealOnAppear(index)
                )
            }
        }

        if (albums.isNotEmpty()) {
            item(key = "albums_header") {
                ResultsSectionLabel(text = stringResource(R.string.search_section_albums))
            }
            item(key = "albums_row") {
                MediaCarousel(
                    items = albums,
                    onClick = onItemActivated,
                    modifier = Modifier.animateItem(),
                )
            }
        }

        if (playlists.isNotEmpty()) {
            item(key = "playlists_header") {
                ResultsSectionLabel(text = stringResource(R.string.search_section_playlists))
            }
            item(key = "playlists_row") {
                MediaCarousel(
                    items = playlists,
                    onClick = onItemActivated,
                    modifier = Modifier.animateItem(),
                )
            }
        }

        if (artists.isNotEmpty()) {
            item(key = "artists_header") {
                ResultsSectionLabel(text = stringResource(R.string.search_section_artists))
            }
            items(
                items = artists,
                key = { "artist_${it.id}" },
                contentType = { it.type },
            ) { item ->
                ListItem(
                    type = item.type,
                    title = item.title,
                    badges = item.badges,
                    subtitle = item.subtitle,
                    artworkUrl = item.artworkUrl,
                    isActive = false,
                    isPlaying = isPlaying,
                    onClick = { onItemActivated(item) },
                    onMoreClick = { onMore(item) },
                    onLongClick = { onMore(item) },
                    modifier = Modifier
                        .animateItem()
                        .fillMaxWidth()
                )
            }
        }

        item(key = "load_more") {
            LaunchedEffect(Unit) {
                onLoadMore()
            }
        }
    }
}

/** Corner radius for the filled active-row container (mockup 4a). */
private val ActiveRowCorner = 14.dp

/** Song-row artwork thumbnail size + corner radius (mockup 4a bumps the radius from the shared 6dp). */
private val SearchRowArtworkSize = 50.dp
private val SearchRowArtworkCorner = 10.dp

/**
 * A Search results song row built locally (not via the shared [ListItem]) so it can carry the mockup-4a
 * fidelity the shared row can't: the now-playing highlight (filled [ActiveRowCorner]-rounded
 * `surfaceContainerHigh` container with a small horizontal inset, primary-tinted title, and a trailing
 * `primary` graphic_eq glyph before the more button) and the small rounded-square "E" explicit tag after
 * the title. The artwork ([SearchRowArtwork], reusing the shared now-playing overlay) stays visible —
 * the eq lives beside the title, never over the thumbnail. Keeps the swipe-left → play-next /
 * swipe-right → add-to-queue gestures the shared row provided.
 */
@Composable
private fun SearchSongRow(
    item: SearchItem,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onMore: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    modifier: Modifier = Modifier,
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
                onDragStopped = {
                    scope.launch {
                        when {
                            offset.value < -threshold -> {
                                onPlayNext()
                                Toast.makeText(context, playingNextMessage, Toast.LENGTH_SHORT).show()
                            }
                            offset.value > threshold -> {
                                onAddToQueue()
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
            offset.value < 0 -> SearchSwipeAffordance(
                icon = Icons.Rounded.QueuePlayNext,
                contentDescription = stringResource(R.string.action_play_next),
                width = absOffsetDp,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
            // Dragged right → reveal add-to-queue on the leading edge.
            offset.value > 0 -> SearchSwipeAffordance(
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
                .combinedClickable(onClick = onClick, onLongClick = onMore)
                // Content sits ~16dp from the screen edge. The active row's container already adds an
                // 8dp inset, so its inner padding is 8dp; the flat non-active row uses the full 16dp.
                .padding(horizontal = if (isActive) Spacing.sm else Spacing.lg, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            SearchRowArtwork(
                artworkUrl = item.artworkUrl,
                isActive = isActive,
                isPlaying = isPlaying,
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = item.title,
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
                    if (ItemBadge.EXPLICIT in item.badges) ExplicitTag()
                }
                // Non-explicit badges (favorite/library/download state) plus the subtitle, mirroring the
                // shared row's secondary line — the explicit marker is promoted to the "E" tag above.
                val otherBadges = item.badges.filter { it != ItemBadge.EXPLICIT }
                if (otherBadges.isNotEmpty() || item.subtitle != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        if (otherBadges.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                otherBadges.forEach { badge ->
                                    val icon = when (badge) {
                                        ItemBadge.FAVORITE -> Icons.Rounded.Favorite
                                        ItemBadge.LIBRARY -> Icons.Rounded.LibraryAddCheck
                                        ItemBadge.DOWNLOADING -> Icons.Rounded.Downloading
                                        ItemBadge.DOWNLOADED -> Icons.Rounded.Download
                                        // EXPLICIT is promoted to the "E" tag above and filtered out here.
                                        ItemBadge.EXPLICIT -> null
                                    }
                                    if (icon != null) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                        }
                        item.subtitle?.let { subtitle ->
                            Text(
                                text = subtitle,
                                modifier = Modifier.infiniteBasicMarquee(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
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
            IconButton(onClick = onMore) {
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
private fun SearchSwipeAffordance(
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
 * The song row's leading artwork — a local thumbnail at [SearchRowArtworkCorner] (10dp, up from the
 * shared row's 6dp per mockup 4a) with the shared now-playing overlay centered on top.
 */
@Composable
private fun SearchRowArtwork(
    artworkUrl: String?,
    isActive: Boolean,
    isPlaying: Boolean,
) {
    Box(
        modifier = Modifier.size(SearchRowArtworkSize),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = artworkUrl,
            contentDescription = stringResource(R.string.cd_artwork),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(SearchRowArtworkCorner))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        )
        PlayingArtworkOverlay(isActive = isActive, isPlaying = isPlaying)
    }
}

/**
 * The small rounded-square "E" explicit tag (mockup 4a): a surfaceVariant-filled square with a bold
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

/** Bold section header above a grouped results section, matching the mockup's 15sp / weight-700 rhythm. */
@Composable
private fun ResultsSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.lg, bottom = 6.dp),
    )
}

/** Leading-artwork size in the prominent top-result card. */
private val TopResultArtworkSize = 72.dp

/** Diameter of the top-result card's trailing circular activation button. */
private val TopResultActionSize = 48.dp

/**
 * The prominent "Top result" card: large artwork (circular for artists), a big title + subtitle, and
 * a filled circular activation button (play for a song, open for album/artist/playlist). Tapping the
 * card runs the same activation as the button.
 */
@Composable
private fun TopResultCard(
    item: SearchItem,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SurfaceCard(modifier = modifier.clickable(onClick = onClick)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val artShape = if (item.type == SearchItem.Type.ARTIST) CircleShape else RoundedCornerShape(14.dp)
            Box(
                modifier = Modifier.size(TopResultArtworkSize),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = item.artworkUrl,
                    contentDescription = stringResource(R.string.cd_artwork),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(artShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest, artShape),
                )
                PlayingArtworkOverlay(isActive = isActive, isPlaying = isPlaying)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = item.subtitle
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.5f.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            val isSong = item.type == SearchItem.Type.SONG
            val actionDescription = if (isSong) {
                stringResource(R.string.action_play)
            } else {
                stringResource(R.string.search_open)
            }
            Box(
                modifier = Modifier
                    .size(TopResultActionSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isSong) Icons.Rounded.PlayArrow else Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = actionDescription,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

/** Width/height of a carousel artwork tile (albums & playlists). */
private val CarouselArtworkSize = 108.dp

/** Horizontal carousel of album/playlist tiles: square artwork with a title + subtitle beneath. */
@Composable
private fun MediaCarousel(
    items: List<SearchItem>,
    onClick: (SearchItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        items(
            items = items,
            key = { "carousel_${it.id}" },
        ) { item ->
            Column(
                modifier = Modifier
                    .width(CarouselArtworkSize)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onClick(item) },
            ) {
                AsyncImage(
                    model = item.artworkUrl,
                    contentDescription = stringResource(R.string.cd_artwork),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(CarouselArtworkSize)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                )
                Text(
                    text = item.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
                val subtitle = item.subtitle
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
internal fun RecentSearchesHeader(
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("searchRecentHeader")
            .padding(start = Spacing.lg, end = Spacing.sm, top = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = stringResource(R.string.search_recent_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = onClearAll) {
            Text(stringResource(R.string.search_clear_history))
        }
    }
}

@Composable
private fun SearchFilterRow(
    selectedFilter: SearchFilter?,
    onFilterSelected: (SearchFilter?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        val filters = listOf(
            stringResource(R.string.search_filter_all) to null,
            stringResource(R.string.search_filter_songs) to SearchFilter.SONG,
            stringResource(R.string.search_filter_albums) to SearchFilter.ALBUM,
            stringResource(R.string.search_filter_playlists) to SearchFilter.PLAYLIST,
            stringResource(R.string.search_filter_artists) to SearchFilter.ARTIST
        )

        filters.forEach { (label, filter) ->
            SelectableChip(
                text = label,
                selected = selectedFilter == filter,
                onClick = { onFilterSelected(filter) },
            )
        }
    }
}
