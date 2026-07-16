package com.viperplayer.presentation.settings.lastfm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.domain.lastfm.LastfmRepository
import com.viperplayer.domain.lastfm.LastfmSettings
import com.viperplayer.domain.lastfm.LoginResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the Last.fm settings/login screen. Mirrors the persisted [LastfmSettings] into UI state,
 * runs login/logout, and forwards toggle edits to the [LastfmRepository]. All logic lives here; the
 * screen only renders + emits events.
 *
 * SECURITY: the password is only ever passed through [login] into the repository (which discards it
 * after obtaining a session key). It is never held in this ViewModel's state and never logged.
 */
@HiltViewModel
class LastfmSettingsViewModel @Inject constructor(
    private val repository: LastfmRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        LastfmSettingsUiState(isApiConfigured = repository.isApiConfigured)
    )
    val uiState: StateFlow<LastfmSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.settings.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
    }

    fun login(username: String, password: String) {
        if (_uiState.value.isLoggingIn) return
        _uiState.update { it.copy(isLoggingIn = true, loginError = null) }
        viewModelScope.launch {
            val result = repository.login(username, password)
            _uiState.update {
                it.copy(
                    isLoggingIn = false,
                    loginError = when (result) {
                        is LoginResult.Success -> null
                        is LoginResult.Failed -> result.message
                        LoginResult.NetworkError -> "Couldn't reach Last.fm. Check your connection."
                        LoginResult.NotConfigured -> "Last.fm API key is not configured in this build."
                    },
                )
            }
        }
    }

    fun logout() {
        viewModelScope.launch { repository.logout() }
    }

    fun dismissLoginError() {
        _uiState.update { it.copy(loginError = null) }
    }

    fun setScrobblingEnabled(enabled: Boolean) = repository.setScrobblingEnabled(enabled)

    fun setNowPlayingEnabled(enabled: Boolean) = repository.setNowPlayingEnabled(enabled)

    fun setScrobbleOnWifiOnly(enabled: Boolean) = repository.setScrobbleOnWifiOnly(enabled)
}

/**
 * The Last.fm settings screen state.
 *
 * @param settings the persisted settings + auth state (username, toggles). Never carries a session key.
 * @param isApiConfigured whether a real API key/secret is built in; drives the "not configured" notice.
 * @param isLoggingIn a login request is in flight (disables the button / shows progress).
 * @param loginError a message to show for the last failed login, or null.
 */
data class LastfmSettingsUiState(
    val settings: LastfmSettings = LastfmSettings(),
    val isApiConfigured: Boolean = true,
    val isLoggingIn: Boolean = false,
    val loginError: String? = null,
)
