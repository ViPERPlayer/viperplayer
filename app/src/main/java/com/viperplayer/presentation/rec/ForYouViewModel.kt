package com.viperplayer.presentation.rec

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.domain.model.PlaybackContext
import com.viperplayer.domain.model.RecEmptyReason
import com.viperplayer.domain.model.RecResult
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.repository.PlayerRepository
import com.viperplayer.domain.repository.RecommendationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for the "For You" mix: builds the taste-based recommendations via
 * [RecommendationRepository.forYouFromLibrary] and maps the [RecResult] into a [RecUiState] (reusing the
 * shared [applyResult] fold). All data + playback logic lives here.
 */
@HiltViewModel
class ForYouViewModel @Inject constructor(
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
    }

    fun retry() = load()

    private fun load() {
        _uiState.update { it.copy(isLoading = true, emptyReason = null) }
        viewModelScope.launch {
            val readiness = try {
                recommendationRepository.readiness.first()
            } catch (e: Exception) {
                Timber.w(e, "ForYou: readiness read failed")
                null
            }
            val result = try {
                recommendationRepository.forYouFromLibrary(RESULT_LIMIT)
            } catch (e: Exception) {
                Timber.w(e, "ForYou: forYouFromLibrary failed")
                RecResult.Empty(RecEmptyReason.ERROR)
            }
            _uiState.update { it.applyResult(result, readiness) }
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

    /** Thumbs-up: a strong positive signal (likes the song via the repository's feedback path). */
    fun thumbsUp(song: Song) = launchSafe { recommendationRepository.sendFeedback(song.id, positive = true) }

    /** Thumbs-down: a strong negative taste nudge; dims the row immediately (idempotent). */
    fun thumbsDown(song: Song) {
        _uiState.update { it.copy(dislikedIds = it.dislikedIds + song.id) }
        launchSafe { recommendationRepository.sendFeedback(song.id, positive = false) }
    }

    private fun play(songs: List<Song>, index: Int) {
        if (songs.isEmpty()) return
        launchSafe { playerRepository.playAll(songs, index, PlaybackContext.Suggestions) }
    }

    private inline fun launchSafe(crossinline block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                Timber.w(e, "ForYou playback op failed")
            }
        }
    }

    private companion object {
        const val RESULT_LIMIT = 40
    }
}
