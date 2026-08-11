package com.viperplayer.presentation.plugins

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.R
import com.viperplayer.domain.plugin.PluginUpdate
import com.viperplayer.data.plugin.update.PluginUpdateManager
import com.viperplayer.domain.plugin.PluginUpdateProgress
import com.viperplayer.data.preferences.PushSyncPreferences
import com.viperplayer.data.resources.StringProvider
import com.viperplayer.data.sync.LibrarySyncManager
import com.viperplayer.data.sync.SyncResult
import com.viperplayer.data.sync.push.PushSyncManager
import com.viperplayer.domain.model.Plugin
import com.viperplayer.domain.model.PluginInfo
import com.viperplayer.domain.repository.PluginRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    /** Plugin ids the user has opted into PUSHING local library changes up to (two-way sync). */
    val pushSyncEnabled: Set<String> = emptySet(),
    val togglingPluginId: String? = null,
    /** Available plugin updates, keyed by plugin id (from [PluginUpdateManager]). */
    val availableUpdates: Map<String, PluginUpdate> = emptyMap(),
    /** True while a check-for-updates pass is running. */
    val isCheckingUpdates: Boolean = false,
    /** In-flight download/install progress per plugin id (absent = idle). */
    val updateProgress: Map<String, PluginUpdateProgress> = emptyMap(),
    val error: String? = null
) {
    /** Whether any plugin currently has an update on offer (drives the global indicator). */
    val hasUpdates: Boolean get() = availableUpdates.isNotEmpty()
}

/**
 * ViewModel for Plugins screen.
 */
@HiltViewModel
class PluginsViewModel @Inject constructor(
    private val pluginRepository: PluginRepository,
    private val librarySyncManager: LibrarySyncManager,
    private val pushSyncPreferences: PushSyncPreferences,
    private val pushSyncManager: PushSyncManager,
    private val pluginUpdateManager: PluginUpdateManager,
    private val stringProvider: StringProvider,
) : ViewModel() {
    companion object {
        private const val TAG = "PluginsViewModel"
    }

    private val _uiState = MutableStateFlow(PluginsUiState())
    val uiState: StateFlow<PluginsUiState> = _uiState.asStateFlow()

    /** Plugin ids currently having their library synced (for a per-row spinner). */
    val syncing: StateFlow<Set<String>> = librarySyncManager.syncing

    /** One-shot library-sync outcomes for the UI to surface as a Toast/snackbar. */
    private val _syncEvents = MutableSharedFlow<LibrarySyncEvent>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val syncEvents: SharedFlow<LibrarySyncEvent> = _syncEvents.asSharedFlow()

    /** One-shot download/install progress events for the UI to surface as a Toast. */
    private val _updateEvents = MutableSharedFlow<PluginUpdateProgress>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val updateEvents: SharedFlow<PluginUpdateProgress> = _updateEvents.asSharedFlow()

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

        viewModelScope.launch {
            pushSyncPreferences.enabledPlugins.collect { enabled ->
                _uiState.update { it.copy(pushSyncEnabled = enabled) }
            }
        }

        viewModelScope.launch {
            pluginUpdateManager.availableUpdates.collect { updates ->
                _uiState.update { it.copy(availableUpdates = updates) }
            }
        }

        viewModelScope.launch {
            pluginUpdateManager.isChecking.collect { checking ->
                _uiState.update { it.copy(isCheckingUpdates = checking) }
            }
        }

        viewModelScope.launch {
            pluginUpdateManager.progress.collect { progress ->
                _uiState.update { state ->
                    val next = state.updateProgress.toMutableMap()
                    when (progress) {
                        // Terminal states clear the row's progress so the button returns to idle.
                        is PluginUpdateProgress.Succeeded,
                        is PluginUpdateProgress.Failed -> next.remove(progress.pluginId)
                        else -> next[progress.pluginId] = progress
                    }
                    state.copy(updateProgress = next)
                }
                _updateEvents.emit(progress)
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
                            ?: stringProvider.getString(
                                if (isEnabled) R.string.plugins_disable_failed
                                else R.string.plugins_enable_failed
                            )
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

    /** True when the connected plugin advertises an account library that can be synced. */
    fun hasLibrary(pluginId: String): Boolean {
        return _uiState.value.connectedPlugins[pluginId]?.capabilities?.hasLibrary ?: false
    }

    /** True when the connected plugin can PUSH local library changes up to the account. */
    fun hasLibraryWrite(pluginId: String): Boolean {
        return _uiState.value.connectedPlugins[pluginId]?.capabilities?.hasLibraryWrite ?: false
    }

    /** Whether the user has opted [pluginId] into pushing local library changes up to the account. */
    fun isPushSyncEnabled(pluginId: String): Boolean =
        pluginId in _uiState.value.pushSyncEnabled

    /**
     * Turn two-way push sync on/off for [pluginId]. Enabling kicks a drain to flush anything already
     * queued; disabling discards the queued backlog so a later re-enable doesn't replay stale intents.
     */
    fun setPushSyncEnabled(pluginId: String, enabled: Boolean) {
        viewModelScope.launch {
            pushSyncPreferences.setEnabled(pluginId, enabled)
            if (enabled) {
                pushSyncManager.requestDrain(pluginId)
            } else {
                pushSyncManager.clearPending(pluginId)
            }
        }
    }

    /**
     * Pull [pluginId]'s account library into the local library, then emit a one-shot event with the
     * counts (or "no library") for the UI to Toast.
     */
    fun syncLibrary(pluginId: String) {
        Timber.d("syncLibrary() called for: $pluginId")
        viewModelScope.launch {
            val event = try {
                LibrarySyncEvent.Success(pluginId, librarySyncManager.syncPlugin(pluginId))
            } catch (e: Exception) {
                Timber.e(e, "syncLibrary() failed for: $pluginId")
                LibrarySyncEvent.Failure(pluginId, e.message)
            }
            _syncEvents.emit(event)
        }
    }

    /** The available update for [pluginId], or null if none is on offer. */
    fun updateFor(pluginId: String): PluginUpdate? =
        _uiState.value.availableUpdates[pluginId]

    /** In-flight download/install progress for [pluginId], or null if idle. */
    fun updateProgressFor(pluginId: String): PluginUpdateProgress? =
        _uiState.value.updateProgress[pluginId]

    /** Manual "Check for updates" action. */
    fun checkForUpdates() {
        Timber.d("checkForUpdates() called")
        viewModelScope.launch {
            runCatching { pluginUpdateManager.checkNow() }
                .onFailure { Timber.e(it, "checkForUpdates() failed") }
        }
    }

    /** Download + install the offered update for [pluginId] (user confirms in the system UI). */
    fun installUpdate(pluginId: String) {
        Timber.d("installUpdate() called for: $pluginId")
        pluginUpdateManager.downloadAndInstall(pluginId)
    }

    /** Dismiss the offered update for [pluginId] until a newer version appears. */
    fun dismissUpdate(pluginId: String) {
        Timber.d("dismissUpdate() called for: $pluginId")
        viewModelScope.launch { pluginUpdateManager.dismiss(pluginId) }
    }
}

/** One-shot result of a library sync, surfaced to the UI as a Toast/snackbar. */
sealed interface LibrarySyncEvent {
    val pluginId: String

    data class Success(override val pluginId: String, val result: SyncResult) : LibrarySyncEvent
    data class Failure(override val pluginId: String, val message: String?) : LibrarySyncEvent
}

