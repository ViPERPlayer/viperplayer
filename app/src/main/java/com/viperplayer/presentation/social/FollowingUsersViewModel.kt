package com.viperplayer.presentation.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.domain.social.FollowRepository
import com.viperplayer.domain.social.FollowResult
import com.viperplayer.domain.social.FollowUser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the "Following" users list (social S1): loads the accounts the signed-in user follows via
 * [FollowRepository.following], and supports unfollowing a row (optimistically removing it, re-inserting
 * on failure). The gate ([enabled]) mirrors whether the backend is configured. All logic lives here — the
 * composable only renders + forwards.
 */
@HiltViewModel
class FollowingUsersViewModel @Inject constructor(
    private val followRepository: FollowRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        FollowingUsersUiState(
            enabled = followRepository.isConfigured,
            loading = followRepository.isConfigured,
        ),
    )
    val uiState: StateFlow<FollowingUsersUiState> = _uiState.asStateFlow()

    init {
        if (followRepository.isConfigured) load()
    }

    fun load() {
        if (!followRepository.isConfigured) {
            _uiState.update { it.copy(enabled = false, loading = false) }
            return
        }
        _uiState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = followRepository.following()) {
                is FollowResult.Success ->
                    _uiState.update { it.copy(loading = false, users = result.value, error = null) }
                is FollowResult.Failed ->
                    _uiState.update { it.copy(loading = false, error = result.message) }
                FollowResult.NetworkError ->
                    _uiState.update { it.copy(loading = false, error = NETWORK_ERROR) }
                FollowResult.NotAuthenticated ->
                    _uiState.update { it.copy(loading = false, error = SIGN_IN_ERROR) }
                FollowResult.NotConfigured ->
                    _uiState.update { it.copy(enabled = false, loading = false) }
            }
        }
    }

    /** Optimistically unfollows [user], removing it from the list and re-inserting it if the call fails. */
    fun onUnfollow(user: FollowUser) {
        val previous = _uiState.value.users
        _uiState.update { it.copy(users = it.users.filterNot { u -> u.userId == user.userId }) }
        viewModelScope.launch {
            val result = followRepository.unfollow(user.userId)
            if (result !is FollowResult.Success) {
                _uiState.update { it.copy(users = previous) }
            }
        }
    }

    private companion object {
        const val NETWORK_ERROR = "Couldn't reach the server. Check your connection."
        const val SIGN_IN_ERROR = "Sign in to see who you follow."
    }
}

/**
 * UI state for the "Following" users list. [enabled] mirrors the backend gate; while [loading] the screen
 * shows a spinner; [users] is the followed set; [error] a transient message. Empty [users] with
 * `enabled && !loading && error == null` is the "you don't follow anyone yet" empty state.
 */
data class FollowingUsersUiState(
    val enabled: Boolean = false,
    val loading: Boolean = false,
    val users: List<FollowUser> = emptyList(),
    val error: String? = null,
)
