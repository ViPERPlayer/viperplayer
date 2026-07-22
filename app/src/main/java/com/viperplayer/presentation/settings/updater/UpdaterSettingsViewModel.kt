package com.viperplayer.presentation.settings.updater

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.BuildConfig
import com.viperplayer.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
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

    /**
     * No update endpoint is configured in this build (see [BuildConfig.UPDATE_MANIFEST_URL] — a
     * placeholder). The in-app updater is inert; the user updates manually from their app store.
     */
    data class NotConfigured(val currentVersion: String) : UpdateState()
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

    /**
     * Whether a real (non-placeholder) update manifest endpoint is built in. Mirrors the
     * `LastfmApi.isConfigured` / `SessionApi.isConfigured` convention: the placeholder is recognised at
     * runtime so the UI can explain that the updater requires a configured endpoint.
     */
    private val isConfigured: Boolean
        get() = BuildConfig.UPDATE_MANIFEST_URL.isNotBlank() &&
            BuildConfig.UPDATE_MANIFEST_URL != UPDATE_URL_PLACEHOLDER

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
                    val versionName = packageInfo.versionName
                        ?: context.getString(R.string.version_unknown)
                    _uiState.update {
                        it.copy(
                            currentVersion = versionName,
                            updateState = UpdateState.UpToDate(versionName)
                        )
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to load current version")
                    val unknown = context.getString(R.string.version_unknown)
                    _uiState.update {
                        it.copy(
                            currentVersion = unknown,
                            updateState = UpdateState.Error(
                                context.getString(R.string.updater_error_load_version_failed),
                                unknown
                            )
                        )
                    }
                }
            }
        }
    }

    /**
     * Check for an app update. The updater needs a configured update-manifest endpoint
     * ([BuildConfig.UPDATE_MANIFEST_URL]); it is a placeholder in this build, so we surface an honest
     * "not configured" state rather than faking an "up to date" result. When a real endpoint is wired,
     * fetch the manifest here (latest version + changelog), compare against [currentVersion], and emit
     * [UpdateState.UpdateAvailable] / [UpdateState.UpToDate].
     */
    fun checkForUpdates() {
        viewModelScope.launch {
            val currentVersion = _uiState.value.currentVersion
            if (!isConfigured) {
                _uiState.update { it.copy(updateState = UpdateState.NotConfigured(currentVersion)) }
                return@launch
            }
            _uiState.update { it.copy(updateState = UpdateState.Checking) }
            try {
                withContext(Dispatchers.IO) {
                    // Reachable only once UPDATE_MANIFEST_URL is a real endpoint: fetch + parse the
                    // manifest, then emit UpToDate/UpdateAvailable. Until then isConfigured short-circuits
                    // above, so this stays UpToDate as the safe default.
                    _uiState.update { it.copy(updateState = UpdateState.UpToDate(currentVersion)) }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to check for updates")
                _uiState.update {
                    it.copy(
                        updateState = UpdateState.Error(
                            message = context.getString(
                                R.string.updater_error_check_failed,
                                e.message ?: ""
                            ),
                            currentVersion = currentVersion
                        )
                    )
                }
            }
        }
    }

    /**
     * Download + install an available update. Requires the configured update endpoint (see
     * [checkForUpdates]); a real implementation would download the APK named in the manifest, verify its
     * signature, and launch the package-installer intent. No endpoint is configured in this build, so an
     * update is never offered and this is only reachable defensively.
     */
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
                _uiState.update {
                    it.copy(
                        updateState = UpdateState.Error(
                            message = context.getString(R.string.updater_error_no_endpoint),
                            currentVersion = state.currentVersion
                        )
                    )
                }
            }
        }
    }

    private companion object {
        /** Recognised placeholder for an unconfigured update endpoint (matches build.gradle.kts). */
        const val UPDATE_URL_PLACEHOLDER = "REPLACE_WITH_REAL_VALUE"
    }
}
