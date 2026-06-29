package com.viperplayer.presentation.main

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.materialkolor.ktx.themeColorOrNull
import com.viperplayer.domain.repository.DynamicThemeMode
import com.viperplayer.domain.repository.PlayerRepository
import com.viperplayer.domain.repository.SettingsRepository
import com.viperplayer.domain.repository.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ViperPlayerAppUiState(
    val themeColor: Color? = null,
    val hasCurrentSong: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicThemeMode: DynamicThemeMode = DynamicThemeMode.DYNAMIC,
    val pureBlack: Boolean = false
)

@HiltViewModel
class ViperPlayerAppViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val playerRepository: PlayerRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ViperPlayerAppUiState())
    val uiState = _uiState.asStateFlow()

    init {
        observeThemeColor()
        observeThemeSettings()
    }

    private fun observeThemeColor() {
        viewModelScope.launch {
            // collectLatest: a new song cancels an in-flight decode. runCatching: a decode/OOM
            // failure must not cancel the whole collector (which would freeze theme updates).
            playerRepository.currentSong.collectLatest { song ->
                val themeColor = runCatching {
                    song?.artworkUrl?.let { artworkUrl ->
                        withContext(Dispatchers.IO) {
                            val result = context.imageLoader.execute(
                                ImageRequest.Builder(context)
                                    .data(artworkUrl)
                                    // Downscale before extracting the seed — decoding the full-res
                                    // bitmap is the slow part; 128px is ample to pick a dominant color.
                                    .size(128)
                                    .allowHardware(false)
                                    .build()
                            )
                            result.image?.toBitmap()?.asImageBitmap()?.themeColorOrNull()
                        }
                    }
                }.getOrNull()

                _uiState.update {
                    it.copy(
                        themeColor = themeColor,
                        hasCurrentSong = song != null
                    )
                }
            }
        }
    }

    private fun observeThemeSettings() {
        viewModelScope.launch {
            settingsRepository.themeMode.collect { themeMode ->
                _uiState.update {
                    it.copy(themeMode = themeMode)
                }
            }
        }
        viewModelScope.launch {
            settingsRepository.dynamicThemeMode.collect { mode ->
                _uiState.update { it.copy(dynamicThemeMode = mode) }
            }
        }
        viewModelScope.launch {
            settingsRepository.pureBlack.collect { enabled ->
                _uiState.update { it.copy(pureBlack = enabled) }
            }
        }
    }
}