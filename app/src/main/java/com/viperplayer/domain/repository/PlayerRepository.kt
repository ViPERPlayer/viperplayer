package com.viperplayer.domain.repository

import com.viperplayer.domain.model.PlaybackInfo
import com.viperplayer.domain.model.PlayerState
import com.viperplayer.domain.model.RepeatMode
import com.viperplayer.domain.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository interface for player operations.
 */
interface PlayerRepository {
    
    /**
     * Flow of playback state (playing/paused/idle, shuffle, repeat, volume, queue info).
     * Does NOT include position, currentSong, or duration - these are separate flows.
     * Emits only when playback state or settings change.
     */
    val playbackState: StateFlow<PlaybackInfo>
    
    /**
     * Flow of currently playing song metadata.
     * Emits only when track changes.
     */
    val currentSong: StateFlow<Song?>
    
    /**
     * Flow of current track duration in milliseconds.
     * Emits only when track changes.
     */
    val duration: StateFlow<Long>
    
    /**
     * Gets the current playback position in milliseconds.
     * Use this for polling-based position updates where the UI controls the polling frequency.
     */
    suspend fun getCurrentPosition(): Long
    
    /**
     * Flow of current queue.
     */
    val queue: Flow<List<Song>>
    
    /**
     * Legacy combined player state for backward compatibility.
     * Combines all separate flows - use individual flows for better performance.
     */
    val playerState: StateFlow<PlayerState>
    
    /**
     * Play a song.
     */
    suspend fun play(song: Song)
    
    /**
     * Play a list of songs starting from a specific index.
     */
    suspend fun playAll(songs: List<Song>, startIndex: Int = 0)
    
    /**
     * Pause playback.
     */
    suspend fun pause()
    
    /**
     * Resume playback.
     */
    suspend fun resume()
    
    /**
     * Toggle play/pause.
     */
    suspend fun togglePlayPause()
    
    /**
     * Stop playback.
     */
    suspend fun stop()
    
    /**
     * Skip to next track.
     */
    suspend fun skipToNext()
    
    /**
     * Skip to previous track.
     */
    suspend fun skipToPrevious()
    
    /**
     * Seek to position.
     */
    suspend fun seekTo(positionMs: Long)
    
    /**
     * Add song to queue.
     */
    suspend fun addToQueue(song: Song)
    
    /**
     * Add song to play next.
     */
    suspend fun playNext(song: Song)
    
    /**
     * Remove song from queue.
     */
    suspend fun removeFromQueue(index: Int)
    
    /**
     * Clear queue.
     */
    suspend fun clearQueue()
    
    /**
     * Set shuffle mode.
     */
    suspend fun setShuffle(enabled: Boolean)
    
    /**
     * Set repeat mode.
     */
    suspend fun setRepeatMode(mode: RepeatMode)
}

