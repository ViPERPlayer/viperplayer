package com.viperplayer.data.social

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Supplies a stable per-install device id. An interface so the repository is trivially fakeable. */
interface DeviceIdProvider {
    suspend fun deviceId(): String
}

/**
 * A stable, per-install device identifier for the Jam session backend.
 *
 * A random [UUID] is generated once and persisted in a dedicated Preferences DataStore, then reused
 * for every session. This is deliberately NOT a hardware ID (ANDROID_ID / IMEI) — it is an opaque
 * app-scoped value that resets on reinstall/clear-data, which is exactly what the backend keys a
 * member on.
 */
@Singleton
class DeviceIdStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : DeviceIdProvider {
    private val dataStore = context.deviceIdDataStore

    /** Returns the persisted device id, generating and storing one on first use. */
    override suspend fun deviceId(): String {
        val existing = dataStore.data.first()[DEVICE_ID_KEY]
        if (!existing.isNullOrBlank()) return existing
        val generated = UUID.randomUUID().toString()
        dataStore.edit { prefs ->
            // Re-check inside the transaction in case a concurrent caller wrote first.
            val raced = prefs[DEVICE_ID_KEY]
            if (raced.isNullOrBlank()) prefs[DEVICE_ID_KEY] = generated
        }
        return dataStore.data.first()[DEVICE_ID_KEY] ?: generated
    }

    private companion object {
        val Context.deviceIdDataStore: DataStore<Preferences> by preferencesDataStore(name = "jam_device")
        val DEVICE_ID_KEY = stringPreferencesKey("jam_device_id")
    }
}
