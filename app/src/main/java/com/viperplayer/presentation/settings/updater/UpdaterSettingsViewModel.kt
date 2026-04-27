package com.viperplayer.presentation.settings.updater

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

sealed class UpdateState {
    data object Idle : UpdateState()
    data object Checking : UpdateState()
    data class UpToDate(val currentVersion: String) : UpdateState()
    data class UpdateAvailable(
        val currentVersion: String,
        val latestVersion: String,
        val changelog: String
    ) : UpdateState()

    data class Downloading(val currentVersion: String, val latestVersion: String) : UpdateState()
    data class Error(val message: String, val currentVersion: String) : UpdateState()
}

data class UpdaterSettingsUiState(
    val currentVersion: String = "Unknown",
    val updateState: UpdateState = UpdateState.Idle
)

@HiltViewModel
class UpdaterSettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(UpdaterSettingsUiState())
    val uiState: StateFlow<UpdaterSettingsUiState> = _uiState.asStateFlow()

    init {
        loadCurrentVersion()
    }

    private fun loadCurrentVersion() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val packageInfo = context.packageManager.getPackageInfo(
                        context.packageName,
                        0
                    )
                    val versionName = packageInfo.versionName ?: "Unknown"
                    _uiState.update {
                        it.copy(
                            currentVersion = versionName,
                            updateState = UpdateState.UpToDate(versionName)
                        )
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to load current version")
                    _uiState.update {
                        it.copy(
                            currentVersion = "Unknown",
                            updateState = UpdateState.Error("Failed to load version", "Unknown")
                        )
                    }
                }
            }
        }
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            val currentVersion = _uiState.value.currentVersion
            _uiState.update {
                it.copy(updateState = UpdateState.Checking)
            }

            try {
                // Simulate network call to check for updates
                delay(1000)

                withContext(Dispatchers.IO) {
                    // TODO: Implement actual update check API call
                    // For now, this is a placeholder that always returns no update
                    // In a real implementation, you would:
                    // 1. Make an API call to your update server
                    // 2. Compare version codes/names
                    // 3. Fetch changelog if update is available

                    val hasUpdate = false // Placeholder
                    val latestVersion: String? = null // Placeholder
                    val changelog = "" // Placeholder

                    _uiState.update {
                        it.copy(
                            updateState = if (hasUpdate && latestVersion != null) {
                                UpdateState.UpdateAvailable(
                                    currentVersion = currentVersion,
                                    latestVersion = latestVersion,
                                    changelog = changelog
                                )
                            } else {
                                UpdateState.UpToDate(currentVersion)
                            }
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to check for updates")
                _uiState.update {
                    it.copy(
                        updateState = UpdateState.Error(
                            message = "Failed to check for updates: ${e.message}",
                            currentVersion = currentVersion
                        )
                    )
                }
            }
        }
    }

    fun downloadUpdate() {
        viewModelScope.launch {
            val state = _uiState.value.updateState
            if (state is UpdateState.UpdateAvailable) {
                _uiState.update {
                    it.copy(
                        updateState = UpdateState.Downloading(
                            currentVersion = state.currentVersion,
                            latestVersion = state.latestVersion
                        )
                    )
                }

                try {
                    // TODO: Implement actual update download
                    // In a real implementation, you would:
                    // 1. Download the APK from your update server
                    // 2. Verify the APK signature
                    // 3. Launch the installer intent

                    delay(2000) // Simulate download

                    _uiState.update {
                        it.copy(
                            updateState = UpdateState.Error(
                                message = "Update download not yet implemented. Please update manually from the app store.",
                                currentVersion = state.currentVersion
                            )
                        )
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to download update")
                    _uiState.update {
                        it.copy(
                            updateState = UpdateState.Error(
                                message = "Failed to download update: ${e.message}",
                                currentVersion = state.currentVersion
                            )
                        )
                    }
                }
            }
        }
    }
}
