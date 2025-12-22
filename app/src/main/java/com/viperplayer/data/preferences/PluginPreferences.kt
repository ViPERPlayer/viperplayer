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
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "PluginPreferences"
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "plugin_preferences")
        private fun enabledKey(pluginId: String) = booleanPreferencesKey("plugin_enabled_$pluginId")
    }
    
    private val dataStore = context.dataStore
    
    /**
     * Get enabled state for a plugin.
     * Defaults to true (enabled) if not set.
     */
    fun isEnabled(pluginId: String): Flow<Boolean> {
        Timber.d("[$TAG] Getting enabled state for plugin: $pluginId")
        return dataStore.data.map { preferences ->
            val enabled = preferences[enabledKey(pluginId)] ?: true // Default to enabled
            Timber.d("[$TAG] Plugin $pluginId enabled state: $enabled (default: ${preferences[enabledKey(pluginId)] == null})")
            enabled
        }
    }
    
    /**
     * Get all enabled plugin IDs.
     */
    val enabledPlugins: Flow<Set<String>> = dataStore.data.map { preferences ->
        val enabledSet = preferences.asMap()
            .filterKeys { it.name.startsWith("plugin_enabled_") }
            .filterValues { it as? Boolean ?: true }
            .keys.map { it.name.removePrefix("plugin_enabled_") }
            .toSet()
        Timber.d("[$TAG] Enabled plugins: $enabledSet")
        enabledSet
    }
    
    /**
     * Set enabled state for a plugin.
     */
    suspend fun setEnabled(pluginId: String, enabled: Boolean) {
        Timber.d("[$TAG] Setting plugin $pluginId enabled state to: $enabled")
        try {
            dataStore.edit { preferences ->
                preferences[enabledKey(pluginId)] = enabled
            }
            Timber.d("[$TAG] Successfully set plugin $pluginId enabled state to: $enabled")
        } catch (e: Exception) {
            Timber.e(e, "[$TAG] Failed to set enabled state for plugin: $pluginId")
            throw e
        }
    }
    
    /**
     * Get enabled state synchronously (for initial load).
     */
    suspend fun isEnabledSync(pluginId: String): Boolean {
        Timber.d("[$TAG] Getting enabled state synchronously for plugin: $pluginId")
        return try {
            val enabled = dataStore.data.map { preferences ->
                preferences[enabledKey(pluginId)] ?: true
            }.first()
            Timber.d("[$TAG] Plugin $pluginId enabled state (sync): $enabled")
            enabled
        } catch (e: Exception) {
            Timber.e(e, "[$TAG] Failed to get enabled state for plugin: $pluginId")
            true // Default to enabled on error
        }
    }
}

