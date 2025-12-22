package com.viperplayer.data.repository

import com.viperplayer.domain.model.PlayerState
import com.viperplayer.domain.model.RepeatMode
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.repository.PlayerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of PlayerRepository.
 * TODO: Integrate with ExoPlayer/Media3 for actual playback.
 */
@Singleton
class PlayerRepositoryImpl @Inject constructor() : PlayerRepository {
    
    private val _playerState = MutableStateFlow(PlayerState())
    override val playerState: Flow<PlayerState> = _playerState.asStateFlow()
    
    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    override val queue: Flow<List<Song>> = _queue.asStateFlow()
    
    private var currentIndex = 0
    
    override suspend fun play(song: Song) {
        _queue.value = listOf(song)
        currentIndex = 0
        updateState(song)
    }
    
    override suspend fun playAll(songs: List<Song>, startIndex: Int) {
        _queue.value = songs
        currentIndex = startIndex.coerceIn(0, songs.lastIndex.coerceAtLeast(0))
        if (songs.isNotEmpty()) {
            updateState(songs[currentIndex])
        }
    }
    
    override suspend fun pause() {
        _playerState.value = _playerState.value.copy(
            state = com.viperplayer.domain.model.PlaybackState.PAUSED
        )
    }
    
    override suspend fun resume() {
        _playerState.value = _playerState.value.copy(
            state = com.viperplayer.domain.model.PlaybackState.PLAYING
        )
    }
    
    override suspend fun togglePlayPause() {
        val current = _playerState.value
        if (current.isPlaying) {
            pause()
        } else if (current.currentSong != null) {
            resume()
        }
    }
    
    override suspend fun stop() {
        _playerState.value = PlayerState()
    }
    
    override suspend fun skipToNext() {
        val queue = _queue.value
        if (queue.isEmpty()) return
        
        currentIndex = (currentIndex + 1) % queue.size
        updateState(queue[currentIndex])
    }
    
    override suspend fun skipToPrevious() {
        val queue = _queue.value
        if (queue.isEmpty()) return
        
        currentIndex = if (currentIndex > 0) currentIndex - 1 else queue.lastIndex
        updateState(queue[currentIndex])
    }
    
    override suspend fun seekTo(positionMs: Long) {
        _playerState.value = _playerState.value.copy(positionMs = positionMs)
    }
    
    override suspend fun addToQueue(song: Song) {
        _queue.value = _queue.value + song
        updateQueueState()
    }
    
    override suspend fun playNext(song: Song) {
        val queue = _queue.value.toMutableList()
        queue.add(currentIndex + 1, song)
        _queue.value = queue
        updateQueueState()
    }
    
    override suspend fun removeFromQueue(index: Int) {
        val queue = _queue.value.toMutableList()
        if (index in queue.indices) {
            queue.removeAt(index)
            _queue.value = queue
            
            if (index < currentIndex) {
                currentIndex--
            } else if (index == currentIndex && queue.isNotEmpty()) {
                currentIndex = currentIndex.coerceAtMost(queue.lastIndex)
                updateState(queue[currentIndex])
            }
            updateQueueState()
        }
    }
    
    override suspend fun clearQueue() {
        _queue.value = emptyList()
        _playerState.value = PlayerState()
        currentIndex = 0
    }
    
    override suspend fun setShuffle(enabled: Boolean) {
        _playerState.value = _playerState.value.copy(shuffleEnabled = enabled)
    }
    
    override suspend fun setRepeatMode(mode: RepeatMode) {
        _playerState.value = _playerState.value.copy(repeatMode = mode)
    }
    
    private fun updateState(song: Song) {
        _playerState.value = _playerState.value.copy(
            state = com.viperplayer.domain.model.PlaybackState.PLAYING,
            currentSong = song,
            positionMs = 0,
            durationMs = song.durationMs,
            queueSize = _queue.value.size,
            queuePosition = currentIndex
        )
    }
    
    private fun updateQueueState() {
        _playerState.value = _playerState.value.copy(
            queueSize = _queue.value.size,
            queuePosition = currentIndex
        )
    }
}

