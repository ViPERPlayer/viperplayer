package com.viperplayer.presentation.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viperplayer.domain.model.MediaId
import com.viperplayer.presentation.common.ListItem
import com.viperplayer.presentation.common.ViperScaffold
import com.viperplayer.presentation.ktx.bottom
import com.viperplayer.presentation.search.model.SearchItem
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(
    rootPadding: PaddingValues,
    onNavigateToAlbum: (MediaId) -> Unit = {},
    onNavigateToArtist: (MediaId) -> Unit = {},
    onNavigateToPlaylist: (MediaId) -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel()
) {
    val searchSuggestionsState by viewModel.searchSuggestionsState.collectAsStateWithLifecycle()
    val searchResultsState by viewModel.searchResultsState.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val lastSearchedQuery by viewModel.lastSearchedQuery.collectAsStateWithLifecycle()

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
            val textFieldState = rememberTextFieldState(initialText = query)
            val scope = rememberCoroutineScope()

            // Sync text field changes to ViewModel (user typing)
            LaunchedEffect(textFieldState.text) {
                val text = textFieldState.text.toString()
                if (text != query) {
                    viewModel.onQueryChange(text)
                }
            }

            // Sync ViewModel query to text field (programmatic updates)
            LaunchedEffect(query) {
                val currentText = textFieldState.text.toString()
                if (currentText != query) {
                    textFieldState.setTextAndPlaceCursorAtEnd(query)
                }
            }

            val inputField =
                @Composable {
                    SearchBarDefaults.InputField(
                        textFieldState = textFieldState,
                        searchBarState = searchBarState,
                        onSearch = {
                            viewModel.onQueryChange(it)
                            viewModel.performSearch()
                            scope.launch {
                                searchBarState.animateToCollapsed()
                            }
                        },
                        placeholder = {
                            Text(modifier = Modifier.clearAndSetSemantics {}, text = "What's playing in your head?")
                        },
                        leadingIcon = {
                            val onDismiss: () -> Unit = {
                                viewModel.onQueryChange(lastSearchedQuery)
                                scope.launch { searchBarState.animateToCollapsed() }
                            }

                            AnimatedContent(
                                targetState = searchBarState.currentValue == SearchBarValue.Expanded
                            ) { targetState ->
                                if (targetState) {
                                    IconButton(onClick = onDismiss) {
                                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
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
                                visible = searchBarState.currentValue == SearchBarValue.Expanded && query.isNotEmpty()
                            ) {
                                IconButton(onClick = {
                                    viewModel.clearQuery()
                                }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Clear"
                                    )
                                }
                            }
                        },
                    )
                }

            SearchBar(
                state = searchBarState,
                inputField = inputField,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
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
                            onInsert = { viewModel.onQueryChange(suggestion) },
                            modifier = Modifier
                                .animateItem()
                                .clickable {
                                    viewModel.onQueryChange(suggestion)
                                    viewModel.performSearch()
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
                            onInsert = { viewModel.onQueryChange(suggestion) },
                            modifier = Modifier
                                .animateItem()
                                .clickable {
                                    viewModel.onQueryChange(suggestion)
                                    viewModel.performSearch()
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
                            modifier = Modifier
                                .animateItem()
                                .clickable {
                                    when (item.type) {
                                        SearchItem.Type.SONG -> viewModel.playSong(item.id)
                                        SearchItem.Type.ARTIST -> onNavigateToArtist(item.id)
                                        SearchItem.Type.ALBUM -> onNavigateToAlbum(item.id)
                                        SearchItem.Type.PLAYLIST -> onNavigateToPlaylist(item.id)
                                    }
                                }
                                .fillMaxWidth()
                        )
                    }
                }
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
                                .padding(32.dp),
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
                                    text = "Search for music",
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
                            CircularProgressIndicator()
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
                                text = "No results found",
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
                                    modifier = Modifier
                                        .animateItem()
                                        .clickable {
                                            when (item.type) {
                                                SearchItem.Type.SONG -> viewModel.playSong(item.id)
                                                SearchItem.Type.ARTIST -> onNavigateToArtist(item.id)
                                                SearchItem.Type.ALBUM -> onNavigateToAlbum(item.id)
                                                SearchItem.Type.PLAYLIST -> onNavigateToPlaylist(
                                                    item.id
                                                )
                                            }
                                        }
                                        .fillMaxWidth()
                                )
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
                                    text = "Error",
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
    }
}
