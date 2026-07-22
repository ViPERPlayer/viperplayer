package com.viperplayer.presentation.account

import android.text.format.DateUtils
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.R
import com.viperplayer.data.preferences.LibrarySyncPreferences
import com.viperplayer.data.resources.StringProvider
import com.viperplayer.data.sync.LibrarySync
import com.viperplayer.data.sync.LibrarySyncStatusStore
import com.viperplayer.domain.account.AccountRepository
import com.viperplayer.domain.account.AccountState
import com.viperplayer.domain.account.AuthResult
import com.viperplayer.domain.sync.LibrarySyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/** Which auth form the account screen is showing when signed out. */
enum class AuthMode { SignIn, Register }

/**
 * A pre-formatted view of the last local library sync for the Account/You screens: the aggregate
 * counts and a relative "Synced N min ago" label, formatted in the ViewModel (never the composable).
 * [hasSynced] is false until the first sync completes.
 */
data class SyncStatusUi(
    val hasSynced: Boolean = false,
    val relativeTime: String = "",
    val songs: Int = 0,
    val albums: Int = 0,
    val artists: Int = 0,
    val playlists: Int = 0,
)

/**
 * UI state for the account screen. Render-only inputs (email/password/name) are held by the screen;
 * this exposes the account/sign-in state plus in-flight + error state for the form, the member-since
 * label, and the library-sync toggle/status/progress.
 *
 * @param memberSince pre-formatted "MMM yyyy" (empty when the created-at is unknown — hide the row).
 * @param syncEnabled whether local library sync is on.
 * @param isSyncing whether a "Sync now" run is currently in progress.
 * @param syncStatus pre-formatted last-sync counts + relative time.
 */
data class AccountUiState(
    val account: AccountState = AccountState(),
    val isConfigured: Boolean = true,
    val mode: AuthMode = AuthMode.SignIn,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val memberSince: String = "",
    val syncEnabled: Boolean = true,
    val isSyncing: Boolean = false,
    val syncStatus: SyncStatusUi = SyncStatusUi(),
)

/**
 * ViewModel for the account (sign in / register / manage) screen. Observes the [AccountRepository]
 * state and forwards register/login/logout/change-password/delete events, and drives the local
 * library-sync toggle/status/"Sync now". All network + persistence + iteration lives in the
 * repository/manager/store — this only holds UI state and launches + observes (MVVM house rule).
 */
@HiltViewModel
class AccountViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val librarySync: LibrarySync,
    private val librarySyncPreferences: LibrarySyncPreferences,
    librarySyncStatusStore: LibrarySyncStatusStore,
    private val stringProvider: StringProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AccountUiState(isConfigured = accountRepository.isConfigured)
    )
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

    /** One-shot signal emitted when a change-password succeeds, so the screen can close the dialog. */
    private val _changePasswordSucceeded = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val changePasswordSucceeded: SharedFlow<Unit> = _changePasswordSucceeded.asSharedFlow()

    /** One-shot signal emitted when setting the @handle succeeds, so the screen can close the dialog. */
    private val _setHandleSucceeded = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val setHandleSucceeded: SharedFlow<Unit> = _setHandleSucceeded.asSharedFlow()

    init {
        viewModelScope.launch {
            accountRepository.state.collect { account ->
                _uiState.update {
                    it.copy(account = account, memberSince = formatMemberSince(account))
                }
            }
        }
        viewModelScope.launch {
            librarySyncPreferences.isEnabled.collect { enabled ->
                _uiState.update { it.copy(syncEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            librarySyncStatusStore.status.collect { status ->
                _uiState.update { it.copy(syncStatus = status.toUi()) }
            }
        }
        viewModelScope.launch {
            librarySync.syncing.collect { syncing ->
                _uiState.update { it.copy(isSyncing = syncing.isNotEmpty()) }
            }
        }
        // Refresh the cached profile so pre-existing sessions pick up the created-at the server sends.
        viewModelScope.launch { accountRepository.refreshProfile() }
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

    fun changePassword(current: String, new: String) {
        submit(
            action = { accountRepository.changePassword(current, new) },
            onSuccess = { _changePasswordSucceeded.tryEmit(Unit) },
        )
    }

    fun deleteAccount(password: String) {
        submit { accountRepository.deleteAccount(password) }
    }

    /**
     * Claims / changes the signed-in user's public `@handle` (no leading `@`). On success refreshes the
     * cached profile so the new handle is reflected, and signals the screen to close the dialog. An
     * invalid (400) or already-taken (409) handle surfaces via [AccountUiState.error].
     */
    fun setHandle(handle: String) {
        submit(
            action = { accountRepository.setHandle(handle) },
            onSuccess = {
                viewModelScope.launch { accountRepository.refreshProfile() }
                _setHandleSucceeded.tryEmit(Unit)
            },
        )
    }

    fun setSyncEnabled(enabled: Boolean) {
        viewModelScope.launch { librarySyncPreferences.setEnabled(enabled) }
    }

    /**
     * Runs a full local library sync across all connected plugins (no-op when sync is disabled or a
     * run is already in flight). All iteration + persistence lives in the [LibrarySync] manager; the
     * screen reflects progress via [AccountUiState.isSyncing].
     */
    fun syncNow() {
        if (!_uiState.value.syncEnabled || _uiState.value.isSyncing) return
        viewModelScope.launch { librarySync.syncConnectedPlugins() }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun submit(onSuccess: () -> Unit = {}, action: suspend () -> AuthResult) {
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
                        AuthResult.NetworkError -> stringProvider.getString(R.string.error_server_unreachable)
                        AuthResult.NotConfigured -> stringProvider.getString(R.string.account_error_not_configured)
                    },
                )
            }
            if (result is AuthResult.Success) onSuccess()
        }
    }

    private fun LibrarySyncStatus.toUi(): SyncStatusUi = SyncStatusUi(
        hasSynced = hasSynced,
        relativeTime = if (hasSynced) {
            DateUtils.getRelativeTimeSpanString(
                lastSyncedAtMs,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE,
            ).toString()
        } else "",
        songs = songs,
        albums = albums,
        artists = artists,
        playlists = playlists,
    )

    /** Formats the account's created-at as "MMM yyyy", or "" when unknown (0). */
    private fun formatMemberSince(account: AccountState): String {
        val createdAtMs = account.user?.createdAtMs ?: 0L
        if (createdAtMs <= 0L) return ""
        return MEMBER_SINCE_FORMAT.format(
            Instant.ofEpochMilli(createdAtMs).atZone(ZoneId.systemDefault()),
        )
    }

    private companion object {
        val MEMBER_SINCE_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault())
    }
}
