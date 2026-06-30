package com.viperplayer.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.MediaItem
import com.viperplayer.domain.model.PlaybackContext
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.repository.MediaLibraryRepository
import com.viperplayer.domain.repository.PlayerRepository
import com.viperplayer.domain.repository.PluginRepository
import com.viperplayer.domain.repository.SearchRepository
import com.viperplayer.domain.usecase.search.SearchUseCase
import com.viperplayer.domain.model.SearchFilter
import com.viperplayer.presentation.search.model.ItemBadge
import com.viperplayer.presentation.search.model.SearchItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
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
    data class Results(val items: List<SearchItem>) : SearchResultsState()
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
    private val searchRepository: SearchRepository,
    private val mediaLibraryRepository: MediaLibraryRepository,
    private val searchUseCase: SearchUseCase
) : ViewModel() {
    // Expose current song and playing state from player repository
    val currentSong: StateFlow<Song?> = playerRepository.currentSong
    val isPlaying: StateFlow<Boolean> = playerRepository.playbackState
        .map { it.isPlaying }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    private val _searchSuggestionsState = MutableStateFlow(SearchSuggestionsState())
    // isActive is derived per-item in the UI from currentSong, so the list is NOT rebuilt (every item
    // re-allocated) on every track change.
    val searchSuggestionsState: StateFlow<SearchSuggestionsState> = _searchSuggestionsState.asStateFlow()

    private val _searchResultsState = MutableStateFlow<SearchResultsState>(SearchResultsState.Idle)
    val searchResultsState: StateFlow<SearchResultsState> = _searchResultsState.asStateFlow()

    private val _query = MutableStateFlow("")

    private val _lastSearchedQuery = MutableStateFlow("")
    val lastSearchedQuery = _lastSearchedQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow<SearchFilter?>(null)
    val selectedFilter = _selectedFilter.asStateFlow()

    private var nextCursor: String? = null
    private var isSearchingMore = false

    // The in-flight full-search coroutine; cancel it before each new search so a slow earlier query
    // can't overwrite a newer one (or advance pagination from the wrong cursor).
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            _query.debounce(250L).flatMapLatest { query ->
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
            _query.debounce(250L).flatMapLatest { query ->
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
                    // Get current song to set isActive
                    val current = currentSong.value
                    val items = successfulResults.map { it.items }.flatten()
                        .map { it.toSearchItem(current) }
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

    fun onFilterSelected(filter: SearchFilter?) {
        _selectedFilter.value = filter
        if (_query.value.isNotBlank()) {
            performSearch(_query.value, resetFilter = false)
        }
    }

    private fun MediaItem.toSearchItem(currentSong: Song? = null): SearchItem {
        return when (this) {
            is Song -> this.let {
                val subtitle = buildString {
                    append("Song")
                    if (it.artists.isNotEmpty()) {
                        append(" • ")
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
                    }
//                    it.album?.let { album ->
//                        append(" • ")
//                        append(album.name)
//                    }
                }

                SearchItem(
                    id = it.id,
                    type = SearchItem.Type.SONG,
                    artworkUrl = it.artworkUrl,
                    title = it.title,
                    subtitle = subtitle,
                    isActive = currentSong?.id == it.id,
                    badges = buildList {
                        if (it.isExplicit) {
                            add(ItemBadge.EXPLICIT)
                        }
                    },
                    song = it // Store the full Song object
                )
            }

            is Artist -> this.let {
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

            is Album -> this.let {
                val subtitle = buildString {
                    append("Album")
                    if (it.artists.isNotEmpty()) {
                        append(" • ")
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
                    }
                    it.releaseYear?.let { releaseYear ->
                        append(" • ")
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
                        if (it.type == com.viperplayer.domain.model.AlbumType.COMPILATION) {
                            add(ItemBadge.EXPLICIT)
                        }
                    }
                )
            }

            is Playlist -> this.let {
                val subtitle = buildString {
                    append("Playlist")
                    it.ownerName?.let { ownerName ->
                        append(" • ")
                        append(ownerName)
                    }
                }
                SearchItem(
                    id = it.id,
                    type = SearchItem.Type.PLAYLIST,
                    artworkUrl = it.artworkUrl,
                    title = it.name,
                    subtitle = subtitle,
                    isActive = false,
                    badges = emptyList()
                )
            }
        }
    }

    fun performSearch(query: String, resetFilter: Boolean = true) {
        if (resetFilter) {
            _selectedFilter.value = null
        }

        if (query.isBlank()) {
            _searchResultsState.value = SearchResultsState.Idle
            _lastSearchedQuery.value = ""
            nextCursor = null
            return
        }

        _searchResultsState.value = SearchResultsState.Searching
        _lastSearchedQuery.value = query
        nextCursor = null

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            searchRepository.saveSearchHistory(query)

            searchUseCase(query, _selectedFilter.value)
                .onSuccess { results ->
                    nextCursor = results.nextCursor
                    // Convert domain SearchSectionItem to presentation SearchItem
                    // Get current song to set isActive
                    val current = currentSong.value
                    val items = results.items.map { it.toSearchItem(current) }
                    _searchResultsState.value = if (items.isEmpty()) {
                        SearchResultsState.Empty
                    } else {
                        SearchResultsState.Results(items)
                    }
                }
                .onFailure { e ->
                    _searchResultsState.value =
                        SearchResultsState.Error(e.message ?: "Search failed")
                }
        }
    }

    fun loadMore() {
        if (isSearchingMore || nextCursor == null) return
        val query = _lastSearchedQuery.value
        if (query.isBlank()) return

        isSearchingMore = true
        viewModelScope.launch {
            searchUseCase(query, _selectedFilter.value, nextCursor)
                .onSuccess { results ->
                    isSearchingMore = false
                    nextCursor = results.nextCursor

                    _searchResultsState.update { currentState ->
                        if (currentState is SearchResultsState.Results) {
                            val current = currentSong.value
                            val newItems = results.items.map { it.toSearchItem(current) }
                            // Filter out duplicates if any, though usually backend handles this
                            val existingIds = currentState.items.map { it.id }.toSet()
                            val uniqueNewItems = newItems.filter { !existingIds.contains(it.id) }

                            SearchResultsState.Results(currentState.items + uniqueNewItems)
                        } else {
                            currentState
                        }
                    }
                }
                .onFailure {
                    isSearchingMore = false
                }
        }
    }

    fun playSong(songId: MediaId) {
        viewModelScope.launch {
            try {
                // Try to find the song in search results first
                val songFromResults = findSongInResults(songId)
                if (songFromResults != null) {
                    playerRepository.play(songFromResults, PlaybackContext.Search)
                    return@launch
                }

                // If not found, fetch it from the repository
                val songResult = pluginRepository.getSong(songId)
                songResult.onSuccess { song ->
                    // Save song to database
                    mediaLibraryRepository.saveSong(song)
                    playerRepository.play(song, PlaybackContext.Search)
                }.onFailure { error ->
                    // Handle error - could show a toast or error message
                    Timber.e(error, "Failed to play song: $songId")
                }
            } catch (e: Exception) {
                Timber.e(e, "Exception while playing song: $songId")
            }
        }
    }

    private fun findSongInResults(songId: MediaId): Song? {
        // Check search results
        val resultsState = _searchResultsState.value
        if (resultsState is SearchResultsState.Results) {
            val item =
                resultsState.items.find { it.id == songId && it.type == SearchItem.Type.SONG }
            if (item?.song != null) {
                return item.song
            }
        }

        // Check search suggestions
        val suggestionsState = _searchSuggestionsState.value
        val item =
            suggestionsState.items.find { it.id == songId && it.type == SearchItem.Type.SONG }
        return item?.song
    }

    fun removeHistoryEntry(history: String) {
        viewModelScope.launch {
            searchRepository.removeHistoryEntry(history)
        }
    }

    fun playNext(song: Song) {
        viewModelScope.launch {
            playerRepository.playNext(song)
        }
    }

    fun addToQueue(song: Song) {
        viewModelScope.launch {
            playerRepository.addToQueue(song)
        }
    }

    fun toggleLike(song: Song) {
        viewModelScope.launch {
            try {
                mediaLibraryRepository.saveSong(song)
                val currentSongFromDb = mediaLibraryRepository.getSong(song.id).first()
                val isCurrentlyLiked = currentSongFromDb?.isLiked ?: false
                mediaLibraryRepository.setSongLiked(song.id, !isCurrentlyLiked)
            } catch (e: Exception) {
                Timber.e(e, "Failed to toggle like for song: ${song.title}")
            }
        }
    }

    suspend fun getSong(mediaId: MediaId): Song? {
        return findSongInResults(mediaId) ?: run {
            val song = pluginRepository.getSong(mediaId).getOrNull()
            song?.let { mediaLibraryRepository.saveSong(it) }
            song
        }
    }

    suspend fun getAlbum(mediaId: MediaId): Album? {
        val album = pluginRepository.getAlbum(mediaId).getOrNull()
        album?.let { mediaLibraryRepository.saveAlbum(it) }
        return album
    }

    suspend fun getArtist(mediaId: MediaId): Artist? {
        val artist = pluginRepository.getArtist(mediaId).getOrNull()
        artist?.let { mediaLibraryRepository.saveArtist(it) }
        return artist
    }

    suspend fun getPlaylist(mediaId: MediaId): Playlist? {
        val playlist = pluginRepository.getPlaylist(mediaId).getOrNull()
        playlist?.let { mediaLibraryRepository.savePlaylist(it) }
        return playlist
    }
}
