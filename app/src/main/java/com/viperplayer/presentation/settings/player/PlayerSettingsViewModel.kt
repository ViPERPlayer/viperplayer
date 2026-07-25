package com.viperplayer.presentation.settings.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.domain.repository.AudioQuality
import com.viperplayer.domain.repository.HistoryDuration
import com.viperplayer.domain.repository.ReplayGainMode
import com.viperplayer.domain.repository.SEEK_INCREMENT_DEFAULT_SECONDS
import com.viperplayer.domain.repository.SEEK_INCREMENT_MAX_SECONDS
import com.viperplayer.domain.repository.SEEK_INCREMENT_MIN_SECONDS
import com.viperplayer.domain.repository.SettingsRepository
import com.viperplayer.domain.repository.SwipeSensitivity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlayerSettingsUiState(
    val audioQuality: AudioQuality = AudioQuality.HIGH,
    val historyDuration: HistoryDuration = HistoryDuration.FOREVER,
    val skipSilence: Boolean = false,
    val replayGainEnabled: Boolean = true,
    val replayGainPreampDb: Float = 0f,
    val autoLoadMore: Boolean = false,
    val dspBypass: Boolean = false,
    val replayGainAlbumMode: Boolean = false,
    val crossfadeDurationSeconds: Int = 0,
    val replayGainMode: ReplayGainMode = ReplayGainMode.SMART,
    val replayGainUntaggedPreampDb: Float = 0f,
    val replayGainDrcEnabled: Boolean = false,
    val replayGainPostAmpDb: Float = 0f,
    val skipOnError: Boolean = true,
    val seekIncrementSeconds: Int = SEEK_INCREMENT_DEFAULT_SECONDS,
    val stopOnTaskRemoved: Boolean = false,
    val pauseWhenMuted: Boolean = false,
    val resumeOnBluetooth: Boolean = false,
    val preventDuplicateQueue: Boolean = false,
    val persistentQueueEnabled: Boolean = true,
    val keepScreenOnPlayer: Boolean = false,
    val blockScreenshots: Boolean = false,
    val swipeToChangeSong: Boolean = true,
    val swipeSensitivity: SwipeSensitivity = SwipeSensitivity.MEDIUM
)

