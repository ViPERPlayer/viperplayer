package com.viperplayer.domain.model

/**
 * Represents the context from which playback was initiated.
 */
sealed class PlaybackContext {
    data object Search : PlaybackContext()
    data class Album(val mediaId: MediaId, val name: String) : PlaybackContext()
    data class Artist(val mediaId: MediaId, val name: String) : PlaybackContext()
    data class Playlist(val mediaId: MediaId, val name: String) : PlaybackContext()
}
