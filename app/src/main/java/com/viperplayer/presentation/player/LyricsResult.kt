package com.viperplayer.presentation.player

import com.viperplayer.domain.model.Lyrics

/**
 * The lyrics fetch state for the current track, so the renderer can tell "still fetching" apart from
 * "fetched, none available" (both of which used to collapse to a bare `null`).
 */
sealed interface LyricsResult {
    /** A plugin lookup for the current track is still in flight. */
    data object Loading : LyricsResult

    /** The lookup finished: [lyrics] is the result, or null when the track has no lyrics. */
    data class Loaded(val lyrics: Lyrics?) : LyricsResult
}
