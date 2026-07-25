package com.viperplayer.presentation.detail

import android.icu.text.ListFormatter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.R
import com.viperplayer.data.resources.StringProvider
import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.MediaItem
import com.viperplayer.domain.model.PlaybackContext
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.model.toArtist
import com.viperplayer.domain.repository.MediaLibraryRepository
import com.viperplayer.domain.repository.PlayerRepository
import com.viperplayer.domain.repository.PluginRepository
import com.viperplayer.presentation.navigation.CategoryDetail
import com.viperplayer.presentation.search.model.ItemBadge
import com.viperplayer.presentation.search.model.SearchItem
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * UI state for the category-contents screen. The loaded [items] are the flat, presentation-ready rows
 * ([SearchItem]) produced from the plugin's [MediaItem] page — reusing the shared search-row rendering.
 */
sealed class CategoryContentsUiState {
    data object Loading : CategoryContentsUiState()
    data object Empty : CategoryContentsUiState()
    data class Success(val items: List<SearchItem>) : CategoryContentsUiState()
    data class Error(val message: String) : CategoryContentsUiState()
}

/**
 * ViewModel for the "browse a category's contents" screen. On open it fetches the first page from
 * [PluginRepository.getCategoryContents] for the [CategoryDetail]'s plugin + category, maps the returned
 * [MediaItem]s to [SearchItem]s so the shared search rows can render them, and paginates via the
 * plugin's opaque cursor as the list is scrolled. Item taps play a song or resolve an album / artist /
 * playlist for the host to navigate into — all data + playback logic lives here (MVVM).
 */
