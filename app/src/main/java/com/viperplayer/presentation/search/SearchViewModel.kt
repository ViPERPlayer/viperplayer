package com.viperplayer.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.SearchSuggestionItem
import com.viperplayer.domain.model.SearchSuggestionType
import com.viperplayer.domain.repository.PlayerRepository
import com.viperplayer.domain.repository.PluginRepository
import com.viperplayer.domain.repository.SearchRepository
import com.viperplayer.presentation.search.model.ItemBadge
import com.viperplayer.presentation.search.model.SearchItem
import com.viperplayer.presentation.search.model.SearchSection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Represents the different states of search suggestions (history, suggestions, items).
 */
data class SearchSuggestionsState(
    val history: List<String> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val items: List<SearchItem> = emptyList()
)

/**
 * Represents the different states of search results for animation.
 */
sealed class SearchResultsState {
    data object Idle : SearchResultsState()
    data object Searching : SearchResultsState()
    data class Results(val sections: List<SearchSection>) : SearchResultsState()
    data object Empty : SearchResultsState()
    data class Error(val message: String) : SearchResultsState()
}

/**
 * ViewModel for Search screen.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val pluginRepository: PluginRepository,
    private val playerRepository: PlayerRepository,
    private val searchRepository: SearchRepository
) : ViewModel() {
    private val _searchSuggestionsState = MutableStateFlow(SearchSuggestionsState())
    val searchSuggestionsState = _searchSuggestionsState.asStateFlow()

    private val _searchResultsState = MutableStateFlow<SearchResultsState>(SearchResultsState.Idle)
    val searchResultsState = _searchResultsState.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()
    
    private val _lastSearchedQuery = MutableStateFlow("")
    val lastSearchedQuery = _lastSearchedQuery.asStateFlow()

    init {
        viewModelScope.launch {
            _query.flatMapLatest { query ->
                if (query.isBlank()) {
                    searchRepository.getRecentHistory(limit = 10)
                } else {
                    searchRepository.getHistoryContaining(query, limit = 10)
                }
            }.collect { history ->
                _searchSuggestionsState.update {
                    it.copy(history = history)
                }
            }
        }

        viewModelScope.launch {
            _query.flatMapLatest { query ->
                if (query.isBlank()) {
                    flowOf(emptyList())
                } else {
                    searchRepository.getSuggestions(query)
                }
            }.collect { suggestionsResults ->
                if (suggestionsResults.isEmpty()) {
                    _searchSuggestionsState.update {
                        it.copy(
                            suggestions = emptyList(),
                            items = emptyList()
                        )
                    }
                } else {
                    val successfulResults = suggestionsResults.mapNotNull { it.getOrNull() }
                    val suggestions = successfulResults.map { it.suggestions }.flatten().distinct()
                    val items = successfulResults.map { it.items }.flatten().map { it.toSearchItem() }
                    _searchSuggestionsState.update {
                        it.copy(
                            suggestions = suggestions,
                            items = items
                        )
                    }
                }
            }
        }
    }
    
    fun onQueryChange(query: String) {
        _query.value = query
    }

    fun clearQuery() {
        _query.value = ""
    }

    private fun SearchSuggestionItem.toSearchItem(): SearchItem {
        return when (this.type) {
            SearchSuggestionType.SONG -> this.song.let {
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
                    id = MediaId("", it.id.sourceId),
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

            SearchSuggestionType.ARTIST -> this.artist.let {
                if (it == null) throw IllegalArgumentException("Artist can't be null")
                SearchItem(
                    id = MediaId("", it.id.sourceId),
                    type = SearchItem.Type.ARTIST,
                    artworkUrl = it.imageUrl,
                    title = it.name,
                    subtitle = null,
                    isActive = false,
                    badges = emptyList()
                )
            }

            SearchSuggestionType.ALBUM -> this.album.let {
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
                    id = MediaId("", it.id.sourceId),
                    type = SearchItem.Type.ALBUM,
                    artworkUrl = it.artworkUrl,
                    title = it.name,
                    subtitle = subtitle,
                    isActive = false,
                    badges = buildList {
                        if (it.type == com.viperplayer.domain.model.AlbumType.COMPILATION) {
                            add(ItemBadge.EXPLICIT)
                        }
                    }
                )
            }

            SearchSuggestionType.PLAYLIST -> this.playlist.let {
                if (it == null) throw IllegalArgumentException("Playlist can't be null")
                SearchItem(
                    id = MediaId("", it.id.sourceId),
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

    fun performSearch() {
        if (query.value.isBlank()) {
            _searchResultsState.value = SearchResultsState.Idle
            _lastSearchedQuery.value = ""
            return
        }

        _searchResultsState.value = SearchResultsState.Searching
        _lastSearchedQuery.value = query.value
        
        viewModelScope.launch {
            // Save search to history
            searchRepository.saveSearchHistory(query.value)
            
            pluginRepository.search(query.value)
                .onSuccess { results ->
                    // Convert domain SearchSectionItem to presentation SearchItem
                    val sections = results.sections.map { section ->
                        SearchSection(
                            title = when (section.type) {
                                com.viperplayer.domain.model.SearchSectionType.TOP_RESULT -> "Top Result"
                                com.viperplayer.domain.model.SearchSectionType.OTHER -> "Other"
                            },
                            items = section.items.map { item ->
                                when (item.type) {
                                    com.viperplayer.domain.model.SearchSectionItemType.SONG -> {
                                        if (item.song == null) throw IllegalArgumentException("Song can't be null")
                                        item.song.toSearchItem()
                                    }
                                    com.viperplayer.domain.model.SearchSectionItemType.ARTIST -> {
                                        if (item.artist == null) throw IllegalArgumentException("Artist can't be null")
                                        item.artist.toSearchItem()
                                    }
                                    com.viperplayer.domain.model.SearchSectionItemType.ALBUM -> {
                                        if (item.album == null) throw IllegalArgumentException("Album can't be null")
                                        item.album.toSearchItem()
                                    }
                                    com.viperplayer.domain.model.SearchSectionItemType.PLAYLIST -> {
                                        if (item.playlist == null) throw IllegalArgumentException("Playlist can't be null")
                                        item.playlist.toSearchItem()
                                    }
                                }
                            }
                        )
                    }
                    _searchResultsState.value = if (sections.isEmpty()) {
                        SearchResultsState.Empty
                    } else {
                        SearchResultsState.Results(sections)
                    }
                }
                .onFailure { e ->
                    _searchResultsState.value = SearchResultsState.Error(e.message ?: "Search failed")
                }
        }
    }
    
    fun playSong(songId: MediaId) {
        viewModelScope.launch {
//            playerRepository.play(song)
        }
    }

    fun removeHistoryEntry(history: String) {
        viewModelScope.launch {
            searchRepository.removeHistoryEntry(history)
        }
    }

    // Extension functions to convert domain models to SearchItem
    private fun com.viperplayer.domain.model.Song.toSearchItem(): SearchItem {
        val subtitle = buildString {
            if (artists.size > 1) {
                artists.forEachIndexed { index, artist ->
                    if (index == artists.size - 1) {
                        append(" and ")
                    } else if (index > 0) {
                        append(", ")
                    }
                    append(artist.name)
                }
            } else {
                append(artists.joinToString { it.name })
            }
            album?.let { album ->
                if (artists.isNotEmpty()) append(" • ")
                append(album.name)
            }
        }

        return SearchItem(
            id = id,
            type = SearchItem.Type.SONG,
            artworkUrl = effectiveArtworkUrl,
            title = title,
            subtitle = subtitle,
            isActive = false,
            badges = buildList {
                if (isExplicit) {
                    add(ItemBadge.EXPLICIT)
                }
            }
        )
    }

    private fun com.viperplayer.domain.model.Artist.toSearchItem(): SearchItem {
        return SearchItem(
            id = id,
            type = SearchItem.Type.ARTIST,
            artworkUrl = imageUrl,
            title = name,
            subtitle = null,
            isActive = false,
            badges = emptyList()
        )
    }

    private fun com.viperplayer.domain.model.Album.toSearchItem(): SearchItem {
        val subtitle = buildString {
            if (artists.size > 1) {
                artists.forEachIndexed { index, artist ->
                    if (index == artists.size - 1) {
                        append(" and ")
                    } else if (index > 0) {
                        append(", ")
                    }
                    append(artist.name)
                }
            } else {
                append(artists.joinToString { it.name })
            }
            releaseYear?.let { year ->
                if (artists.isNotEmpty()) append(" • ")
                append(year)
            }
        }

        return SearchItem(
            id = id,
            type = SearchItem.Type.ALBUM,
            artworkUrl = artworkUrl,
            title = name,
            subtitle = subtitle,
            isActive = false,
            badges = emptyList()
        )
    }

    private fun com.viperplayer.domain.model.Playlist.toSearchItem(): SearchItem {
        return SearchItem(
            id = id,
            type = SearchItem.Type.PLAYLIST,
            artworkUrl = artworkUrl,
            title = name,
            subtitle = ownerName,
            isActive = false,
            badges = emptyList()
        )
    }
}
