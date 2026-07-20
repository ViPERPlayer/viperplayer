package com.viperplayer.presentation.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.domain.model.ListenSession
import com.viperplayer.domain.repository.ListenTogetherRepository
import com.viperplayer.presentation.navigation.JoinSession
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class JoinSessionUiState(
    val code: String = "",
    val codeLength: Int = 6,
    val joining: Boolean = false,
    val error: String? = null,
    /** Set once a session is joined — the screen observes this to navigate away. */
    val joined: ListenSession? = null,
) {
    val isComplete: Boolean get() = code.length >= codeLength
}

@HiltViewModel(assistedFactory = JoinSessionViewModel.Factory::class)
class JoinSessionViewModel @AssistedInject constructor(
    @Assisted private val route: JoinSession,
    private val repository: ListenTogetherRepository,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(route: JoinSession): JoinSessionViewModel
    }

    private val _state = MutableStateFlow(JoinSessionUiState(codeLength = repository.codeLength))
    val state: StateFlow<JoinSessionUiState> = _state.asStateFlow()

    init {
        // Seed the manual code from a prefill (e.g. arriving from a friend's "Join" affordance),
        // normalising it the same way as a scan. Does NOT auto-join — the guest still taps Join.
        route.prefillCode?.let { prefill ->
            val code = repository.parseCode(prefill) ?: prefill
            val cleaned = code.uppercase().filter { it.isLetterOrDigit() }.take(repository.codeLength)
            if (cleaned.isNotEmpty()) _state.update { it.copy(code = cleaned) }
        }
    }

    /** User typed/edited the manual code — keep only allowed characters, cap at the code length. */
    fun onCodeChange(raw: String) {
        val cleaned = raw.uppercase().filter { it.isLetterOrDigit() }.take(repository.codeLength)
        _state.update { it.copy(code = cleaned, error = null) }
    }

    /** A QR code was decoded from the camera — parse and auto-join. */
    fun onScanned(text: String) {
        if (_state.value.joining || _state.value.joined != null) return
        val code = repository.parseCode(text) ?: return
        _state.update { it.copy(code = code, error = null) }
        join()
    }

    /** Pasted an invite link or raw code. */
    fun onPaste(text: String) {
        val code = repository.parseCode(text)
        if (code != null) {
            _state.update { it.copy(code = code, error = null) }
        } else {
            _state.update { it.copy(error = "Couldn't read a code from that") }
        }
    }

    fun join() {
        val current = _state.value
        if (!current.isComplete || current.joining) return
        viewModelScope.launch {
            _state.update { it.copy(joining = true, error = null) }
            repository.joinSession(current.code).fold(
                onSuccess = { session -> _state.update { it.copy(joining = false, joined = session) } },
                onFailure = { e -> _state.update { it.copy(joining = false, error = e.message ?: "Couldn't join") } },
            )
        }
    }
}
