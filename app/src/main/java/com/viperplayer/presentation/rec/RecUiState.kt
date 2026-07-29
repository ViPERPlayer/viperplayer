package com.viperplayer.presentation.rec

import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.RecEmptyReason
import com.viperplayer.domain.model.RecReason
import com.viperplayer.domain.model.Song

/**
 * UI state shared by the two recommendation surfaces ("More like this" / "For You"). Both render the
 * same shape — a header, a ranked song list with Play/Shuffle, and the loading/empty/fallback states —
 * so they share this state and the [RecommendationsScreen] that renders it.
 *
 * @param title header title (e.g. "Similar to <song>" or "For You").
 * @param subtitle optional secondary header line.
 * @param artworkUrl optional header artwork (the seed's cover for "More like this").
 * @param songs the ranked recommendations, best-first.
 * @param isLoading true while the first result is being computed.
 * @param usingFallback true when [songs] came from the plugin's related-songs feed rather than the
 *   local kNN (drives the subtle "using related songs" note).
 * @param emptyReason non-null when there are no songs — selects the explanatory empty state.
 * @param indexingProcessed / [indexingTotal] the "Analyzing your library… N/M" progress, when indexing.
 * @param reasons per-song "Sounds like X" reasons (P4), rendered as a caption where present.
 * @param dislikedIds ids the user just thumbed-down this session — dimmed/removed immediately so the
 *   feedback feels instant without a full refetch. Idempotent (a set).
 */
data class RecUiState(
    val title: String = "",
    val subtitle: String? = null,
    val artworkUrl: String? = null,
    val songs: List<Song> = emptyList(),
    val isLoading: Boolean = true,
    val usingFallback: Boolean = false,
    val emptyReason: RecEmptyReason? = null,
    val indexingProcessed: Int? = null,
    val indexingTotal: Int? = null,
    val reasons: Map<MediaId, RecReason> = emptyMap(),
    val dislikedIds: Set<MediaId> = emptySet(),
) {
    val hasSongs: Boolean get() = songs.isNotEmpty()
    val isIndexing: Boolean get() = indexingProcessed != null && indexingTotal != null
}
