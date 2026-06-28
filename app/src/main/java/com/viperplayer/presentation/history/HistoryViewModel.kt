package com.viperplayer.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.domain.model.HistoryEntry
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.repository.MediaLibraryRepository
import com.viperplayer.domain.repository.PlayerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the History screen: a chronological, newest-first timeline of every play, plus the
 * now-playing wiring and a clear-history action.
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val mediaLibraryRepository: MediaLibraryRepository,
    private val playerRepository: PlayerRepository
) : ViewModel() {

    val currentSong: StateFlow<Song?> = playerRepository.currentSong

    val isPlaying: StateFlow<Boolean> = playerRepository.playbackState
        .map { it.isPlaying }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    /** Flat, newest-first listening history. Display grouping by date is done in the screen. */
    val history: StateFlow<List<HistoryEntry>> = mediaLibraryRepository.getHistory()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /** Play the whole history as a queue, starting from the tapped [index]. */
    fun play(index: Int) {
        viewModelScope.launch {
            val songs = history.value.map { it.song }
            if (index in songs.indices) {
                playerRepository.playAll(songs, index)
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            mediaLibraryRepository.clearHistory()
        }
    }
}
