package com.viperplayer.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-plugin toggle for PUSHING local library changes up to that plugin's account (the write
 * direction of two-way sync). Off by default so nothing is written to a user's streaming account
 * until they opt in per plugin. Backed by DataStore, mirroring [PluginPreferences].
 *
 * When push is disabled for a plugin, local mutations for it are simply not enqueued into the outbox
 * (the local library still changes; it just isn't propagated remotely).
 */
@Singleton
class PushSyncPreferences @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private companion object {
        val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "push_sync_preferences")
        fun enabledKey(pluginId: String) = booleanPreferencesKey("push_sync_enabled_$pluginId")
    }

    private val dataStore = context.dataStore

    /** Whether push sync is enabled for [pluginId] (defaults to false — opt-in). */
    fun isEnabled(pluginId: String): Flow<Boolean> =
        dataStore.data.map { it[enabledKey(pluginId)] ?: false }

    /** All plugin ids the user has enabled push sync for. */
    val enabledPlugins: Flow<Set<String>> = dataStore.data.map { prefs ->
        prefs.asMap()
            .filterKeys { it.name.startsWith("push_sync_enabled_") }
            .filterValues { it as? Boolean ?: false }
            .keys.map { it.name.removePrefix("push_sync_enabled_") }
            .toSet()
    }

    /** Synchronous read for the enqueue path (defaults to disabled on any error). */
    suspend fun isEnabledSync(pluginId: String): Boolean =
        runCatching { dataStore.data.map { it[enabledKey(pluginId)] ?: false }.first() }
            .getOrDefault(false)

    suspend fun setEnabled(pluginId: String, enabled: Boolean) {
        dataStore.edit { it[enabledKey(pluginId)] = enabled }
    }
}
