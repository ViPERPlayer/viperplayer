package com.viperplayer.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * User toggle for the LOCAL library sync (pulling each connected plugin's account library into the
 * on-device library). Enabled by default — a signed-in user expects their sources to stay in sync;
 * the toggle lets them pause it. Backed by DataStore, mirroring [PushSyncPreferences].
 *
 * When disabled, the "Sync now" action is a no-op (nothing is pulled).
 *
 * The primary constructor takes a [DataStore] directly so unit tests can inject an in-memory one
 * (PreferenceDataStoreFactory) and round-trip on the JVM without Android.
 */
@Singleton
class LibrarySyncPreferences(
    private val dataStore: DataStore<Preferences>,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context.librarySyncDataStore)

    /** Whether local library sync is enabled (defaults to true — opt-out). */
    val isEnabled: Flow<Boolean> = dataStore.data.map { it[ENABLED_KEY] ?: true }

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[ENABLED_KEY] = enabled }
    }

    private companion object {
        val Context.librarySyncDataStore: DataStore<Preferences>
            by preferencesDataStore(name = "library_sync_preferences")
        val ENABLED_KEY = booleanPreferencesKey("sync_enabled")
    }
}
