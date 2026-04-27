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
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages plugin enable/disable preferences using DataStore.
 * Plugins are enabled by default.
 */
@Singleton
class PluginPreferences @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "plugin_preferences")
        private fun disabledKey(pluginId: String) =
            booleanPreferencesKey("plugin_disabled_$pluginId")
    }

    private val dataStore = context.dataStore

    /**
     * Get disabled state for a plugin.
     * Defaults to false (enabled) if not set.
     */
    fun isDisabled(pluginId: String): Flow<Boolean> {
        Timber.d("Getting disabled state for plugin: $pluginId")
        return dataStore.data.map { preferences ->
            val disabled = preferences[disabledKey(pluginId)] ?: false // Default to enabled
            Timber.d(
                "Plugin $pluginId disabled state: $disabled (default: ${
                    preferences[disabledKey(
                        pluginId
                    )] == null
                })"
            )
            disabled
        }
    }

    /**
     * Get all disabled plugin IDs.
     */
    val disabledPlugins: Flow<Set<String>> = dataStore.data.map { preferences ->
        val disabledSet = preferences.asMap()
            .filterKeys { it.name.startsWith("plugin_disabled") }
            .filterValues { it as? Boolean ?: false }
            .keys.map { it.name.removePrefix("plugin_disabled") }
            .toSet()
        Timber.d("Disabled plugins: $disabledSet")
        disabledSet
    }

    /**
     * Set disabled state for a plugin.
     */
    suspend fun setDisabled(pluginId: String, disabled: Boolean) {
        Timber.d("Setting plugin $pluginId disabled state to: $disabled")
        try {
            dataStore.edit { preferences ->
                preferences[disabledKey(pluginId)] = disabled
            }
            Timber.d("Successfully set plugin $pluginId disabled state to: $disabled")
        } catch (e: Exception) {
            Timber.e(e, "Failed to set disabled state for plugin: $pluginId")
            throw e
        }
    }

    /**
     * Get disabled state synchronously (for initial load).
     */
    suspend fun isDisabledSync(pluginId: String): Boolean {
        Timber.d("Getting disabled state synchronously for plugin: $pluginId")
        return try {
            val disabled = dataStore.data.map { preferences ->
                preferences[disabledKey(pluginId)] ?: false
            }.first()
            Timber.d("Plugin $pluginId disabled state (sync): $disabled")
            disabled
        } catch (e: Exception) {
            Timber.e(e, "Failed to get disabled state for plugin: $pluginId")
            false // Default to enabled on error
        }
    }
}

