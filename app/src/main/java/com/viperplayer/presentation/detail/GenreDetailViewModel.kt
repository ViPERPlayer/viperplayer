package com.viperplayer.presentation.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.domain.model.PlaybackContext
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.model.SortOrder
import com.viperplayer.domain.model.SortView
import com.viperplayer.domain.repository.MediaLibraryRepository
import com.viperplayer.domain.repository.PlayerRepository
import com.viperplayer.domain.repository.SettingsRepository
import com.viperplayer.domain.sort.MediaSorter
import com.viperplayer.presentation.navigation.GenreDetail
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * UI state for the Genre Detail screen: the genre's name and its (sorted) songs, pulled reactively from
 * the local library. Unlike the plugin-backed album/artist details there is no separate Loading/Error
 * remote fetch — the local song list simply starts empty and fills in.
 */
data class GenreDetailUiState(
    val name: String,
    val songs: List<Song> = emptyList(),
    val sortOrder: SortOrder = SortOrder.DEFAULT,
    val isLoading: Boolean = true,
)

/**
 * ViewModel for the Genre Detail screen. Observes the local songs tagged with the genre (identified by
 * its Room row id) and re-sorts them per the persisted [SortView.LIBRARY_SONGS] order, so the visible
 * list tracks library changes without a reload. All data logic lives here — the screen only renders
 * state and forwards events.
 */
@HiltViewModel(assistedFactory = GenreDetailViewModel.Factory::class)
class GenreDetailViewModel @AssistedInject constructor(
    @Assisted private val genreDetail: GenreDetail,
    private val mediaLibraryRepository: MediaLibraryRepository,
    private val playerRepository: PlayerRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(genreDetail: GenreDetail): GenreDetailViewModel
    }

    private val genreId = genreDetail.genreId

    private val _uiState = MutableStateFlow(GenreDetailUiState(name = genreDetail.initialName))
    val uiState = _uiState.asStateFlow()

    // Expose current song and playing state from the player repository (drives the now-playing row).
    val currentSong = playerRepository.currentSong
    val isPlaying = playerRepository.playbackState
        .map { it.isPlaying }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    // Raw (title-ordered) songs as loaded, kept so a sort-menu change re-sorts without a reload.
    private var rawSongs: List<Song> = emptyList()
    // Latest persisted order, tracked even before the first load so a fresh emission applies it.
    private var currentSortOrder: SortOrder = SortOrder.DEFAULT

    init {
        observeSongs()
        observeSortOrder()
    }

    /** Perpetual collector of the genre's local songs; re-sorts + republishes on every change. */
    private fun observeSongs() {
        viewModelScope.launch {
            mediaLibraryRepository.getSongsForGenre(genreId).collect { songs ->
                rawSongs = songs
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        songs = MediaSorter.sortSongs(songs, currentSortOrder),
                    )
                }
            }
        }
    }

    /** Re-apply the persisted song order whenever it changes (or on first load). */
    private fun observeSortOrder() {
        viewModelScope.launch {
            settingsRepository.sortOrder(SortView.LIBRARY_SONGS).collect { order ->
                currentSortOrder = order
                _uiState.update {
                    it.copy(sortOrder = order, songs = MediaSorter.sortSongs(rawSongs, order))
                }
            }
        }
    }

    /** Persist a new song [order]; the observer re-sorts the visible list. */
    fun setSortOrder(order: SortOrder) {
        viewModelScope.launch {
            settingsRepository.setSortOrder(SortView.LIBRARY_SONGS, order)
        }
    }

    /** Play the whole genre starting at [song] (or just [song] if it isn't in the visible list). */
    fun playSong(song: Song) {
        viewModelScope.launch {
            try {
                val songs = _uiState.value.songs
                val context = PlaybackContext.Genre(genreId, _uiState.value.name)
                val index = songs.indexOfFirst { it.id == song.id }
                if (index != -1) {
                    playerRepository.playAll(songs, index, context)
                } else {
                    playerRepository.play(song, context)
                }
            } catch (e: Exception) {
                Timber.w(e, "GenreDetail background operation failed")
            }
        }
    }

    fun playAll() {
        viewModelScope.launch {
            try {
                val songs = _uiState.value.songs
                if (songs.isNotEmpty()) {
                    playerRepository.playAll(songs, 0, PlaybackContext.Genre(genreId, _uiState.value.name))
                }
            } catch (e: Exception) {
                Timber.w(e, "GenreDetail background operation failed")
            }
        }
    }

    fun shuffle() {
        viewModelScope.launch {
            try {
                val songs = _uiState.value.songs.shuffled()
                if (songs.isNotEmpty()) {
                    playerRepository.playAll(songs, 0, PlaybackContext.Genre(genreId, _uiState.value.name))
                }
            } catch (e: Exception) {
                Timber.w(e, "GenreDetail background operation failed")
            }
        }
    }

    fun playNext(song: Song) {
        viewModelScope.launch {
            try {
                playerRepository.playNext(song)
            } catch (e: Exception) {
                Timber.w(e, "GenreDetail background operation failed")
            }
        }
    }

    fun addToQueue(song: Song) {
        viewModelScope.launch {
            try {
                playerRepository.addToQueue(song)
            } catch (e: Exception) {
                Timber.w(e, "GenreDetail background operation failed")
            }
        }
    }
}
