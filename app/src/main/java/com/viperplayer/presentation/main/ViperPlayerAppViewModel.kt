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
import com.viperplayer.domain.model.PlayerState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ViperPlayerAppUiState(
    val color: Color = Color.Unspecified
)

@HiltViewModel
class ViperPlayerAppViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ViperPlayerAppUiState())
    val uiState = _uiState.asStateFlow()

    private val _dynamicThemeColor = MutableStateFlow<Color?>(null)
    val dynamicThemeColor = _dynamicThemeColor.asStateFlow()

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState = _playerState.asStateFlow()

    init {
        observeThemeColor()
    }

    private fun observeThemeColor() {
        viewModelScope.launch {
            playerState.collect { playerState ->
                val themeColor = playerState.currentSong?.artworkUrl?.let { artworkUrl ->
                    val result = context.imageLoader.execute(
                        ImageRequest.Builder(context)
                            .data(artworkUrl)
                            .allowHardware(false)
                            .build()
                    )
                    result.image?.toBitmap()?.asImageBitmap()?.themeColorOrNull()
                }

                _dynamicThemeColor.update { themeColor }
            }
        }
    }

    fun togglePlayPause() {
        TODO("Not yet implemented")
    }
}