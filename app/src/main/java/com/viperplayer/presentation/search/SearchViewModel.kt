package com.viperplayer.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.domain.model.SearchResult
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.usecase.player.PlaySongUseCase
import com.viperplayer.domain.usecase.search.SearchUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI State for Search screen.
 */
data class SearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val results: SearchResult? = null,
    val error: String? = null
)

/**
 * ViewModel for Search screen.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchUseCase: SearchUseCase,
    private val playSongUseCase: PlaySongUseCase
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()
    
    private val _searchQuery = MutableStateFlow("")
    
    init {
        // Debounced search
        viewModelScope.launch {
            _searchQuery
                .debounce(300)
                .distinctUntilChanged()
                .collect { query ->
                    performSearch(query)
                }
        }
    }
    
    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        _searchQuery.value = query
    }
    
    fun clearQuery() {
        onQueryChange("")
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
}