@HiltViewModel(assistedFactory = CategoryContentsViewModel.Factory::class)
class CategoryContentsViewModel @AssistedInject constructor(
    @Assisted private val category: CategoryDetail,
    private val pluginRepository: PluginRepository,
    private val mediaLibraryRepository: MediaLibraryRepository,
    private val playerRepository: PlayerRepository,
    private val stringProvider: StringProvider,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(category: CategoryDetail): CategoryContentsViewModel
    }

    /** The category's display name, used to title the screen. */
    val name: String = category.name

    private val _uiState = MutableStateFlow<CategoryContentsUiState>(CategoryContentsUiState.Loading)
    val uiState: StateFlow<CategoryContentsUiState> = _uiState.asStateFlow()

    val currentSong: StateFlow<Song?> = playerRepository.currentSong
    val isPlaying: StateFlow<Boolean> = playerRepository.playbackState
        .map { it.isPlaying }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false,
        )

    private var nextCursor: String? = null
    private var isLoadingMore = false

    // The in-flight paging coroutine (first page or load-more). Cancelled before a fresh [load] so a
    // slow earlier fetch can't append onto — or advance the cursor of — a reloaded list.
    private var pagingJob: Job? = null

    init {
        load()
    }

    /** (Re)load the first page, replacing whatever is currently shown. */
    fun load() {
        nextCursor = null
        isLoadingMore = false
        _uiState.value = CategoryContentsUiState.Loading
        pagingJob?.cancel()
        pagingJob = viewModelScope.launch {
            pluginRepository.getCategoryContents(category.pluginId, category.categoryId)
                .onSuccess { result ->
                    nextCursor = result.nextCursor
                    val current = currentSong.value
                    val items = result.items.map { it.toSearchItem(current) }
                    _uiState.value = when {
                        items.isNotEmpty() -> CategoryContentsUiState.Success(items)
                        // Empty page but more to come: stay in a (momentarily empty) Success so the
                        // load-more sentinel keeps paging until a page has items or the cursor runs out.
                        nextCursor != null -> CategoryContentsUiState.Success(emptyList())
                        else -> CategoryContentsUiState.Empty
                    }
                }
                .onFailure { e ->
                    Timber.e(e, "Failed to load category contents")
                    _uiState.value = CategoryContentsUiState.Error(
                        e.message ?: stringProvider.getString(R.string.category_load_failed)
                    )
                }
        }
    }

    /** Fetch and append the next page, if there is one and no fetch is already in flight. */
    fun loadMore() {
        if (isLoadingMore || nextCursor == null) return
        if (_uiState.value !is CategoryContentsUiState.Success) return

        isLoadingMore = true
        pagingJob = viewModelScope.launch {
            pluginRepository.getCategoryContents(category.pluginId, category.categoryId, nextCursor)
                .onSuccess { result ->
                    nextCursor = result.nextCursor
                    val current = currentSong.value
                    _uiState.update { currentState ->
                        if (currentState is CategoryContentsUiState.Success) {
                            // Dedup on the same (type, id) the list keys on, so a genuine cross-type id
                            // reuse (album X then song X) isn't dropped and the LazyColumn keys stay unique.
                            val existing = currentState.items.map { it.type to it.id }.toSet()
                            val newItems = result.items
                                .map { it.toSearchItem(current) }
                                .filter { (it.type to it.id) !in existing }
                            val merged = currentState.items + newItems
                            // A run of empty pages that exhausts the cursor with nothing to show ends as Empty.
                            if (merged.isEmpty() && nextCursor == null) {
                                CategoryContentsUiState.Empty
                            } else {
                                CategoryContentsUiState.Success(merged)
                            }
                        } else {
                            currentState
                        }
                    }
                    isLoadingMore = false
                }
                .onFailure { e ->
                    // A failed "load more" leaves the already-shown page intact; the sentinel can retry.
                    Timber.w(e, "Failed to load more category contents")
                    isLoadingMore = false
                }
        }
    }

    /** Play a song tapped in the list — resolving the full [Song] from the loaded page or the plugin. */
    fun playSong(songId: MediaId) {
        viewModelScope.launch {
            try {
                val song = findSong(songId) ?: pluginRepository.getSong(songId).getOrNull()?.also {
                    mediaLibraryRepository.saveSong(it)
                }
                if (song != null) {
                    playerRepository.play(song, PlaybackContext.Search(name))
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to play category song: $songId")
            }
        }
    }

    fun playNext(song: Song) {
        viewModelScope.launch { playerRepository.playNext(song) }
    }

    fun addToQueue(song: Song) {
        viewModelScope.launch { playerRepository.addToQueue(song) }
    }

    suspend fun getSong(mediaId: MediaId): Song? =
        findSong(mediaId) ?: pluginRepository.getSong(mediaId).getOrNull()?.also {
            mediaLibraryRepository.saveSong(it)
        }

    suspend fun getAlbum(mediaId: MediaId): Album? =
        pluginRepository.getAlbum(mediaId).getOrNull()?.also { mediaLibraryRepository.saveAlbum(it) }

    suspend fun getArtist(mediaId: MediaId): Artist? =
        pluginRepository.getArtist(mediaId).getOrNull()
            ?.also { mediaLibraryRepository.saveArtist(it) }
            ?.toArtist()

    suspend fun getPlaylist(mediaId: MediaId): Playlist? =
        pluginRepository.getPlaylist(mediaId).getOrNull()?.also { mediaLibraryRepository.savePlaylist(it) }

    /** The full [Song] for [songId] if it's in the loaded page (stored on song rows), else null. */
    private fun findSong(songId: MediaId): Song? {
        val state = _uiState.value
        if (state is CategoryContentsUiState.Success) {
            return state.items.find { it.id == songId && it.type == SearchItem.Type.SONG }?.song
        }
        return null
    }

    /** Map a plugin [MediaItem] to the presentation [SearchItem] the shared search rows render. */
    private fun MediaItem.toSearchItem(current: Song?): SearchItem = when (this) {
        is Song -> SearchItem(
            id = id,
            type = SearchItem.Type.SONG,
            artworkUrl = artworkUrl,
            title = title,
            subtitle = buildString {
                if (artists.isNotEmpty()) {
                    append(ListFormatter.getInstance().format(artists.map { it.name }))
                }
            }.ifBlank { null },
            isActive = current?.id == id,
            badges = buildList { if (isExplicit) add(ItemBadge.EXPLICIT) },
            song = this,
        )

        is Album -> SearchItem(
            id = id,
            type = SearchItem.Type.ALBUM,
            artworkUrl = artworkUrl,
            title = name,
            subtitle = buildString {
                append(stringProvider.getString(R.string.search_subtitle_type_album))
                if (artists.isNotEmpty()) {
                    append(" • ")
                    append(ListFormatter.getInstance().format(artists.map { it.name }))
                }
            },
            isActive = false,
            badges = emptyList(),
        )

        is Artist -> SearchItem(
            id = id,
            type = SearchItem.Type.ARTIST,
            artworkUrl = imageUrl,
            title = name,
            subtitle = null,
            isActive = false,
            badges = emptyList(),
        )

        is Playlist -> SearchItem(
            id = id,
            type = SearchItem.Type.PLAYLIST,
            artworkUrl = artworkUrl,
            title = name,
            subtitle = buildString {
                append(stringProvider.getString(R.string.search_subtitle_type_playlist))
                ownerName?.let {
                    append(" • ")
                    append(it)
                }
            },
            isActive = false,
            badges = emptyList(),
        )
    }
}
