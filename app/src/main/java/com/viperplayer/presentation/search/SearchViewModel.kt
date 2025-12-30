package com.viperplayer.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.domain.model.SearchResult
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.repository.SearchRepository
import com.viperplayer.domain.usecase.player.PlaySongUseCase
import com.viperplayer.domain.usecase.search.SearchUseCase
import com.viperplayer.plugin.sdk.v1.SearchSuggestionsItemV1
import com.viperplayer.presentation.search.model.ItemBadge
import com.viperplayer.presentation.search.model.SearchItem
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
    val items: List<SearchItem> = listOf(
        SearchItem(
            id = "fake_artist_1",
            type = SearchItem.Type.ARTIST,
            artworkUrl = "https://lh3.googleusercontent.com/YjFzIIa4WZ3XW_jJXbH7Y76RvuxlTtUhBMHqAgQf3i3lMBU-X1XA7MWj9AnvFnR3D9eclkjtBcSdmHo=w120-h120-p-l90-rj",
            title = "Quevedo",
            subtitle = null,
            isActive = false,
            badges = emptyList()
        ),
        SearchItem(
            id = "fake_song_1",
            type = SearchItem.Type.SONG,
            artworkUrl = "https://lh3.googleusercontent.com/B-lHpmZnApE45vdmNIncW8Xx3Rs3I37pS-Fw8Ztlyp79ypFkss7gb-4_7e_8WgtCrfRTvMoJB6LNqtc=w120-h120-l90-rj",
            title = "Quevedo: Bzrp Music Sessions, Vol. 52/66",
            subtitle = "Bizarrap, Quevedo • Quevedo: Bzrp Music Sessions, Vol. 52/66",
            isActive = false,
            badges = listOf(
                ItemBadge.EXPLICIT
            )
        ),
        SearchItem(
            id = "fake_song_2",
            type = SearchItem.Type.SONG,
            artworkUrl = "https://lh3.googleusercontent.com/B-lHpmZnApE45vdmNIncW8Xx3Rs3I37pS-Fw8Ztlyp79ypFkss7gb-4_7e_8WgtCrfRTvMoJB6LNqtc=w120-h120-l90-rj",
            title = "Without Me",
            subtitle = "Eminem • The Eminem Show",
            isActive = false,
            badges = listOf(
                ItemBadge.EXPLICIT,
                ItemBadge.FAVORITE,
                ItemBadge.LIBRARY,
                ItemBadge.DOWNLOADING,
                ItemBadge.DOWNLOADED
            )
        )
    ),

    // Search results
    val isSearching: Boolean = false,
    val results: SearchResult? = null,
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
    private val playSongUseCase: PlaySongUseCase,
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
            val items = successfulResults.map { it.items }.flatten().map {
                when (it.type) {
                    SearchSuggestionsItemV1.Type.SONG -> it.song.let {
                        if (it == null) throw IllegalArgumentException("Song can't be null")

                        val subtitle = buildString {
                            append(it.artists.joinToString { it.name })
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

                    SearchSuggestionsItemV1.Type.ARTIST -> it.artist.let {
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

                    SearchSuggestionsItemV1.Type.ALBUM -> it.album.let {
                        if (it == null) throw IllegalArgumentException("Album can't be null")

                        val subtitle = buildString {
                            append(it.artists.joinToString { it.name })
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

                    SearchSuggestionsItemV1.Type.PLAYLIST -> it.playlist.let {
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
            _uiState.update { it.copy(
                suggestions = suggestions,
                items = items,
            ) }
        }
    }
    
    private suspend fun performSearch(query: String) {
        if (query.isBlank()) {
            _uiState.update { it.copy(results = null) }
            return
        }

        _uiState.update { it.copy(isSearching = true, error = null) }
        
        searchUseCase(query)
            .onSuccess { results ->
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        results = results
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
    
    fun playSong(song: Song) {
        viewModelScope.launch {
            playSongUseCase(song)
        }
    }

    fun removeHistoryEntry(history: String) {

    }
}

