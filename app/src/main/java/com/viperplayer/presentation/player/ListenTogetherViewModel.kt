package com.viperplayer.presentation.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.domain.model.ListenSession
import com.viperplayer.domain.repository.ListenTogetherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owns the *host* side of a "Listen together" session for the player social sheets.
 *
 * The share sheet used to fabricate its own code inside the Composable, so the shared code mapped to
 * no real backend session (guests would 404). This ViewModel instead drives a real session through
 * [ListenTogetherRepository]: [startHosting] creates one (mock repo when no backend is configured, the
 * real session service otherwise) and the sheet renders the session's real `code` / `inviteUrl`.
 * [leaveHosting] tears it down. The join path (guest side) stays in [com.viperplayer.presentation.social.JoinSessionViewModel].
 */
@HiltViewModel
class ListenTogetherViewModel @Inject constructor(
    private val repository: ListenTogetherRepository,
) : ViewModel() {

    /** The active session (from the repository), or null when not hosting/joined. */
    val session: StateFlow<ListenSession?> = repository.currentSession

    private val _starting = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    // Whether the host currently wants a session. Written only on the (Main-confined) viewModelScope,
    // so it needs no synchronization. Guards the create/leave race: on the real backend path
    // startSession() is a suspending REST+WS call, so a user who dismisses the sheet before it returns
    // would otherwise leak a session created after leaveHosting() already ran.
    private var wantsSession = false

    /**
     * UI-facing host state: the session (if any) plus the in-flight/error flags for the sheet. Shared
     * eagerly (not WhileSubscribed) so the latest value is always available — the sheet subscribes and
     * unsubscribes as it opens/closes, and this combine is cheap.
     */
    val uiState: StateFlow<ListenTogetherUiState> =
        combine(session, _starting, _error) { session, starting, error ->
            ListenTogetherUiState(session = session, starting = starting, error = error)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ListenTogetherUiState(),
        )

    /**
     * Start hosting a session if one isn't already active. Idempotent: a second call while a session
     * exists (or a start is in flight) is a no-op, so re-opening the sheet reuses the same code.
     */
    fun startHosting() {
        if (_starting.value || session.value != null) return
        wantsSession = true
        viewModelScope.launch {
            _starting.value = true
            _error.value = null
            repository.startSession().fold(
                onSuccess = {
                    _starting.value = false
                    // If the user dismissed the sheet while the create was in flight, tear the
                    // just-created session down immediately (leaveSession is idempotent).
                    if (!wantsSession) repository.leaveSession()
                },
                onFailure = { e ->
                    _starting.value = false
                    _error.value = e.message
                },
            )
        }
    }

    /** Leave / end the current session (host closes the sheet). */
    fun leaveHosting() {
        wantsSession = false
        viewModelScope.launch { repository.leaveSession() }
    }
}

/** Host-side UI state for the Listen-together sheet. */
data class ListenTogetherUiState(
    val session: ListenSession? = null,
    val starting: Boolean = false,
    val error: String? = null,
)
