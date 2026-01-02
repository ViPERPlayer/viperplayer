package com.viperplayer.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.repository.PlayerRepository
import com.viperplayer.domain.repository.SearchRepository
import com.viperplayer.domain.usecase.search.SearchUseCase
import com.viperplayer.plugin.sdk.v1.SearchSuggestionsItemV1
import com.viperplayer.presentation.search.model.ItemBadge
import com.viperplayer.presentation.search.model.SearchItem
import com.viperplayer.presentation.search.model.SearchSection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI State for Search screen.
 */
data class SearchUiState(
    // Search box
    val history: List<String> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val items: List<SearchItem> = emptyList(),

    // Search results
    val isSearching: Boolean = false,
    val results: List<SearchSection> = emptyList(),
    val error: String? = null,

    // Random TODO: Move somewhere else?
    val isPlaying: Boolean = false,
)

/**
 * ViewModel for Search screen.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchUseCase: SearchUseCase,
    private val playerRepository: PlayerRepository,
    private val searchRepository: SearchRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    init {
        // Debounced search
        viewModelScope.launch {
            _query
//                .debounce(300)
//                .distinctUntilChanged()
                .collect { query ->
                    getSearchSuggestions(query)
                }
        }
    }
    
    fun onQueryChange(query: String) {
        _query.update { query }
    }

    private fun SearchSuggestionsItemV1.toSearchItem(): SearchItem {
        return when (this.type) {
            SearchSuggestionsItemV1.Type.SONG -> this.song.let {
                if (it == null) throw IllegalArgumentException("Song can't be null")

                val subtitle = buildString {
                    if (it.artists.size > 1) {
                        it.artists.forEachIndexed { index, artist ->
                            if (index == it.artists.size - 1) {
                                append(" and ")
                            } else if (index > 0) {
                                append(", ")
                            }
                            append(artist.name)
                        }
                    } else {
                        append(it.artists.joinToString { it.name })
                    }
                    it.album?.let { album ->
                        if (it.artists.isNotEmpty()) append(" • ")
                        append(album.name)
                    }
                }

                SearchItem(
                    id = it.id,
                    type = SearchItem.Type.SONG,
                    artworkUrl = it.artworkUrl,
                    title = it.title,
                    subtitle = subtitle,
                    isActive = false,
                    badges = buildList {
                        if (it.isExplicit) {
                            add(ItemBadge.EXPLICIT)
                        }
                    }
                )
            }

            SearchSuggestionsItemV1.Type.ARTIST -> this.artist.let {
                if (it == null) throw IllegalArgumentException("Artist can't be null")
                SearchItem(
                    id = it.id,
                    type = SearchItem.Type.ARTIST,
                    artworkUrl = it.imageUrl,
                    title = it.name,
                    subtitle = null,
                    isActive = false,
                    badges = emptyList()
                )
            }

            SearchSuggestionsItemV1.Type.ALBUM -> this.album.let {
                if (it == null) throw IllegalArgumentException("Album can't be null")

                val subtitle = buildString {
                    if (it.artists.size > 1) {
                        it.artists.forEachIndexed { index, artist ->
                            if (index == it.artists.size - 1) {
                                append(" and ")
                            } else if (index > 0) {
                                append(", ")
                            }
                            append(artist.name)
                        }
                    } else {
                        append(it.artists.joinToString { it.name })
                    }
                    it.releaseYear?.let { releaseYear ->
                        if (it.artists.isNotEmpty()) append(" • ")
                        append(releaseYear)
                    }
                }

                SearchItem(
                    id = it.id,
                    type = SearchItem.Type.ALBUM,
                    artworkUrl = it.artworkUrl,
                    title = it.name,
                    subtitle = subtitle,
                    isActive = false,
                    badges = buildList {
                        if (it.isExplicit) {
                            add(ItemBadge.EXPLICIT)
                        }
                    }
                )
            }

            SearchSuggestionsItemV1.Type.PLAYLIST -> this.playlist.let {
                if (it == null) throw IllegalArgumentException("Playlist can't be null")
                SearchItem(
                    id = it.id,
                    type = SearchItem.Type.PLAYLIST,
                    artworkUrl = it.artworkUrl,
                    title = it.name,
                    subtitle = it.ownerName,
                    isActive = false,
                    badges = emptyList()
                )
            }
        }
    }

    private suspend fun getSearchSuggestions(query: String) {
        if (query.isBlank()) {
            _uiState.update { it.copy(
                history = emptyList(),
                suggestions = emptyList(),
//                items = emptyList(),
            ) }
            return
        }

        _uiState.update {
            it.copy(
                history = listOf(query)
            )
        }

        searchRepository.getSuggestions(query).collect { results ->
            val successfulResults = results.mapNotNull { it.getOrNull() }
            val suggestions = successfulResults.map { it.suggestions }.flatten().distinct()
            val items = successfulResults.map { it.items }.flatten().map { it.toSearchItem() }
            _uiState.update { it.copy(
                suggestions = suggestions,
                items = items,
            ) }
        }
    }
    
    fun performSearch(query: String) {
        if (query.isBlank()) {
            _uiState.update { it.copy(results = emptyList()) }
            return
        }

        _uiState.update { it.copy(isSearching = true, error = null) }
        
        viewModelScope.launch {
            searchUseCase(query)
                .onSuccess { results ->
                    _uiState.update {
                        it.copy(
                            isSearching = false,
                            results = results.sections.map { section ->
                                SearchSection(
                                    title = when (section.type) {
                                        com.viperplayer.plugin.sdk.v1.SearchResult.Section.Type.TOP_RESULT -> "Top result"
                                        com.viperplayer.plugin.sdk.v1.SearchResult.Section.Type.OTHER -> "Other"
                                    },
                                    items = section.items.map { it.toSearchItem() }
                                )
                            }
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isSearching = false,
                            error = e.message ?: "Search failed"
                        )
                    }
                }
        }
    }
    
    fun playSong(song: Song) {
        viewModelScope.launch {
            playerRepository.play(song)
        }
    }

    fun removeHistoryEntry(history: String) {

    }
}

