package com.viperplayer.presentation.plugins

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.domain.model.Plugin
import com.viperplayer.domain.model.PluginInfo
import com.viperplayer.domain.repository.PluginRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * UI State for Plugins screen.
 */
data class PluginsUiState(
    val isRefreshing: Boolean = false,
    val discoveredPlugins: List<PluginInfo> = emptyList(),
    val connectedPlugins: Map<String, Plugin> = emptyMap(),
    val enabledStates: Map<String, Boolean> = emptyMap(),
    val togglingPluginId: String? = null,
    val error: String? = null
)

/**
 * ViewModel for Plugins screen.
 */
@HiltViewModel
class PluginsViewModel @Inject constructor(
    private val pluginRepository: PluginRepository
) : ViewModel() {
    companion object {
        private const val TAG = "PluginsViewModel"
    }

    private val _uiState = MutableStateFlow(PluginsUiState())
    val uiState: StateFlow<PluginsUiState> = _uiState.asStateFlow()

    init {
        Timber.d("ViewModel initialized")
        observePlugins()
        refresh()
    }

    private fun observePlugins() {
        Timber.d("Starting to observe plugins")
        viewModelScope.launch {
            pluginRepository.discoveredPlugins.collect { plugins ->
                Timber.d("Discovered plugins updated: ${plugins.size} plugins")
                _uiState.update { it.copy(discoveredPlugins = plugins) }
            }
        }

        viewModelScope.launch {
            pluginRepository.connectedPlugins.collect { plugins ->
                Timber.d("Connected plugins updated: ${plugins.size} plugins")
                _uiState.update {
                    it.copy(connectedPlugins = plugins.associateBy { p -> p.info.id })
                }
            }
        }

        viewModelScope.launch {
            pluginRepository.disabledPlugins.collect { disabled ->
                Timber.d("Disabled plugins updated: $disabled")
                // enabledStates maps a plugin id to false only when disabled; absent => enabled.
                _uiState.update { it.copy(enabledStates = disabled.associateWith { false }) }
            }
        }
    }

    fun refresh() {
        Timber.d("refresh() called")

        _uiState.update { it.copy(isRefreshing = true, error = null) }

        viewModelScope.launch {
            try {
                pluginRepository.refreshPlugins()
                Timber.d("refresh() completed successfully")
            } catch (e: Exception) {
                Timber.e(e, "Error in refresh()")
                _uiState.update { it.copy(error = e.message) }
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun togglePlugin(pluginId: String) {
        Timber.d("togglePlugin() called for: $pluginId")
        viewModelScope.launch {
            val isEnabled = _uiState.value.enabledStates[pluginId] ?: true
            Timber.d("Plugin $pluginId current state: enabled=$isEnabled")
            _uiState.update { it.copy(togglingPluginId = pluginId, error = null) }

            val result = if (isEnabled) {
                Timber.d("Disabling plugin: $pluginId")
                pluginRepository.disablePlugin(pluginId)
            } else {
                Timber.d("Enabling plugin: $pluginId")
                pluginRepository.enablePlugin(pluginId)
            }

            result.onFailure { e ->
                Timber.e(e, "Failed to toggle plugin: $pluginId")
                _uiState.update {
                    it.copy(
                        togglingPluginId = null,
                        error = e.message
                            ?: "Failed to ${if (isEnabled) "disable" else "enable"} plugin"
                    )
                }
            }

            result.onSuccess {
                Timber.d("Successfully toggled plugin: $pluginId")
            }

            _uiState.update { it.copy(togglingPluginId = null) }
        }
    }

    fun isEnabled(pluginId: String): Boolean {
        return _uiState.value.enabledStates[pluginId] ?: true
    }

    fun isConnected(pluginId: String): Boolean {
        return _uiState.value.connectedPlugins.containsKey(pluginId)
    }

    fun getConnectedPlugin(pluginId: String): Plugin? {
        return _uiState.value.connectedPlugins[pluginId]
    }
}

