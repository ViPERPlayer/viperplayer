package com.viperplayer.presentation.rec

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.domain.model.Daylist
import com.viperplayer.domain.model.PlaybackContext
import com.viperplayer.domain.model.RecEmptyReason
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.repository.DaylistRepository
import com.viperplayer.domain.repository.PlayerRepository
import com.viperplayer.domain.repository.RecommendationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
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
 *
 * It re-derives (without a loading flicker) when the recommender readiness changes on/off, and when the
 * screen reports the wall clock ticked ([onTimeChanged]) — so an open screen rolls over as the
 * time-of-day bucket changes rather than showing a stale slot.
 */
@HiltViewModel
class DaylistViewModel @Inject constructor(
    private val daylistRepository: DaylistRepository,
    private val recommendationRepository: RecommendationRepository,
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
        // Reload when the recommender readiness changes in a way that can turn the daylist on/off
        // (enablement / model / index size), collapsing on those fields to avoid re-running on unrelated
        // DataStore emissions. drop(1) skips the current value that init's load() already covers.
        viewModelScope.launch {
            recommendationRepository.readiness
                .distinctUntilChanged { old, new ->
                    old.recommendationsEnabled == new.recommendationsEnabled &&
                        old.modelReady == new.modelReady &&
                        old.indexedCount == new.indexedCount
                }
                .drop(1)
                .collect { refresh() }
        }
    }

    fun retry() = load()

    /** Re-derive as the wall clock ticks (the time-of-day bucket may have rolled over). Called by the screen. */
    fun onTimeChanged() {
        viewModelScope.launch { refresh() }
    }

    private fun load() {
        _uiState.update { it.copy(isLoading = true, emptyReason = null) }
        viewModelScope.launch { applyDaylist(loadCurrent()) }
    }

    /** A background re-derive that swaps content in place without flashing the loading state. */
    private suspend fun refresh() = applyDaylist(loadCurrent())

    private suspend fun loadCurrent(): Daylist? = try {
        daylistRepository.currentDaylist()
    } catch (e: Exception) {
        Timber.w(e, "Daylist: currentDaylist failed")
        null
    }

    private suspend fun applyDaylist(daylist: Daylist?) {
        if (daylist == null) {
            // Distinguish "recommendations off" from "no results" so the correct empty message renders
            // and the dead Retry button is suppressed when recommendations are simply disabled.
            val enabled = try {
                recommendationRepository.readiness.first().recommendationsEnabled
            } catch (e: Exception) {
                true
            }
            val reason = if (enabled) RecEmptyReason.NO_RESULTS else RecEmptyReason.RECOMMENDATIONS_DISABLED
            _uiState.update { it.copy(isLoading = false, songs = emptyList(), emptyReason = reason) }
        } else {
            _uiState.update {
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
