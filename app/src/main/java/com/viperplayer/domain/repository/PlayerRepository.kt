package com.viperplayer.domain.repository

import com.viperplayer.domain.model.PlayerState
import com.viperplayer.domain.model.RepeatMode
import com.viperplayer.domain.model.Song
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for player operations.
 */
interface PlayerRepository {
    
    /**
     * Flow of current player state.
     */
    val playerState: Flow<PlayerState>
    
    /**
     * Flow of current queue.
     */
    val queue: Flow<List<Song>>
    
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

