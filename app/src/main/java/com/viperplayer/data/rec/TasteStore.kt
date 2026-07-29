package com.viperplayer.data.rec

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The persistence seam for the taste model: reads/writes a single opaque JSON blob. Abstracted behind
 * an interface so [TasteRepositoryImpl]'s learning/serialization logic is unit-testable with an
 * in-memory fake (no Android DataStore / Context needed in tests).
 */
interface TasteStore {
    /** The stored blob, or null when nothing has been persisted yet. Never throws. */
    suspend fun read(): String?

    /** Persists [blob], replacing any previous value. Never throws. */
    suspend fun write(blob: String)
}

/** DataStore-backed [TasteStore] — one string blob under a preferences key. */
@Singleton
class DataStoreTasteStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : TasteStore {

    override suspend fun read(): String? =
        runCatching { context.dataStore.data.first()[BLOB_KEY] }
            .onFailure { Timber.w(it, "TasteStore: read failed") }
            .getOrNull()

    override suspend fun write(blob: String) {
        runCatching { context.dataStore.edit { it[BLOB_KEY] = blob } }
            .onFailure { Timber.w(it, "TasteStore: write failed") }
    }

    private companion object {
        val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "taste_model")
        val BLOB_KEY = stringPreferencesKey("taste_snapshot")
    }
}
