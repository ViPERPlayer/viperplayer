package com.viperplayer.data.account

import com.viperplayer.domain.account.AccountRepository
import com.viperplayer.domain.account.AccountState
import com.viperplayer.domain.account.AccountUser
import com.viperplayer.domain.account.AuthResult
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default [AccountRepository]: stitches the HTTP/JSON [AccountApi] (network) and the
 * [AccountCredentialStore] (token persistence). The password is used transiently for register/login
 * and never stored; only the returned tokens are persisted.
 *
 * Transport is HTTP/JSON REST (`/auth/`); this layer is transport-agnostic — it consumes
 * [AccountApiResult] and never sees a raw HTTP status. Token refresh is transparent:
 * [validAccessToken] refreshes an about-to-expire access token, and [withAuth] retries an authed call
 * once against a freshly-refreshed token when the server answers 401/Unauthenticated.
 */
@Singleton
class AccountRepositoryImpl @Inject constructor(
    private val client: AccountApi,
    private val credentialStore: AccountCredentialStore,
) : AccountRepository {

    override val state: Flow<AccountState> = credentialStore.state

    override val isConfigured: Boolean get() = client.isConfigured

    override suspend fun register(email: String, password: String, displayName: String?): AuthResult =
        persistAuth(client.register(email.trim(), password, displayName?.trim())) {
            it.user to it.tokens
        }

    override suspend fun login(email: String, password: String): AuthResult =
        persistAuth(client.login(email.trim(), password)) {
            it.user to it.tokens
        }

    override suspend fun logout() {
        val snapshot = credentialStore.snapshot()
        snapshot.refreshToken?.let { client.logout(it) }
        credentialStore.clear()
    }

    override suspend fun changePassword(current: String, new: String): AuthResult {
        val result = withAuth { token -> client.changePassword(token, current, new) }
        // A 401 here (after the transparent refresh-and-retry) means the CURRENT password was wrong,
        // not an expired token — surface it as a rejection rather than clearing the session.
        return result.toAuthResult(unauthenticatedMessage = "Current password is incorrect")
    }

    override suspend fun deleteAccount(password: String): AuthResult {
        val result = withAuth { token -> client.deleteAccount(token, password) }
        return when (val authResult = result.toAuthResult(unauthenticatedMessage = "Password is incorrect")) {
            is AuthResult.Success -> {
                // Account gone server-side → drop the local session so the app returns signed-out.
                credentialStore.clear()
                authResult
            }
            else -> authResult
        }
    }

    /**
     * Maps a no-body authed result ([AccountApiResult] of [Unit]) into an [AuthResult]. There is no
     * user payload, so [AuthResult.Success] carries the current cached user (or a blank placeholder);
     * callers of change/delete ignore the user and only branch on success/failure.
     */
    private suspend fun AccountApiResult<Unit>.toAuthResult(unauthenticatedMessage: String): AuthResult =
        when (this) {
            is AccountApiResult.Success -> AuthResult.Success(
                credentialStore.snapshot().state.user
                    ?: AccountUser(id = "", email = "", displayName = ""),
            )
            is AccountApiResult.Rejected -> AuthResult.Failed(message)
            AccountApiResult.Unauthenticated -> AuthResult.Failed(unauthenticatedMessage)
            AccountApiResult.NetworkError -> AuthResult.NetworkError
            AccountApiResult.NotConfigured -> AuthResult.NotConfigured
        }

    override suspend fun validAccessToken(): String? {
        val snapshot = credentialStore.snapshot()
        val access = snapshot.accessToken ?: return null
        // Refresh a little early (30s skew) to avoid handing out a token that expires mid-flight.
        if (snapshot.accessExpiresAtMs - System.currentTimeMillis() > EXPIRY_SKEW_MS) {
            return access
        }
        return forceRefresh(snapshot.refreshToken)
    }

    /**
     * Refreshes using the stored refresh token, persisting the new pair. Returns the new access token,
     * or null when there is no refresh token or the server rejected it (session cleared on rejection).
     */
    private suspend fun forceRefresh(refreshToken: String?): String? {
        val refresh = refreshToken ?: return null
        return when (val result = client.refresh(refresh)) {
            is AccountApiResult.Success -> {
                val tokens = result.value
                credentialStore.updateTokens(
                    accessToken = tokens.accessToken,
                    refreshToken = tokens.refreshToken,
                    accessExpiresAtMs = tokens.accessExpiresAtMs,
                )
                tokens.accessToken
            }
            AccountApiResult.Unauthenticated, is AccountApiResult.Rejected -> {
                // Refresh token rejected/expired → treat as signed out.
                Timber.w("Token refresh rejected; clearing session")
                credentialStore.clear()
                null
            }
            AccountApiResult.NetworkError, AccountApiResult.NotConfigured -> {
                // Transient: keep the (possibly-expired) session; caller retries later.
                null
            }
        }
    }

    /**
     * Runs an authenticated RPC with the current access token attached, transparently refreshing and
     * retrying ONCE if the server answers [AccountApiResult.Unauthenticated]. Returns
     * [AccountApiResult.Unauthenticated] when there is no usable token or the retry also fails.
     *
     * Internal (not part of the public [AccountRepository] surface) — the seam behind authenticated
     * calls such as [refreshProfile], reused by the later library-sync wiring. Kept testable.
     */
    internal suspend fun <T> withAuth(
        call: suspend (accessToken: String) -> AccountApiResult<T>,
    ): AccountApiResult<T> = runAuthenticated(
        provideToken = { validAccessToken() },
        forceRefresh = { forceRefresh(credentialStore.snapshot().refreshToken) },
        call = call,
    )

    /**
     * Public [AccountRepository] seam over [withAuth], so other backend transports (e.g. the
     * library-sync client) can make authenticated calls through the shared refresh-and-retry policy
     * without touching the token store or duplicating the retry logic.
     */
    override suspend fun <T> withBackendAuth(
        call: suspend (accessToken: String) -> AccountApiResult<T>,
    ): AccountApiResult<T> = withAuth(call)

    /**
     * Refreshes the cached user profile from the backend (`GetMe`), persisting any change. Best-effort:
     * returns the fresh [AccountUser] on success, null otherwise (offline, signed out, not configured).
     */
    override suspend fun refreshProfile(): AccountUser? {
        val result = withAuth { token -> client.getMe(token) }
        return when (result) {
            is AccountApiResult.Success -> {
                val user = result.value.toAccountUser()
                credentialStore.updateUser(user)
                user
            }
            else -> null
        }
    }

    /** Maps a successful auth RPC (register/login) into an [AuthResult], persisting the session. */
    private suspend fun <T> persistAuth(
        result: AccountApiResult<T>,
        extract: (T) -> Pair<UserDto, TokenPairDto>,
    ): AuthResult = when (result) {
        is AccountApiResult.Success -> {
            val (dtoUser, tokens) = extract(result.value)
            val user = dtoUser.toAccountUser()
            credentialStore.saveSession(
                user = user,
                accessToken = tokens.accessToken,
                refreshToken = tokens.refreshToken,
                accessExpiresAtMs = tokens.accessExpiresAtMs,
            )
            AuthResult.Success(user)
        }
        is AccountApiResult.Rejected -> AuthResult.Failed(result.message)
        AccountApiResult.Unauthenticated -> AuthResult.Failed("Invalid email or password")
        AccountApiResult.NetworkError -> AuthResult.NetworkError
        AccountApiResult.NotConfigured -> AuthResult.NotConfigured
    }

    private companion object {
        const val EXPIRY_SKEW_MS = 30_000L
    }
}

/** Maps a JSON [UserDto] into the domain [AccountUser], defaulting a blank display name. */
private fun UserDto.toAccountUser(): AccountUser = AccountUser(
    id = id,
    email = email,
    displayName = displayName.ifBlank { email.substringBefore('@') },
    createdAtMs = createdAtMs,
)
