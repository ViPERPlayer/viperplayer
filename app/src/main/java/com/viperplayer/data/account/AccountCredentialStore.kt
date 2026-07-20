package com.viperplayer.data.account

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.viperplayer.domain.account.AccountState
import com.viperplayer.domain.account.AccountUser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the ViPER account session in a dedicated Preferences DataStore ("account").
 *
 * SECURITY: the access + refresh TOKENS are stored here but are never exposed in the public
 * [state] flow, never surfaced in UI, and never logged. The user's PASSWORD is never written — it
 * is used transiently for register/login and then discarded (see [AccountRepositoryImpl]).
 */
@Singleton
class AccountCredentialStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val dataStore = context.accountDataStore

    /** The public account snapshot — deliberately excludes the raw tokens. */
    val state: Flow<AccountState> = dataStore.data.map { prefs ->
        toState(prefs)
    }.distinctUntilChanged()

    /** A single-read snapshot including the raw tokens, for the authenticated send paths. */
    suspend fun snapshot(): AccountSnapshot {
        val prefs = dataStore.data.first()
        return AccountSnapshot(
            state = toState(prefs),
            accessToken = prefs[ACCESS_TOKEN_KEY]?.takeIf { it.isNotBlank() },
            refreshToken = prefs[REFRESH_TOKEN_KEY]?.takeIf { it.isNotBlank() },
            accessExpiresAtMs = prefs[ACCESS_EXPIRES_AT_KEY] ?: 0L,
        )
    }

    /** Persists the session returned by a successful register/login. */
    suspend fun saveSession(
        user: AccountUser,
        accessToken: String,
        refreshToken: String,
        accessExpiresAtMs: Long,
    ) {
        dataStore.edit { prefs ->
            prefs[USER_ID_KEY] = user.id
            prefs[EMAIL_KEY] = user.email
            prefs[DISPLAY_NAME_KEY] = user.displayName
            prefs[CREATED_AT_KEY] = user.createdAtMs
            prefs[ACCESS_TOKEN_KEY] = accessToken
            prefs[REFRESH_TOKEN_KEY] = refreshToken
            prefs[ACCESS_EXPIRES_AT_KEY] = accessExpiresAtMs
        }
    }

    /** Updates just the tokens after a refresh, keeping the user identity. */
    suspend fun updateTokens(accessToken: String, refreshToken: String, accessExpiresAtMs: Long) {
        dataStore.edit { prefs ->
            prefs[ACCESS_TOKEN_KEY] = accessToken
            prefs[REFRESH_TOKEN_KEY] = refreshToken
            prefs[ACCESS_EXPIRES_AT_KEY] = accessExpiresAtMs
        }
    }

    /** Updates just the stored user profile after a whoami/GetMe refresh, keeping the tokens. */
    suspend fun updateUser(user: AccountUser) {
        dataStore.edit { prefs ->
            prefs[USER_ID_KEY] = user.id
            prefs[EMAIL_KEY] = user.email
            prefs[DISPLAY_NAME_KEY] = user.displayName
            prefs[CREATED_AT_KEY] = user.createdAtMs
        }
    }

    /** Clears the entire stored session (logout). */
    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private fun toState(prefs: Preferences): AccountState {
        val userId = prefs[USER_ID_KEY]
        val hasSession = !userId.isNullOrBlank() && !prefs[REFRESH_TOKEN_KEY].isNullOrBlank()
        return AccountState(
            user = if (userId.isNullOrBlank()) null else AccountUser(
                id = userId,
                email = prefs[EMAIL_KEY].orEmpty(),
                displayName = prefs[DISPLAY_NAME_KEY].orEmpty(),
                createdAtMs = prefs[CREATED_AT_KEY] ?: 0L,
            ),
            isSignedIn = hasSession,
        )
    }

    private companion object {
        val Context.accountDataStore: DataStore<Preferences> by preferencesDataStore(name = "account")

        val USER_ID_KEY = stringPreferencesKey("account_user_id")
        val EMAIL_KEY = stringPreferencesKey("account_email")
        val DISPLAY_NAME_KEY = stringPreferencesKey("account_display_name")
        val CREATED_AT_KEY = longPreferencesKey("account_created_at_ms")
        val ACCESS_TOKEN_KEY = stringPreferencesKey("account_access_token")
        val REFRESH_TOKEN_KEY = stringPreferencesKey("account_refresh_token")
        val ACCESS_EXPIRES_AT_KEY = longPreferencesKey("account_access_expires_at_ms")
    }
}

/**
 * A one-read view of the credential store for authenticated paths: the public [state] plus the raw
 * tokens (null when signed out). The tokens must never be logged or surfaced in UI.
 */
data class AccountSnapshot(
    val state: AccountState,
    val accessToken: String?,
    val refreshToken: String?,
    val accessExpiresAtMs: Long,
)
