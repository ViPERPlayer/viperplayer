package com.viperplayer.data.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.viperplayer.domain.sync.LibrarySyncStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the outcome of the most recent LOCAL library sync (the plugin-account → on-device pull run
 * by [LibrarySyncManager]) in a dedicated DataStore ("library_sync_status"): when it last finished and
 * the aggregate per-type counts pulled. Backed by DataStore, mirroring the other preference stores.
 *
 * A single "Sync now" iterates every connected plugin, each producing a [SyncResult] and calling
 * [record]. Records within the same run (their [atMs] falling inside [RUN_WINDOW_MS] of the run's
 * START timestamp) ACCUMULATE, so the status reflects the whole run's totals; a record starting a
 * new run (an [atMs] beyond that window of the run start) RESETS the counts. Anchoring on the run
 * start — not the last record — means a second "Sync now" fired soon after the previous one starts
 * fresh instead of piling onto it.
 *
 * The primary constructor takes a [DataStore] directly so unit tests can inject an in-memory one
 * (PreferenceDataStoreFactory) and round-trip on the JVM without Android.
 */
@Singleton
class LibrarySyncStatusStore(
    private val dataStore: DataStore<Preferences>,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context.librarySyncStatusDataStore)

    /** The last-sync status; emits [LibrarySyncStatus] with a 0 timestamp until the first sync. */
    val status: Flow<LibrarySyncStatus> = dataStore.data.map { prefs ->
        LibrarySyncStatus(
            lastSyncedAtMs = prefs[LAST_SYNCED_AT_KEY] ?: 0L,
            songs = prefs[SONGS_KEY] ?: 0,
            albums = prefs[ALBUMS_KEY] ?: 0,
            artists = prefs[ARTISTS_KEY] ?: 0,
            playlists = prefs[PLAYLISTS_KEY] ?: 0,
        )
    }

    /**
     * Records the outcome of one plugin's sync at [atMs]. Counts accumulate with any record from the
     * same run — one whose [atMs] is within [RUN_WINDOW_MS] of the run's START; a record beyond that
     * window opens a new run and starts fresh totals (anchored on the run start, not the last record,
     * so back-to-back runs don't pile onto each other).
     */
    suspend fun record(result: SyncResult, atMs: Long) {
        dataStore.edit { prefs ->
            val runStartedAt = prefs[RUN_STARTED_AT_KEY] ?: 0L
            val sameRun = runStartedAt != 0L && atMs - runStartedAt in 0..RUN_WINDOW_MS
            val baseSongs = if (sameRun) prefs[SONGS_KEY] ?: 0 else 0
            val baseAlbums = if (sameRun) prefs[ALBUMS_KEY] ?: 0 else 0
            val baseArtists = if (sameRun) prefs[ARTISTS_KEY] ?: 0 else 0
            val basePlaylists = if (sameRun) prefs[PLAYLISTS_KEY] ?: 0 else 0
            if (!sameRun) prefs[RUN_STARTED_AT_KEY] = atMs
            prefs[LAST_SYNCED_AT_KEY] = atMs
            prefs[SONGS_KEY] = baseSongs + result.songs
            prefs[ALBUMS_KEY] = baseAlbums + result.albums
            prefs[ARTISTS_KEY] = baseArtists + result.artists
            prefs[PLAYLISTS_KEY] = basePlaylists + result.playlists
        }
    }

    private companion object {
        val Context.librarySyncStatusDataStore: DataStore<Preferences>
            by preferencesDataStore(name = "library_sync_status")

        /** Records whose timestamps fall within this window of the stored one are the same sync run. */
        const val RUN_WINDOW_MS = 5 * 60 * 1000L

        val LAST_SYNCED_AT_KEY = longPreferencesKey("library_sync_last_synced_at_ms")
        val RUN_STARTED_AT_KEY = longPreferencesKey("library_sync_run_started_at_ms")
        val SONGS_KEY = intPreferencesKey("library_sync_songs")
        val ALBUMS_KEY = intPreferencesKey("library_sync_albums")
        val ARTISTS_KEY = intPreferencesKey("library_sync_artists")
        val PLAYLISTS_KEY = intPreferencesKey("library_sync_playlists")
    }
}
