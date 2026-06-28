package com.viperplayer.presentation.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.model.SongWithStats
import com.viperplayer.domain.model.StatPeriod
import com.viperplayer.domain.model.StatsSummary
import com.viperplayer.domain.repository.MediaLibraryRepository
import com.viperplayer.domain.repository.PlayerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Stats screen: a selected [StatPeriod] drives a time-windowed summary and a
 * ranked "most played" list, plus play-all / shuffle and now-playing wiring.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel @Inject constructor(
    private val mediaLibraryRepository: MediaLibraryRepository,
    private val playerRepository: PlayerRepository
) : ViewModel() {

    private val _period = MutableStateFlow(StatPeriod.PAST_WEEK)
    val period: StateFlow<StatPeriod> = _period.asStateFlow()

    val currentSong: StateFlow<Song?> = playerRepository.currentSong

    val isPlaying: StateFlow<Boolean> = playerRepository.playbackState
        .map { it.isPlaying }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val mostPlayed: StateFlow<List<SongWithStats>> = _period
        .flatMapLatest { period ->
            mediaLibraryRepository.getMostPlayedSongs(period, limit = 100)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val summary: StateFlow<StatsSummary> = _period
        .flatMapLatest { period ->
            mediaLibraryRepository.getStatsSummary(period)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = StatsSummary(totalPlays = 0, distinctSongs = 0, totalTimeMs = 0L)
        )

    fun selectPeriod(period: StatPeriod) {
        _period.value = period
    }

    /** Play the ranked list as a queue starting from [index]. */
    fun playFrom(index: Int) {
        viewModelScope.launch {
            val songs = mostPlayed.value.map { it.song }
            if (index in songs.indices) {
                playerRepository.playAll(songs, index)
            }
        }
    }

    fun playAll() {
        viewModelScope.launch {
            val songs = mostPlayed.value.map { it.song }
            if (songs.isNotEmpty()) {
                playerRepository.playAll(songs, 0)
            }
        }
    }

    fun shuffle() {
        viewModelScope.launch {
            val songs = mostPlayed.value.map { it.song }.shuffled()
            if (songs.isNotEmpty()) {
                playerRepository.playAll(songs, 0)
            }
        }
    }
}
