package com.viperplayer.presentation.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.domain.account.AccountRepository
import com.viperplayer.domain.account.AccountState
import com.viperplayer.domain.account.AuthResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which auth form the account screen is showing when signed out. */
enum class AuthMode { SignIn, Register }

/**
 * UI state for the account screen. Render-only inputs (email/password/name) are held by the screen;
 * this exposes the account/sign-in state plus in-flight + error state for the form.
 */
data class AccountUiState(
    val account: AccountState = AccountState(),
    val isConfigured: Boolean = true,
    val mode: AuthMode = AuthMode.SignIn,
    val isSubmitting: Boolean = false,
    val error: String? = null,
)

/**
 * ViewModel for the account (sign in / register) screen. Observes the [AccountRepository] state and
 * forwards register/login/logout events. All network + persistence lives in the repository — this
 * only holds UI state (MVVM house rule).
 */
@HiltViewModel
class AccountViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AccountUiState(isConfigured = accountRepository.isConfigured)
    )
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            accountRepository.state.collect { account ->
                _uiState.update { it.copy(account = account) }
            }
        }
    }

    fun setMode(mode: AuthMode) {
        _uiState.update { it.copy(mode = mode, error = null) }
    }

    fun signIn(email: String, password: String) {
        submit { accountRepository.login(email, password) }
    }

    fun register(email: String, password: String, displayName: String) {
        submit { accountRepository.register(email, password, displayName.takeIf { it.isNotBlank() }) }
    }

    fun signOut() {
        viewModelScope.launch { accountRepository.logout() }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun submit(action: suspend () -> AuthResult) {
        if (_uiState.value.isSubmitting) return
        _uiState.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            val result = action()
            _uiState.update {
                it.copy(
                    isSubmitting = false,
                    error = when (result) {
                        is AuthResult.Success -> null
                        is AuthResult.Failed -> result.message
                        AuthResult.NetworkError -> "Couldn't reach the server. Check your connection."
                        AuthResult.NotConfigured -> "Accounts aren't available in this build."
                    },
                )
            }
        }
    }
}
