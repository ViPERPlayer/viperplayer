package com.viperplayer.data.player.resumption

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tiny, self-contained store for the last playback session used by media resumption (Android 11+
 * resume card). Backed by its **own** Preferences DataStore — deliberately *not* the shared
 * [com.viperplayer.domain.repository.SettingsRepository] nor
 * [com.viperplayer.data.player.PlayerStatePersistence], so resumption state stays decoupled and can
 * be read on the framework's resumption path without pulling in the queue-in-Room machinery.
 *
 * All logic beyond I/O lives in the pure [LastSessionCodec]; this class only reads/writes a single
 * JSON string on [Dispatchers.IO].
 */
@Singleton
class LastSessionStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    companion object {
        private val Context.lastSessionDataStore: DataStore<Preferences> by preferencesDataStore(
            name = "last_session"
        )
        private val LAST_SESSION_JSON_KEY = stringPreferencesKey("last_session_json")
    }

    private val dataStore = context.lastSessionDataStore

    /** Persists [session] as JSON on a background dispatcher. Swallows failures (best-effort). */
    suspend fun save(session: LastSession) = withContext(Dispatchers.IO) {
        try {
            val encoded = LastSessionCodec.encode(session)
            dataStore.edit { it[LAST_SESSION_JSON_KEY] = encoded }
            Timber.d("LastSessionStore: saved ${session.items.size} items, index=${session.currentIndex}, pos=${session.positionMs}ms")
        } catch (e: Exception) {
            Timber.e(e, "LastSessionStore: failed to save last session")
        }
    }

    /**
     * Reads the last session, or `null` when absent/malformed. Never throws — malformed stored data
     * degrades to `null` via [LastSessionCodec.decode].
     */
    suspend fun load(): LastSession? = withContext(Dispatchers.IO) {
        try {
            val stored = dataStore.data.first()[LAST_SESSION_JSON_KEY]
            LastSessionCodec.decode(stored)
        } catch (e: Exception) {
            Timber.e(e, "LastSessionStore: failed to load last session")
            null
        }
    }

    /** Clears the persisted session (e.g. when the queue is emptied). */
    suspend fun clear() = withContext(Dispatchers.IO) {
        try {
            dataStore.edit { it.remove(LAST_SESSION_JSON_KEY) }
        } catch (e: Exception) {
            Timber.e(e, "LastSessionStore: failed to clear last session")
        }
    }
}
