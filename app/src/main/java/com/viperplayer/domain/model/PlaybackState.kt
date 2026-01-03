package com.viperplayer.domain.model

/**
 * Playback state.
 */
enum class PlaybackState {
    IDLE, BUFFERING, PLAYING, PAUSED, STOPPED, ERROR
}

/**
 * Repeat mode.
 */
enum class RepeatMode {
    OFF, ONE, ALL
}

/**
 * Playback information (state, settings, queue info).
 * Does NOT include position, currentSong, or duration - these are separate.
 * Use this for UI that only needs playback state and settings.
 */
data class PlaybackInfo(
    val state: PlaybackState = PlaybackState.IDLE,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val volume: Float = 1.0f,
    val queueSize: Int = 0,
    val queuePosition: Int = 0
) {
    val isPlaying: Boolean get() = state == PlaybackState.BUFFERING || state == PlaybackState.PLAYING
    val isPaused: Boolean get() = state == PlaybackState.PAUSED
}

/**
 * Current player state (legacy - combines all fields).
 * For better performance, use separate flows: playbackState, currentSong, position, duration.
 */
data class PlayerState(
    val state: PlaybackState = PlaybackState.IDLE,
    val currentSong: Song? = null,
    val positionMs: Long = 0,
    val durationMs: Long? = 0,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val volume: Float = 1.0f,
    val queueSize: Int = 0,
    val queuePosition: Int = 0
) {
    val isPlaying: Boolean get() = state == PlaybackState.PLAYING
    val isPaused: Boolean get() = state == PlaybackState.PAUSED
    val hasContent: Boolean get() = currentSong != null
}

