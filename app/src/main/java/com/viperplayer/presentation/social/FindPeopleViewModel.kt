package com.viperplayer.presentation.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.R
import com.viperplayer.data.resources.StringProvider
import com.viperplayer.domain.social.FollowRepository
import com.viperplayer.domain.social.FollowResult
import com.viperplayer.domain.social.FollowUser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the "Find people" discovery screen (social S1). Holds the search query, debounces it, and runs
 * [FollowRepository.search]; tracks which of the results the signed-in account already follows (seeded
 * from [FollowRepository.following] on first load) so each row can show a Follow / Following toggle, and
 * applies follow/unfollow optimistically. The gate ([enabled]) mirrors whether the backend is configured
 * so the screen can render its gated state. All logic lives here — the composable only renders + forwards.
 */
@HiltViewModel
class FindPeopleViewModel @Inject constructor(
    private val followRepository: FollowRepository,
    private val stringProvider: StringProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        FindPeopleUiState(enabled = followRepository.isConfigured),
    )
    val uiState: StateFlow<FindPeopleUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        if (followRepository.isConfigured) {
            viewModelScope.launch {
                // Seed the "already following" set so results render the correct toggle state.
                val result = followRepository.following()
                if (result is FollowResult.Success) {
                    _uiState.update { it.copy(followingIds = result.value.map(FollowUser::userId).toSet()) }
                }
            }
        }
    }

    /** Updates the query and (debounced) runs the search. A blank query clears the results. */
    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(results = emptyList(), searching = false, error = null) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            _uiState.update { it.copy(searching = true, error = null) }
            when (val result = followRepository.search(query)) {
                is FollowResult.Success ->
                    _uiState.update { it.copy(results = result.value, searching = false, error = null) }
                is FollowResult.Failed ->
                    _uiState.update { it.copy(searching = false, error = result.message) }
                FollowResult.NetworkError ->
                    _uiState.update { it.copy(searching = false, error = stringProvider.getString(R.string.error_server_unreachable)) }
                FollowResult.NotAuthenticated ->
                    _uiState.update { it.copy(searching = false, error = stringProvider.getString(R.string.find_people_error_sign_in)) }
                FollowResult.NotConfigured ->
                    _uiState.update { it.copy(enabled = false, searching = false) }
            }
        }
    }

    /** Optimistically follows/unfollows [user], reverting the toggle if the backend call fails. */
    fun onToggleFollow(user: FollowUser) {
        val currentlyFollowing = user.userId in _uiState.value.followingIds
        // Optimistic: flip immediately so the toggle feels instant.
        _uiState.update { it.copy(followingIds = it.followingIds.toggled(user.userId, !currentlyFollowing)) }
        viewModelScope.launch {
            val result = if (currentlyFollowing) {
                followRepository.unfollow(user.userId)
            } else {
                followRepository.follow(user.userId)
            }
            if (result !is FollowResult.Success) {
                // Revert on failure.
                _uiState.update { it.copy(followingIds = it.followingIds.toggled(user.userId, currentlyFollowing)) }
            }
        }
    }

    private fun Set<String>.toggled(id: String, present: Boolean): Set<String> =
        if (present) this + id else this - id

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}

/**
 * UI state for the "Find people" screen. [enabled] mirrors the backend gate; [query] is the live search
 * text; [results] are the matched users; [followingIds] is the set of user ids the signed-in account
 * follows (drives each row's Follow / Following toggle); [searching] shows the inline spinner and
 * [error] a transient message. A blank query with no results is the resting state.
 */
data class FindPeopleUiState(
    val enabled: Boolean = false,
    val query: String = "",
    val results: List<FollowUser> = emptyList(),
    val followingIds: Set<String> = emptySet(),
    val searching: Boolean = false,
    val error: String? = null,
)
