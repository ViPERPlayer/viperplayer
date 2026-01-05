package com.viperplayer.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.BrowseCategory
import com.viperplayer.domain.model.Plugin
import com.viperplayer.domain.repository.PlayerRepository
import com.viperplayer.domain.repository.PluginRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

/**
 * UI State for Home screen.
 */
data class HomeUiState(
    val isLoading: Boolean = true,
    val categories: List<BrowseCategory> = emptyList(),
    val recentAlbums: List<Album> = emptyList(),
    val connectedPlugins: List<Plugin> = emptyList(),
    val error: String? = null,
    val greetingType: GreetingType = GreetingType.MORNING,
    val userName: String? = null
)

/**
 * ViewModel for Home screen.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val pluginRepository: PluginRepository,
    private val playerRepository: PlayerRepository,
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val playerState = playerRepository.playerState
    
    init {
        observeConnectedPlugins()
        loadContent()
    }
    
    private fun observeConnectedPlugins() {
        viewModelScope.launch {
            pluginRepository.connectedPlugins.collect { plugins ->
                _uiState.update { it.copy(connectedPlugins = plugins) }
            }
        }
    }
    
    fun loadContent() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            try {
                // Load categories
                val categoriesResult = pluginRepository.getBrowseCategories(limit = 10)
                val categories = categoriesResult.getOrNull()?.items.orEmpty()
                
                // Load albums
                val albumsResult = pluginRepository.getLibraryAlbums(limit = 10)
                val albums = albumsResult.getOrNull()?.items.orEmpty()
                
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        categories = categories,
                        recentAlbums = albums
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load content"
                    )
                }
            }
        }
    }
    
    fun refresh() {
        loadContent()
    }

    fun onTimeChanged() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        val greetingType = when (hour) {
            in 5..11 -> GreetingType.MORNING
            in 12..16 -> GreetingType.AFTERNOON
            in 17..20 -> GreetingType.EVENING
            else -> GreetingType.NIGHT
        }

        _uiState.update { it.copy(greetingType = greetingType) }
    }
}

enum class GreetingType {
    NIGHT, MORNING, AFTERNOON, EVENING
}
