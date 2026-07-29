package com.viperplayer.presentation.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.domain.repository.DaylistRepository
import com.viperplayer.domain.repository.RecommendationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Drives the pinned Home **Daylist card**. It is a thin, pinned surface (never part of the shuffled Home
 * sections): it asks the on-device [DaylistRepository] for the current slot's daylist and exposes an
 * [Immutable] [DaylistCardState] the card renders. Re-derives whenever the recommender readiness changes
 * in a way that could turn a daylist on/off (enablement / model / index size), and when Home reports the
 * wall clock ticked (so the card rolls over as the time-of-day bucket changes).
 *
 * All data logic lives here (MVVM). The card itself only renders the state and forwards the "open" tap.
 */
@HiltViewModel
class DaylistCardViewModel @Inject constructor(
    private val daylistRepository: DaylistRepository,
    private val recommendationRepository: RecommendationRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<DaylistCardState>(DaylistCardState.Hidden)
    val state: StateFlow<DaylistCardState> = _state.asStateFlow()

    init {
        // Re-derive the card whenever the recommender readiness changes materially (mirrors the
        // Suggestions surface): a daylist can only exist when recommendations are on + the library is
        // indexed, so collapse on those fields to avoid re-running on unrelated DataStore emissions.
        viewModelScope.launch {
            recommendationRepository.readiness
                .distinctUntilChanged { old, new ->
                    old.recommendationsEnabled == new.recommendationsEnabled &&
                        old.modelReady == new.modelReady &&
                        old.indexedCount == new.indexedCount
                }
                .collect { refresh() }
        }
    }

    /** Re-derive the card as the wall clock ticks (the time-of-day bucket may have rolled over). */
    fun onTimeChanged() {
        viewModelScope.launch { refresh() }
    }

    private suspend fun refresh() {
        val daylist = try {
            daylistRepository.currentDaylist()
        } catch (e: Exception) {
            Timber.w(e, "DaylistCard: currentDaylist failed")
            null
        }
        _state.value = if (daylist == null) {
            DaylistCardState.Hidden
        } else {
            DaylistCardState.Ready(
                title = daylist.title,
                description = daylist.description,
                // Preview the first few songs that actually have artwork (skip art-less rows).
                artworkUrls = daylist.songs.mapNotNull { it.artworkUrl }.take(ARTWORK_COUNT),
            )
        }
    }

    private companion object {
        /** How many artworks to preview on the card. */
        const val ARTWORK_COUNT = 4
    }
}

/**
 * The pinned Home Daylist card state. [Immutable] so Compose can skip recomposition when unchanged.
 * String-free where it matters — the generated [title]/[description] ARE English content (see the
 * generator), but the FIXED card labels come from resources at render time.
 */
@Immutable
sealed interface DaylistCardState {

    /** No daylist to show right now (recommendations off, cold taste, or unindexed) — don't nag. */
    data object Hidden : DaylistCardState

    /** A daylist is available: render the prominent card. */
    data class Ready(
        val title: String,
        val description: String,
        val artworkUrls: List<String>,
    ) : DaylistCardState
}
