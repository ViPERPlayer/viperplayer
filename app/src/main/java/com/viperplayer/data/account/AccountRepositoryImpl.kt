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
 * Default [AccountRepository]: stitches the [AccountApi] (network) and the
 * [AccountCredentialStore] (token persistence). The password is used transiently
 * for register/login and never stored; only the returned tokens are persisted.
 */
@Singleton
class AccountRepositoryImpl @Inject constructor(
    private val api: AccountApi,
    private val credentialStore: AccountCredentialStore,
) : AccountRepository {

    override val state: Flow<AccountState> = credentialStore.state

    override val isConfigured: Boolean get() = api.isConfigured

    override suspend fun register(email: String, password: String, displayName: String?): AuthResult =
        persistAuth(api.register(email.trim(), password, displayName?.trim()))

    override suspend fun login(email: String, password: String): AuthResult =
        persistAuth(api.login(email.trim(), password))

    override suspend fun logout() {
        val snapshot = credentialStore.snapshot()
        snapshot.refreshToken?.let { api.logout(it) }
        credentialStore.clear()
    }

    override suspend fun validAccessToken(): String? {
        val snapshot = credentialStore.snapshot()
        val access = snapshot.accessToken ?: return null
        // Refresh a little early (30s skew) to avoid handing out a token that expires mid-flight.
        if (snapshot.accessExpiresAtMs - System.currentTimeMillis() > EXPIRY_SKEW_MS) {
            return access
        }
        val refresh = snapshot.refreshToken ?: return null
        return when (val result = api.refresh(refresh)) {
            is AccountApiResult.Success -> {
                credentialStore.updateTokens(
                    accessToken = result.value.accessToken,
                    refreshToken = result.value.refreshToken,
                    accessExpiresAtMs = result.value.accessExpiresAtMs,
                )
                result.value.accessToken
            }
            is AccountApiResult.Rejected -> {
                // Refresh token rejected/expired → treat as signed out.
                Timber.w("Token refresh rejected (${result.status}); clearing session")
                credentialStore.clear()
                null
            }
            AccountApiResult.NetworkError, AccountApiResult.NotConfigured -> {
                // Transient: keep the (possibly-expired) session; caller retries later.
                null
            }
        }
    }

    /** Maps an [AccountApiResult] carrying auth tokens into an [AuthResult], persisting on success. */
    private suspend fun persistAuth(result: AccountApiResult<AuthDto>): AuthResult = when (result) {
        is AccountApiResult.Success -> {
            val dto = result.value
            val user = AccountUser(
                id = dto.user.id,
                email = dto.user.email,
                displayName = dto.user.displayName.ifBlank { dto.user.email.substringBefore('@') },
            )
            credentialStore.saveSession(
                user = user,
                accessToken = dto.tokens.accessToken,
                refreshToken = dto.tokens.refreshToken,
                accessExpiresAtMs = dto.tokens.accessExpiresAtMs,
            )
            AuthResult.Success(user)
        }
        is AccountApiResult.Rejected -> AuthResult.Failed(result.message)
        AccountApiResult.NetworkError -> AuthResult.NetworkError
        AccountApiResult.NotConfigured -> AuthResult.NotConfigured
    }

    private companion object {
        const val EXPIRY_SKEW_MS = 30_000L
    }
}
