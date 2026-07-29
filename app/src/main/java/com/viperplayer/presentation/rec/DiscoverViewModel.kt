package com.viperplayer.presentation.rec

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.domain.model.PlaybackContext
import com.viperplayer.domain.model.RecEmptyReason
import com.viperplayer.domain.model.RecResult
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.repository.DiscoveryRepository
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
 * ViewModel for the **Discover** screen: server-backed recommendations of songs the user does NOT own,
 * via [DiscoveryRepository.discover], mapped into a [RecUiState] with the shared [applyResult] fold.
 * All data + playback logic lives here — the screen only renders + forwards events.
 *
 * On each load it also fires [DiscoveryRepository.contributeTasteIfEnabled] once (fire-and-forget,
 * no-op unless the user opted in and the taste is warm). Opening Discover is a natural, non-spammy
 * trigger: the user is actively engaging with discovery, so refreshing the anonymized contribution
 * here keeps the global pool current without a background job.
 */
@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val discoveryRepository: DiscoveryRepository,
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
            // Non-spammy contribution trigger: fire once per Discover open (opt-in + warm-taste gated
            // inside the repository). Fire-and-forget — never blocks or fails the feed load.
            launch {
                try {
                    discoveryRepository.contributeTasteIfEnabled()
                } catch (e: Exception) {
                    Timber.w(e, "Discover: contribute failed")
                }
            }
            val readiness = try {
                recommendationRepository.readiness.first()
            } catch (e: Exception) {
                Timber.w(e, "Discover: readiness read failed")
                null
            }
            val result = try {
                discoveryRepository.discover(RESULT_LIMIT)
            } catch (e: Exception) {
                Timber.w(e, "Discover: discover failed")
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

    private fun play(songs: List<Song>, index: Int) {
        if (songs.isEmpty()) return
        launchSafe { playerRepository.playAll(songs, index, PlaybackContext.Suggestions) }
    }

    private inline fun launchSafe(crossinline block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                Timber.w(e, "Discover playback op failed")
            }
        }
    }

    private companion object {
        const val RESULT_LIMIT = 40
    }
}
