package com.viperplayer.presentation.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.data.rec.ClapModelRepository
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.PlaybackContext
import com.viperplayer.domain.model.RecEmptyReason
import com.viperplayer.domain.model.RecReadiness
import com.viperplayer.domain.model.RecReason
import com.viperplayer.domain.model.RecResult
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.repository.PlayerRepository
import com.viperplayer.domain.repository.RecommendationRepository
import com.viperplayer.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * The host-generated **"Suggested for you"** surface, shared by Home (a pinned carousel above the plugin
 * sections) and the empty Search state (a compact list). It is driven entirely by the on-device
 * recommender — [RecommendationRepository.forYouFromLibrary] + its [RecommendationRepository.readiness]
 * — so it needs **no login and no network** (the CLAP kNN over the local taste model).
 *
 * The [SuggestionsUiState] is string-free and [Immutable] so both screens map it to localized copy and
 * Compose can skip recomposition when it is unchanged. All data + playback logic lives here, per MVVM.
 */
@HiltViewModel
class SuggestionsViewModel @Inject constructor(
    private val recommendationRepository: RecommendationRepository,
    private val settingsRepository: SettingsRepository,
    private val clapModelRepository: ClapModelRepository,
    private val playerRepository: PlayerRepository,
) : ViewModel() {

    // The user dismissed the enable card this session; kept out of the readiness fold so it survives
    // readiness re-emissions but not a process restart (a session-only "hide").
    private val _dismissed = MutableStateFlow(false)

    private val _uiState = MutableStateFlow<SuggestionsUiState>(SuggestionsUiState.Hidden)
    val uiState: StateFlow<SuggestionsUiState> = _uiState.asStateFlow()

    val currentSong: StateFlow<Song?> = playerRepository.currentSong
    val isPlaying: StateFlow<Boolean> = playerRepository.playbackState
        .map { it.isPlaying }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        // Re-derive the surface whenever the recommender readiness changes in a way that matters
        // (enablement, model readiness, index size, or indexing progress) or the card is dismissed.
        // Collapsing on those fields avoids re-running the kNN on every unrelated DataStore emission.
        viewModelScope.launch {
            recommendationRepository.readiness
                .distinctUntilChanged { old, new ->
                    old.recommendationsEnabled == new.recommendationsEnabled &&
                        old.modelReady == new.modelReady &&
                        old.indexedCount == new.indexedCount &&
                        old.indexingProcessed == new.indexingProcessed &&
                        old.indexingTotal == new.indexingTotal
                }
                .collect { readiness -> refresh(readiness) }
        }
        viewModelScope.launch {
            _dismissed.collect { dismissed ->
                if (dismissed) {
                    // Hide immediately when dismissed; only the enable card is dismissible so this is safe.
                    _uiState.value = SuggestionsUiState.Hidden
                }
            }
        }
    }

    /** Recompute the surface from the latest [readiness] (and, when enabled, a fresh For-You result). */
    private suspend fun refresh(readiness: RecReadiness) {
        if (_dismissed.value && !readiness.recommendationsEnabled) {
            // Stay hidden while off + dismissed; a later enable clears dismissal implicitly (see below).
            _uiState.value = SuggestionsUiState.Hidden
            return
        }
        if (!readiness.recommendationsEnabled) {
            _uiState.value = SuggestionsUiState.Disabled
            return
        }
        val result = try {
            recommendationRepository.forYouFromLibrary(RESULT_LIMIT)
        } catch (e: Exception) {
            Timber.w(e, "Suggestions: forYouFromLibrary failed")
            RecResult.Empty(RecEmptyReason.ERROR)
        }
        _uiState.value = mapToState(result, readiness)
    }

    /**
     * Inline enable (mirrors ContentSettings): persist the opt-in and start the Wi-Fi-gated model
     * download. The readiness flow then drives the surface to its Personalizing / Ready state. Clears
     * any session dismissal so the newly-enabled surface actually appears.
     */
    fun enable() {
        _dismissed.value = false
        viewModelScope.launch {
            settingsRepository.setRecommendationsEnabled(true)
            clapModelRepository.ensureDownloaded()
        }
    }

    /** Dismiss the enable card for this session (until the app is relaunched). */
    fun dismiss() {
        _dismissed.value = true
    }

    /**
     * Play a suggested [song] under the Suggestions playback context, seeding the queue with the whole
     * ready suggestion list (so next/prev traverse the shelf) when it is available.
     */
    fun play(song: Song) {
        viewModelScope.launch {
            try {
                val songs = (_uiState.value as? SuggestionsUiState.Ready)?.songs.orEmpty()
                val index = songs.indexOfFirst { it.id == song.id }
                if (index >= 0) {
                    playerRepository.playAll(songs, index, PlaybackContext.Suggestions)
                } else {
                    playerRepository.play(song, PlaybackContext.Suggestions)
                }
            } catch (e: Exception) {
                Timber.w(e, "Suggestions: play failed")
            }
        }
    }

    private companion object {
        /** Enough to fill a multi-browse carousel with peeking neighbours, kept small for perf. */
        const val RESULT_LIMIT = 12
    }
}

