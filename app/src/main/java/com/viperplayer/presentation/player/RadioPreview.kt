package com.viperplayer.presentation.player

import com.viperplayer.domain.model.Song

/**
 * The state of a "Song radio" preview. The radio is built from a seed track and SHOWN to the user as
 * a playlist before anything plays (issue #7) — playback only starts when the user picks a track from
 * the preview. Kept as a small sealed type (like [LyricsResult]) so the sheet can distinguish "still
 * building" from "ready" without a separate loading flag.
 */
sealed interface RadioPreview {
    /** The seed track whose radio this preview represents. */
    val seed: Song

    /** The related-songs lookup for [seed] is still in flight. */
    data class Loading(override val seed: Song) : RadioPreview

    /**
     * The radio is built: [songs] is the seed followed by the related tracks (seed always first, no
     * duplicates of the seed). Playing index i starts playback from `songs[i]`.
     */
    data class Ready(override val seed: Song, val songs: List<Song>) : RadioPreview
}
