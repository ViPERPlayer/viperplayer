package com.viperplayer.presentation.settings.lyrics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.domain.model.LyricsAlignment
import com.viperplayer.domain.model.LyricsBehavior
import com.viperplayer.domain.model.LyricsFontSize
import com.viperplayer.domain.model.LyricsFontWeight
import com.viperplayer.domain.model.LyricsHighlightColor
import com.viperplayer.domain.model.LyricsSettings
import com.viperplayer.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the Lyrics settings screen. Mirrors the persisted [LyricsSettings] into UI state and forwards
 * edits to the [SettingsRepository]. All persistence/logic lives here; the screen only renders + emits.
 */
@HiltViewModel
class LyricsSettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LyricsSettings())
    val uiState: StateFlow<LyricsSettings> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.lyricsSettings.collect { settings ->
                _uiState.value = settings
            }
        }
    }

    fun setFontSize(size: LyricsFontSize) = launchEdit { settingsRepository.setLyricsFontSize(size) }

    fun setAlignment(alignment: LyricsAlignment) = launchEdit { settingsRepository.setLyricsAlignment(alignment) }

    fun setFontWeight(weight: LyricsFontWeight) = launchEdit { settingsRepository.setLyricsFontWeight(weight) }

    fun setHighlightColor(color: LyricsHighlightColor) = launchEdit { settingsRepository.setLyricsHighlightColor(color) }

    fun setActiveLineScale(scale: Float) = launchEdit {
        settingsRepository.setLyricsActiveLineScale(LyricsBehavior.clampActiveLineScale(scale))
    }

    fun setAutoScroll(enabled: Boolean) = launchEdit { settingsRepository.setLyricsAutoScroll(enabled) }

    fun setTapToSeek(enabled: Boolean) = launchEdit { settingsRepository.setLyricsTapToSeek(enabled) }

    fun setDimInactiveLines(enabled: Boolean) = launchEdit { settingsRepository.setLyricsDimInactiveLines(enabled) }

    fun setShowTranslationByDefault(enabled: Boolean) =
        launchEdit { settingsRepository.setLyricsShowTranslationByDefault(enabled) }

    fun setShowRomanizationByDefault(enabled: Boolean) =
        launchEdit { settingsRepository.setLyricsShowRomanizationByDefault(enabled) }

    private inline fun launchEdit(crossinline block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
