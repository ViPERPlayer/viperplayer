package com.viperplayer.presentation.search

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viperplayer.R
import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.MediaItem
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.Song
import com.viperplayer.plugin.v1.SearchFilter
import com.viperplayer.presentation.common.ListItem
import com.viperplayer.presentation.common.MediaItemOptionsBottomSheet
import com.viperplayer.presentation.common.ViperScaffold
import com.viperplayer.presentation.ktx.bottom
import com.viperplayer.presentation.search.model.SearchItem
import kotlinx.coroutines.launch

@SuppressLint("AutoboxingStateCreation")
@Composable
fun SearchScreen(
    rootPadding: PaddingValues,
    onNavigateToAlbum: (Album) -> Unit,
    onNavigateToArtist: (Artist) -> Unit,
    onNavigateToPlaylist: (Playlist) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val searchSuggestionsState by viewModel.searchSuggestionsState.collectAsStateWithLifecycle()
    val searchResultsState by viewModel.searchResultsState.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val lastSearchedQuery by viewModel.lastSearchedQuery.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()

    var selectedMediaItem by remember { mutableStateOf<MediaItem?>(null) }
    val scope = rememberCoroutineScope()

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
                                    Icon(Icons.Default.Search, contentDescription = null)
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
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp)
            )

            ExpandedFullScreenSearchBar(
                state = searchBarState,
                inputField = inputField
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
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
                            isActive = item.isActive,
                            isPlaying = isPlaying,
                            onClick = {
                                when (item.type) {
                                    SearchItem.Type.SONG -> viewModel.playSong(item.id)
                                    SearchItem.Type.ARTIST -> {
                                        onNavigateToArtist(
                                            Artist(
                                                id = item.id,
                                                name = item.title
                                            )
                                        )
                                    }

                                    SearchItem.Type.ALBUM -> {
                                        onNavigateToAlbum(
                                            Album(
                                                id = item.id,
                                                name = item.title
                                            )
                                        )
                                    }

                                    SearchItem.Type.PLAYLIST -> {
                                        onNavigateToPlaylist(
                                            Playlist(
                                                id = item.id,
                                                name = item.title
                                            )
                                        )
                                    }
                                }
                            },
                            onMoreClick = {
                                scope.launch {
                                    selectedMediaItem = when (item.type) {
                                        SearchItem.Type.SONG -> {
                                            item.song ?: viewModel.getSong(item.id)
                                        }

                                        SearchItem.Type.ALBUM -> {
                                            viewModel.getAlbum(item.id)
                                        }

                                        SearchItem.Type.ARTIST -> {
                                            viewModel.getArtist(item.id)
                                        }

                                        SearchItem.Type.PLAYLIST -> {
                                            viewModel.getPlaylist(item.id)
                                        }
                                    }
                                }
                            },
                            onLongClick = {
                                scope.launch {
                                    selectedMediaItem = when (item.type) {
                                        SearchItem.Type.SONG -> {
                                            item.song ?: viewModel.getSong(item.id)
                                        }

                                        SearchItem.Type.ALBUM -> {
                                            viewModel.getAlbum(item.id)
                                        }

                                        SearchItem.Type.ARTIST -> {
                                            viewModel.getArtist(item.id)
                                        }

                                        SearchItem.Type.PLAYLIST -> {
                                            viewModel.getPlaylist(item.id)
                                        }
                                    }
                                }
                            },
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
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
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
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = rootPadding.bottom()
                        ) {
                            items(
                                items = state.items,
                            ) { item ->
                                ListItem(
                                    type = item.type,
                                    title = item.title,
                                    badges = item.badges,
                                    subtitle = item.subtitle,
                                    artworkUrl = item.artworkUrl,
                                    isActive = item.isActive,
                                    isPlaying = isPlaying,
                                    onClick = {
                                        when (item.type) {
                                            SearchItem.Type.SONG -> viewModel.playSong(item.id)
                                            SearchItem.Type.ARTIST -> {
                                                onNavigateToArtist(
                                                    Artist(
                                                        id = item.id,
                                                        name = item.title
                                                    )
                                                )
                                            }

                                            SearchItem.Type.ALBUM -> {
                                                onNavigateToAlbum(
                                                    Album(
                                                        id = item.id,
                                                        name = item.title
                                                    )
                                                )
                                            }

                                            SearchItem.Type.PLAYLIST -> {
                                                onNavigateToPlaylist(
                                                    Playlist(
                                                        id = item.id,
                                                        name = item.title
                                                    )
                                                )
                                            }
                                        }
                                    },
                                    onMoreClick = {
                                        scope.launch {
                                            selectedMediaItem = when (item.type) {
                                                SearchItem.Type.SONG -> {
                                                    item.song ?: viewModel.getSong(item.id)
                                                }

                                                SearchItem.Type.ALBUM -> {
                                                    viewModel.getAlbum(item.id)
                                                }

                                                SearchItem.Type.ARTIST -> {
                                                    viewModel.getArtist(item.id)
                                                }

                                                SearchItem.Type.PLAYLIST -> {
                                                    viewModel.getPlaylist(item.id)
                                                }
                                            }
                                        }
                                    },
                                    onLongClick = {
                                        scope.launch {
                                            selectedMediaItem = when (item.type) {
                                                SearchItem.Type.SONG -> {
                                                    item.song ?: viewModel.getSong(item.id)
                                                }

                                                SearchItem.Type.ALBUM -> {
                                                    viewModel.getAlbum(item.id)
                                                }

                                                SearchItem.Type.ARTIST -> {
                                                    viewModel.getArtist(item.id)
                                                }

                                                SearchItem.Type.PLAYLIST -> {
                                                    viewModel.getPlaylist(item.id)
                                                }
                                            }
                                        }
                                    },
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

                            item {
                                LaunchedEffect(Unit) {
                                    viewModel.loadMore()
                                }
                            }
                        }
                    }

                    is SearchResultsState.Error -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(rootPadding.bottom()),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = stringResource(R.string.action_error),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Media item options bottom sheet
        selectedMediaItem?.let { item ->
            ModalBottomSheet(
                onDismissRequest = { selectedMediaItem = null },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                dragHandle = { BottomSheetDefaults.DragHandle() }
            ) {
                MediaItemOptionsBottomSheet(
                    item = item,
                    onDismiss = { selectedMediaItem = null },
                    onPlay = {
                        scope.launch {
                            when (item) {
                                is Song -> viewModel.playSong(item.id)
                                is Album -> {
                                    // TODO: Implement play album
                                }

                                is Playlist -> {
                                    // TODO: Implement play playlist
                                }

                                else -> {}
                            }
                            selectedMediaItem = null
                        }
                    },
                    onPlayNext = {
                        scope.launch {
                            when (item) {
                                is Song -> viewModel.playNext(item)
                                else -> {}
                            }
                            selectedMediaItem = null
                        }
                    },
                    onAddToQueue = {
                        scope.launch {
                            when (item) {
                                is Song -> viewModel.addToQueue(item)
                                is Album -> {
                                    // TODO: Implement add album to queue
                                }

                                is Playlist -> {
                                    // TODO: Implement add playlist to queue
                                }

                                else -> {}
                            }
                            selectedMediaItem = null
                        }
                    },
                    onLike = {
                        scope.launch {
                            when (item) {
                                is Song -> viewModel.toggleLike(item)
                                else -> {}
                            }
                            selectedMediaItem = null
                        }
                    },
                    onViewArtist = { artist ->
                        onNavigateToArtist(artist)
                        selectedMediaItem = null
                    },
                    onViewAlbum = { album ->
                        onNavigateToAlbum(album)
                        selectedMediaItem = null
                    }
                )
            }
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val filters = listOf(
            stringResource(R.string.search_filter_all) to null,
            stringResource(R.string.search_filter_songs) to SearchFilter.SONG,
            stringResource(R.string.search_filter_albums) to SearchFilter.ALBUM,
            stringResource(R.string.search_filter_playlists) to SearchFilter.PLAYLIST,
            stringResource(R.string.search_filter_artists) to SearchFilter.ARTIST
        )

        filters.forEach { (label, filter) ->
            val isSelected = selectedFilter == filter
            FilterChip(
                onClick = {
                    onFilterSelected(filter)
                },
                label = {
                    Text(label)
                },
                selected = isSelected,
                leadingIcon = if (isSelected) {
                    {
                        Icon(
                            imageVector = Icons.Filled.Done,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                        )
                    }
                } else {
                    null
                }
            )
        }
    }
}
