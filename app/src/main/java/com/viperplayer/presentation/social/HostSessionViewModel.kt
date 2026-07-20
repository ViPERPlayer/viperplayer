package com.viperplayer.presentation.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.domain.model.ListenSession
import com.viperplayer.domain.repository.ListenTogetherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owns the *host* side of a "Listen together" (Jam) session for the [HostSessionScreen] sheet — the
 * host counterpart to [JoinSessionViewModel].
 *
 * On open it starts a real session through the (currently mock-backed)
 * [com.viperplayer.domain.repository.ListenTogetherRepository]: [startHosting] creates one and the
 * sheet renders the session's real `code` / `inviteUrl`; [endSession] tears it down. All backend work
 * runs in [viewModelScope] — the composable only renders [state] and forwards events (copy / share /
 * end).
 */
@HiltViewModel
class HostSessionViewModel @Inject constructor(
    private val repository: ListenTogetherRepository,
) : ViewModel() {

    private val _starting = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _ended = MutableStateFlow(false)

    // Whether the host currently wants a session. Written only on the (Main-confined) viewModelScope,
    // so it needs no synchronization. Guards the create/end race: on the real backend path
    // startSession() is a suspending REST+WS call, so a host who ends the session before it returns
    // would otherwise leak a session created after endSession() already ran.
    private var wantsSession = false

    /**
     * UI-facing host state: the active session (from the repository) plus the in-flight/error/ended
     * flags. Shared while subscribed so the sheet gets the latest value when it opens.
     */
    val state: StateFlow<HostSessionUiState> =
        combine(repository.currentSession, _starting, _error, _ended) { session, starting, error, ended ->
            HostSessionUiState(session = session, starting = starting, error = error, ended = ended)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HostSessionUiState(starting = true),
        )

    init {
        startHosting()
    }

    /**
     * Start hosting a session if one isn't already active. Idempotent: a second call while a session
     * exists (or a start is in flight) is a no-op, so re-entering the sheet reuses the same code.
     */
    fun startHosting() {
        if (_starting.value || repository.currentSession.value != null) return
        wantsSession = true
        viewModelScope.launch {
            _starting.value = true
            _error.value = null
            repository.startSession().fold(
                onSuccess = {
                    _starting.value = false
                    // If the host ended the sheet while the create was in flight, tear the
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

    /** The shareable invite URL for the active session, or the empty string when there is none. */
    fun inviteUrl(): String = repository.currentSession.value?.inviteUrl.orEmpty()

    /** End / leave the current session (host closes the sheet or taps "end"). */
    fun endSession() {
        wantsSession = false
        _ended.value = true
        viewModelScope.launch { repository.leaveSession() }
    }
}

/** Host-side UI state for the [HostSessionScreen] sheet. */
data class HostSessionUiState(
    val session: ListenSession? = null,
    val starting: Boolean = false,
    val error: String? = null,
    /** Set once the host ends the session — the screen observes this to navigate away. */
    val ended: Boolean = false,
)
