package com.viperplayer.presentation.rec

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.domain.model.PlaybackContext
import com.viperplayer.domain.model.RecEmptyReason
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.repository.DaylistRepository
import com.viperplayer.domain.repository.PlayerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for the full **Daylist** screen: loads the current time-of-day daylist via
 * [DaylistRepository.currentDaylist] and maps it into the shared [RecUiState] the recommendation screen
 * renders. All data + playback logic lives here (MVVM); playback routes through the normal player path
 * under [PlaybackContext.Daylist].
 */
@HiltViewModel
class DaylistViewModel @Inject constructor(
    private val daylistRepository: DaylistRepository,
    private val playerRepository: PlayerRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecUiState(isLoading = true))
    val uiState = _uiState.asStateFlow()

    val currentSong = playerRepository.currentSong
    val isPlaying = playerRepository.playbackState
        .map { it.isPlaying }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        _uiState.update { it.copy(isLoading = true, emptyReason = null) }
        viewModelScope.launch {
            val daylist = try {
                daylistRepository.currentDaylist()
            } catch (e: Exception) {
                Timber.w(e, "Daylist: currentDaylist failed")
                null
            }
            _uiState.update {
                if (daylist == null) {
                    // No daylist available (disabled / cold taste / unindexed) → a graceful empty state.
                    it.copy(isLoading = false, songs = emptyList(), emptyReason = RecEmptyReason.NO_RESULTS)
                } else {
                    it.copy(
                        isLoading = false,
                        title = daylist.title,
                        subtitle = daylist.description,
                        songs = daylist.songs,
                        emptyReason = null,
                    )
                }
            }
        }
    }

    fun playAll() = play(_uiState.value.songs, 0)

    fun playSong(song: Song) {
        val songs = _uiState.value.songs
        val index = songs.indexOfFirst { it.id == song.id }
        if (index >= 0) play(songs, index) else play(listOf(song), 0)
    }

    fun shuffle() = play(_uiState.value.songs.shuffled(), 0)

    fun playNext(song: Song) = launchSafe { playerRepository.playNext(song) }

    fun addToQueue(song: Song) = launchSafe { playerRepository.addToQueue(song) }

    private fun play(songs: List<Song>, index: Int) {
        if (songs.isEmpty()) return
        launchSafe { playerRepository.playAll(songs, index, PlaybackContext.Daylist) }
    }

    private inline fun launchSafe(crossinline block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                Timber.w(e, "Daylist playback op failed")
            }
        }
    }
}
