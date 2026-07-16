package com.viperplayer.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore-backed persistence for the plugin update system: the last time the auto-checker ran
 * (to throttle it) and, per plugin, the versionCode the user last dismissed (to keep that exact
 * version quiet until a newer one appears).
 */
@Singleton
class PluginUpdatePreferences @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        private val Context.dataStore: DataStore<Preferences> by
            preferencesDataStore(name = "plugin_update_preferences")

        private val LAST_CHECK_KEY = longPreferencesKey("last_update_check_ms")
        private fun dismissedKey(pluginId: String) =
            longPreferencesKey("dismissed_version_$pluginId")
    }

    private val dataStore = context.dataStore

    /** Epoch-ms of the last completed auto-check, or 0 if never checked. */
    suspend fun lastCheckMs(): Long =
        dataStore.data.map { it[LAST_CHECK_KEY] ?: 0L }.first()

    /** Record that an auto-check just completed at [nowMs]. */
    suspend fun setLastCheckMs(nowMs: Long) {
        dataStore.edit { it[LAST_CHECK_KEY] = nowMs }
    }

    /** The versionCode the user dismissed for [pluginId], or null if none. */
    suspend fun dismissedVersion(pluginId: String): Long? =
        dataStore.data.map { it[dismissedKey(pluginId)] }.first()

    /** All dismissed-version entries keyed by plugin id (for evaluating many plugins at once). */
    suspend fun allDismissedVersions(): Map<String, Long> =
        dataStore.data.map { prefs ->
            prefs.asMap()
                .mapNotNull { (key, value) ->
                    val name = key.name
                    if (name.startsWith("dismissed_version_") && value is Long) {
                        name.removePrefix("dismissed_version_") to value
                    } else null
                }
                .toMap()
        }.first()

    /** Remember that the user dismissed [versionCode] for [pluginId]. */
    suspend fun setDismissedVersion(pluginId: String, versionCode: Long) {
        dataStore.edit { it[dismissedKey(pluginId)] = versionCode }
    }
}
