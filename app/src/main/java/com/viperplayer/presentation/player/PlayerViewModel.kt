package com.viperplayer.presentation.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.domain.model.PlaybackInfo
import com.viperplayer.domain.model.RepeatMode
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.repository.MediaLibraryRepository
import com.viperplayer.domain.repository.PlayerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerRepository: PlayerRepository,
    private val mediaLibraryRepository: MediaLibraryRepository
) : ViewModel() {
    // Separate flows for optimal performance
    val playbackState: StateFlow<PlaybackInfo> = playerRepository.playbackState

    val isPlaying: StateFlow<Boolean> =
        playbackState
            .map { it.isPlaying }
            .distinctUntilChanged()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = false
            )

    val currentSong: StateFlow<Song?> = playerRepository.currentSong
    val duration: StateFlow<Long> = playerRepository.duration
    
    /**
     * Gets the current playback position in milliseconds.
     * Use this for polling-based position updates where the UI controls the polling frequency.
     */
    suspend fun getCurrentPosition(): Long = playerRepository.getCurrentPosition()
    val queue: StateFlow<List<Song>> = playerRepository.queue
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    // Liked status - loaded from database and synced with current song
    val isLiked: StateFlow<Boolean> = currentSong
        .flatMapLatest { song ->
            if (song != null) {
                mediaLibraryRepository.getSong(song.id)
                    .map { it?.isLiked ?: false }
                    .distinctUntilChanged()
            } else {
                kotlinx.coroutines.flow.flowOf(false)
            }
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )
    
    fun togglePlayPause() {
        viewModelScope.launch {
            playerRepository.togglePlayPause()
        }
    }
    
    fun skipToNext() {
        viewModelScope.launch {
            playerRepository.skipToNext()
        }
    }
    
    fun skipToPrevious() {
        viewModelScope.launch {
            playerRepository.skipToPrevious()
        }
    }
    
    fun seekTo(positionMs: Long) {
        viewModelScope.launch {
            playerRepository.seekTo(positionMs)
        }
    }
    
    fun toggleShuffle() {
        viewModelScope.launch {
            playerRepository.setShuffle(!playbackState.value.shuffleEnabled)
        }
    }
    
    fun cycleRepeatMode() {
        viewModelScope.launch {
            val nextMode = when (playbackState.value.repeatMode) {
                RepeatMode.OFF -> RepeatMode.ALL
                RepeatMode.ALL -> RepeatMode.ONE
                RepeatMode.ONE -> RepeatMode.OFF
            }
            playerRepository.setRepeatMode(nextMode)
        }
    }
    
    fun addToQueue(song: Song) {
        viewModelScope.launch {
            playerRepository.addToQueue(song)
        }
    }

    fun toggleLike() {
        viewModelScope.launch {
            val song = currentSong.value
            if (song != null) {
                // Ensure song is saved to database first
                mediaLibraryRepository.saveSong(song)
                
                // Then update liked status
                val currentLiked = isLiked.value
                mediaLibraryRepository.setSongLiked(song.id, !currentLiked)
            }
        }
    }
}

