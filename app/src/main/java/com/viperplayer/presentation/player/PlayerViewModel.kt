package com.viperplayer.presentation.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.domain.model.PlayerState
import com.viperplayer.domain.model.RepeatMode
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.usecase.player.AddToQueueUseCase
import com.viperplayer.domain.usecase.player.GetPlayerStateUseCase
import com.viperplayer.domain.usecase.player.GetQueueUseCase
import com.viperplayer.domain.usecase.player.SeekToUseCase
import com.viperplayer.domain.usecase.player.SetRepeatModeUseCase
import com.viperplayer.domain.usecase.player.SetShuffleUseCase
import com.viperplayer.domain.usecase.player.SkipToNextUseCase
import com.viperplayer.domain.usecase.player.SkipToPreviousUseCase
import com.viperplayer.domain.usecase.player.TogglePlayPauseUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for player controls.
 * Can be shared across screens that need player functionality.
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    getPlayerStateUseCase: GetPlayerStateUseCase,
    getQueueUseCase: GetQueueUseCase,
    private val togglePlayPauseUseCase: TogglePlayPauseUseCase,
    private val skipToNextUseCase: SkipToNextUseCase,
    private val skipToPreviousUseCase: SkipToPreviousUseCase,
    private val seekToUseCase: SeekToUseCase,
    private val setShuffleUseCase: SetShuffleUseCase,
    private val setRepeatModeUseCase: SetRepeatModeUseCase,
    private val addToQueueUseCase: AddToQueueUseCase
) : ViewModel() {
    
    val playerState: StateFlow<PlayerState> = getPlayerStateUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = PlayerState()
        )
    
    val queue: StateFlow<List<Song>> = getQueueUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    fun togglePlayPause() {
        viewModelScope.launch {
            togglePlayPauseUseCase()
        }
    }
    
    fun skipToNext() {
        viewModelScope.launch {
            skipToNextUseCase()
        }
    }
    
    fun skipToPrevious() {
        viewModelScope.launch {
            skipToPreviousUseCase()
        }
    }
    
    fun seekTo(positionMs: Long) {
        viewModelScope.launch {
            seekToUseCase(positionMs)
        }
    }
    
    fun toggleShuffle() {
        viewModelScope.launch {
            setShuffleUseCase(!playerState.value.shuffleEnabled)
        }
    }
    
    fun cycleRepeatMode() {
        viewModelScope.launch {
            val nextMode = when (playerState.value.repeatMode) {
                RepeatMode.OFF -> RepeatMode.ALL
                RepeatMode.ALL -> RepeatMode.ONE
                RepeatMode.ONE -> RepeatMode.OFF
            }
            setRepeatModeUseCase(nextMode)
        }
    }
    
    fun addToQueue(song: Song) {
        viewModelScope.launch {
            addToQueueUseCase(song)
        }
    }
}

