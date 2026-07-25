package com.viperplayer.presentation.player

import com.viperplayer.domain.model.LyricsCandidate

/**
 * UI state for the manual lyric-match picker (feature-parity gap B): the editable query the user
 * searches by and the phase of that search. Held in the ViewModel; the composable only renders it.
 */
data class LyricsSearchState(
    val isOpen: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val phase: Phase = Phase.Idle,
) {
    sealed interface Phase {
        /** No search run yet (initial, prefilled query). */
        data object Idle : Phase

        /** A search is in flight. */
        data object Searching : Phase

        /** The search finished: [results] are the candidates (possibly empty = "no matches"). */
        data class Results(val results: List<LyricsCandidate>) : Phase
    }
}