@HiltViewModel
class PlayerSettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerSettingsUiState())
    val uiState: StateFlow<PlayerSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.audioQuality.collect { quality ->
                _uiState.update { it.copy(audioQuality = quality) }
            }
        }
        viewModelScope.launch {
            settingsRepository.historyDuration.collect { duration ->
                _uiState.update { it.copy(historyDuration = duration) }
            }
        }
        viewModelScope.launch {
            settingsRepository.skipSilence.collect { enabled ->
                _uiState.update { it.copy(skipSilence = enabled) }
            }
        }
        viewModelScope.launch {
            settingsRepository.replayGainEnabled.collect { enabled ->
                _uiState.update { it.copy(replayGainEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            settingsRepository.replayGainPreampDb.collect { preampDb ->
                _uiState.update { it.copy(replayGainPreampDb = preampDb) }
            }
        }
        viewModelScope.launch {
            settingsRepository.autoLoadMore.collect { enabled ->
                _uiState.update { it.copy(autoLoadMore = enabled) }
            }
        }
        viewModelScope.launch {
            settingsRepository.dspBypass.collect { enabled ->
                _uiState.update { it.copy(dspBypass = enabled) }
            }
        }
        viewModelScope.launch {
            settingsRepository.replayGainAlbumMode.collect { enabled ->
                _uiState.update { it.copy(replayGainAlbumMode = enabled) }
            }
        }
        viewModelScope.launch {
            settingsRepository.crossfadeDurationSeconds.collect { seconds ->
                _uiState.update { it.copy(crossfadeDurationSeconds = seconds) }
            }
        }
        viewModelScope.launch {
            settingsRepository.replayGainMode.collect { mode ->
                _uiState.update { it.copy(replayGainMode = mode) }
            }
        }
        viewModelScope.launch {
            settingsRepository.replayGainUntaggedPreampDb.collect { preampDb ->
                _uiState.update { it.copy(replayGainUntaggedPreampDb = preampDb) }
            }
        }
        viewModelScope.launch {
            settingsRepository.replayGainDrcEnabled.collect { enabled ->
                _uiState.update { it.copy(replayGainDrcEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            settingsRepository.replayGainPostAmpDb.collect { postAmpDb ->
                _uiState.update { it.copy(replayGainPostAmpDb = postAmpDb) }
            }
        }
        viewModelScope.launch {
            settingsRepository.skipOnError.collect { enabled ->
                _uiState.update { it.copy(skipOnError = enabled) }
            }
        }
        viewModelScope.launch {
            settingsRepository.seekIncrementSeconds.collect { seconds ->
                _uiState.update { it.copy(seekIncrementSeconds = seconds) }
            }
        }
        viewModelScope.launch {
            settingsRepository.stopOnTaskRemoved.collect { enabled ->
                _uiState.update { it.copy(stopOnTaskRemoved = enabled) }
            }
        }
        viewModelScope.launch {
            settingsRepository.pauseWhenMuted.collect { enabled ->
                _uiState.update { it.copy(pauseWhenMuted = enabled) }
            }
        }
        viewModelScope.launch {
            settingsRepository.resumeOnBluetooth.collect { enabled ->
                _uiState.update { it.copy(resumeOnBluetooth = enabled) }
            }
        }
        viewModelScope.launch {
            settingsRepository.preventDuplicateQueue.collect { enabled ->
                _uiState.update { it.copy(preventDuplicateQueue = enabled) }
            }
        }
        viewModelScope.launch {
            settingsRepository.persistentQueueEnabled.collect { enabled ->
                _uiState.update { it.copy(persistentQueueEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            settingsRepository.keepScreenOnPlayer.collect { enabled ->
                _uiState.update { it.copy(keepScreenOnPlayer = enabled) }
            }
        }
        viewModelScope.launch {
            settingsRepository.blockScreenshots.collect { enabled ->
                _uiState.update { it.copy(blockScreenshots = enabled) }
            }
        }
        viewModelScope.launch {
            settingsRepository.swipeToChangeSong.collect { enabled ->
                _uiState.update { it.copy(swipeToChangeSong = enabled) }
            }
        }
        viewModelScope.launch {
            settingsRepository.swipeSensitivity.collect { sensitivity ->
                _uiState.update { it.copy(swipeSensitivity = sensitivity) }
            }
        }
    }

    fun setAudioQuality(quality: AudioQuality) {
        viewModelScope.launch {
            settingsRepository.setAudioQuality(quality)
        }
    }

    fun setHistoryDuration(duration: HistoryDuration) {
        viewModelScope.launch {
            settingsRepository.setHistoryDuration(duration)
        }
    }

    fun setSkipSilence(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSkipSilence(enabled)
        }
    }

    fun setReplayGainEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setReplayGainEnabled(enabled)
        }
    }

    fun setReplayGainPreampDb(preampDb: Float) {
        viewModelScope.launch {
            val clampedPreamp = preampDb.coerceIn(-12f, 6f)
            settingsRepository.setReplayGainPreampDb(clampedPreamp)
        }
    }

    fun setAutoLoadMore(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoLoadMore(enabled)
        }
    }

    fun setDspBypass(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setDspBypass(enabled)
        }
    }

    fun setReplayGainAlbumMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setReplayGainAlbumMode(enabled)
        }
    }

    fun setCrossfadeDurationSeconds(seconds: Int) {
        viewModelScope.launch {
            settingsRepository.setCrossfadeDurationSeconds(seconds.coerceIn(0, 12))
        }
    }

    fun setReplayGainMode(mode: ReplayGainMode) {
        viewModelScope.launch {
            settingsRepository.setReplayGainMode(mode)
        }
    }

    fun setReplayGainUntaggedPreampDb(preampDb: Float) {
        viewModelScope.launch {
            settingsRepository.setReplayGainUntaggedPreampDb(preampDb.coerceIn(-12f, 6f))
        }
    }

    fun setReplayGainDrcEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setReplayGainDrcEnabled(enabled)
        }
    }

    fun setReplayGainPostAmpDb(postAmpDb: Float) {
        viewModelScope.launch {
            settingsRepository.setReplayGainPostAmpDb(postAmpDb.coerceIn(-12f, 12f))
        }
    }

    fun setSkipOnError(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSkipOnError(enabled)
        }
    }

    fun setSeekIncrementSeconds(seconds: Int) {
        viewModelScope.launch {
            settingsRepository.setSeekIncrementSeconds(
                seconds.coerceIn(SEEK_INCREMENT_MIN_SECONDS, SEEK_INCREMENT_MAX_SECONDS)
            )
        }
    }

    fun setStopOnTaskRemoved(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setStopOnTaskRemoved(enabled)
        }
    }

    fun setPauseWhenMuted(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setPauseWhenMuted(enabled)
        }
    }

    fun setResumeOnBluetooth(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setResumeOnBluetooth(enabled)
        }
    }

    fun setPreventDuplicateQueue(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setPreventDuplicateQueue(enabled)
        }
    }

    fun setPersistentQueueEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setPersistentQueueEnabled(enabled)
        }
    }

    fun setKeepScreenOnPlayer(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setKeepScreenOnPlayer(enabled)
        }
    }

    fun setBlockScreenshots(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setBlockScreenshots(enabled)
        }
    }

    fun setSwipeToChangeSong(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSwipeToChangeSong(enabled)
        }
    }

    fun setSwipeSensitivity(sensitivity: SwipeSensitivity) {
        viewModelScope.launch {
            settingsRepository.setSwipeSensitivity(sensitivity)
        }
    }
}

