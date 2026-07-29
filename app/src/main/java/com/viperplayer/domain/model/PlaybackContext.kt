package com.viperplayer.domain.model

/**
 * Represents the context from which playback was initiated.
 */
sealed class PlaybackContext {
    /** Playback started from search results; [query] is what the user searched for. */
    data class Search(val query: String = "") : PlaybackContext()
    data class Album(val mediaId: MediaId, val name: String) : PlaybackContext()
    data class Artist(val mediaId: MediaId, val name: String) : PlaybackContext()
    data class Playlist(val mediaId: MediaId, val name: String) : PlaybackContext()

    /** Playback started from a local library genre; [genreId] is its Room row id, [name] its label. */
    data class Genre(val genreId: Long, val name: String) : PlaybackContext()

    /**
     * Auto-loaded related songs are playing — the original queue ran out and autoplay took over
     * (also used for home quick picks). Derived while the current item carries the suggestion
     * marker, so skipping back into the original queue restores the original context.
     */
    data object Suggestions : PlaybackContext()

    /** Playback started from the on-device time-of-day daylist. */
    data object Daylist : PlaybackContext()
}