/**
 * Maps a [RecResult] (+ [readiness]) to the string-free [SuggestionsUiState] the surface renders.
 * Pure so it is directly unit-testable.
 *
 * - Songs present → [SuggestionsUiState.Ready].
 * - Empty/NOT_INDEXED_YET → [SuggestionsUiState.Personalizing] with progress from [readiness].
 * - Empty/RECOMMENDATIONS_DISABLED → [SuggestionsUiState.Disabled] (the setting flipped under us).
 * - Empty/NO_RESULTS or ERROR → [SuggestionsUiState.Hidden] (empty library / no taste yet — don't nag).
 */
internal fun mapToState(result: RecResult, readiness: RecReadiness): SuggestionsUiState =
    when (result) {
        is RecResult.Local -> if (result.songs.isEmpty()) {
            SuggestionsUiState.Hidden
        } else {
            SuggestionsUiState.Ready(result.songs, result.reasons)
        }

        is RecResult.Fallback -> if (result.songs.isEmpty()) {
            SuggestionsUiState.Hidden
        } else {
            SuggestionsUiState.Ready(result.songs, result.reasons)
        }

        is RecResult.Empty -> when (result.reason) {
            RecEmptyReason.RECOMMENDATIONS_DISABLED -> SuggestionsUiState.Disabled
            RecEmptyReason.NOT_INDEXED_YET ->
                SuggestionsUiState.Personalizing(readiness.indexingProcessed, readiness.indexingTotal)
            RecEmptyReason.NO_RESULTS, RecEmptyReason.ERROR -> SuggestionsUiState.Hidden
        }
    }

/**
 * The "Suggested for you" surface state. [Immutable] so Compose treats it as stable and can skip
 * recomposing the surface when it is unchanged.
 */
@Immutable
sealed interface SuggestionsUiState {
    /** Nothing to show (recommendations ready but no taste/library yet, an error, or dismissed). */
    data object Hidden : SuggestionsUiState

    /** Recommendations are off: show the gentle, dismissible enable card. */
    data object Disabled : SuggestionsUiState

    /**
     * On, but the model is still downloading or the library is still indexing. [processed]/[total] are
     * the live indexing counts when available (else null → a generic "getting ready" hint).
     */
    data class Personalizing(val processed: Int?, val total: Int?) : SuggestionsUiState {
        val hasProgress: Boolean get() = processed != null && total != null
    }

    /** On, ready, and there are picks: render the carousel/list. [reasons] carry "Sounds like X". */
    data class Ready(
        val songs: List<Song>,
        val reasons: Map<MediaId, RecReason>,
    ) : SuggestionsUiState
}
