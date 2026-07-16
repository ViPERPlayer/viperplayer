package com.viperplayer.data.lastfm

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.viperplayer.domain.lastfm.LastfmSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the Last.fm login + preferences in a dedicated Preferences DataStore ("lastfm").
 *
 * SECURITY: only the returned SESSION KEY is stored (plus the username and the boolean toggles). The
 * user's PASSWORD is never written here — it is used transiently for `auth.getMobileSession` and then
 * discarded (see [LastfmRepositoryImpl.login]). The session key is returned to callers that need it
 * for signing requests but is never exposed in UI state and never logged.
 */
@Singleton
class LastfmCredentialStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val dataStore = context.lastfmDataStore

    /** The public settings snapshot — deliberately excludes the raw session key. */
    val settings: Flow<LastfmSettings> = dataStore.data.map { prefs ->
        val sessionKey = prefs[SESSION_KEY]
        LastfmSettings(
            username = prefs[USERNAME_KEY],
            isAuthenticated = !sessionKey.isNullOrBlank(),
            scrobblingEnabled = prefs[SCROBBLING_ENABLED_KEY] ?: true,
            nowPlayingEnabled = prefs[NOW_PLAYING_ENABLED_KEY] ?: true,
            scrobbleOnWifiOnly = prefs[WIFI_ONLY_KEY] ?: false,
        )
    }.distinctUntilChanged()

    /**
     * A single-read snapshot of the settings + the raw session key, so callers that need both (the
     * scrobble/now-playing paths) don't collect the DataStore twice. The session key is on
     * [CredentialSnapshot], never on the public [LastfmSettings], and must not be logged.
     */
    suspend fun snapshot(): CredentialSnapshot {
        val prefs = dataStore.data.first()
        val sessionKey = prefs[SESSION_KEY]?.takeIf { it.isNotBlank() }
        return CredentialSnapshot(
            settings = LastfmSettings(
                username = prefs[USERNAME_KEY],
                isAuthenticated = sessionKey != null,
                scrobblingEnabled = prefs[SCROBBLING_ENABLED_KEY] ?: true,
                nowPlayingEnabled = prefs[NOW_PLAYING_ENABLED_KEY] ?: true,
                scrobbleOnWifiOnly = prefs[WIFI_ONLY_KEY] ?: false,
            ),
            sessionKey = sessionKey,
        )
    }

    /** Persists the session key + username returned by a successful login. */
    suspend fun saveSession(username: String, sessionKey: String) {
        dataStore.edit { prefs ->
            prefs[USERNAME_KEY] = username
            prefs[SESSION_KEY] = sessionKey
        }
    }

    /** Clears the stored login (session key + username), keeping the toggle preferences. */
    suspend fun clearSession() {
        dataStore.edit { prefs ->
            prefs.remove(USERNAME_KEY)
            prefs.remove(SESSION_KEY)
        }
    }

    suspend fun setScrobblingEnabled(enabled: Boolean) {
        dataStore.edit { it[SCROBBLING_ENABLED_KEY] = enabled }
    }

    suspend fun setNowPlayingEnabled(enabled: Boolean) {
        dataStore.edit { it[NOW_PLAYING_ENABLED_KEY] = enabled }
    }

    suspend fun setScrobbleOnWifiOnly(enabled: Boolean) {
        dataStore.edit { it[WIFI_ONLY_KEY] = enabled }
    }

    private companion object {
        val Context.lastfmDataStore: DataStore<Preferences> by preferencesDataStore(name = "lastfm")

        val USERNAME_KEY = stringPreferencesKey("lastfm_username")
        val SESSION_KEY = stringPreferencesKey("lastfm_session_key")
        val SCROBBLING_ENABLED_KEY = booleanPreferencesKey("lastfm_scrobbling_enabled")
        val NOW_PLAYING_ENABLED_KEY = booleanPreferencesKey("lastfm_now_playing_enabled")
        val WIFI_ONLY_KEY = booleanPreferencesKey("lastfm_scrobble_wifi_only")
    }
}

/**
 * A one-read view of the credential store for the send paths: the public [settings] plus the raw
 * [sessionKey] (null when not authenticated). The session key must never be logged or surfaced in UI.
 */
data class CredentialSnapshot(
    val settings: LastfmSettings,
    val sessionKey: String?,
)
