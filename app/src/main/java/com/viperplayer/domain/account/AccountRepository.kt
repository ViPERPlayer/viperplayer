package com.viperplayer.domain.account

import com.viperplayer.data.account.AccountApiResult
import kotlinx.coroutines.flow.Flow

/**
 * Coordinates the ViPER account: registration, login, logout, and token refresh
 * against the ViPER backend. All network + token persistence lives behind this
 * interface; the UI (a ViewModel) only observes [state] and forwards
 * register/login/logout events.
 */
interface AccountRepository {

    /** The current account + sign-in state. Access/refresh tokens are never exposed. */
    val state: Flow<AccountState>

    /** Whether a backend URL is configured. When false the UI must explain auth won't function. */
    val isConfigured: Boolean

    /**
     * Registers a new account with [email] + [password] (and optional [displayName]). On success the
     * returned tokens are persisted and the password is discarded. Returns an [AuthResult].
     */
    suspend fun register(email: String, password: String, displayName: String?): AuthResult

    /**
     * Logs in with [email] + [password]. On success the returned tokens are persisted and the
     * password is discarded. Returns an [AuthResult].
     */
    suspend fun login(email: String, password: String): AuthResult

    /** Clears the stored session (logout), best-effort revoking the refresh token server-side. */
    suspend fun logout()

    /**
     * Returns a currently-valid access token, transparently refreshing it with the stored refresh
     * token if it has expired. Null when signed out or the refresh failed (the caller should treat
     * that as signed-out). For use by other authenticated backend calls (library sync, WS upgrade).
     */
    suspend fun validAccessToken(): String?

    /**
     * Runs an authenticated backend [call] with the current access token attached, transparently
     * refreshing and retrying ONCE if the server answers [AccountApiResult.Unauthenticated]. Returns
     * [AccountApiResult.Unauthenticated] when there is no usable session or the retry also fails.
     *
     * The seam other backend transports (e.g. the library-sync client) use to make authenticated
     * calls without duplicating the token refresh-and-retry policy. The [call] receives a valid
     * access token and issues the actual HTTP request.
     */
    suspend fun <T> withBackendAuth(
        call: suspend (accessToken: String) -> AccountApiResult<T>,
    ): AccountApiResult<T>
}
