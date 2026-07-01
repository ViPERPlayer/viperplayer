package com.viperplayer.data.player

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.viperplayer.data.local.dao.CrossRefDao
import com.viperplayer.data.local.dao.SongDao
import com.viperplayer.data.local.entity.QueueSongCrossRef
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.RepeatMode
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.repository.MediaLibraryRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persisted player state that can be restored on app restart.
 * Queue is stored in Room (via QueueSongCrossRef), simple settings in DataStore.
 * Songs are loaded from database on restoration.
 */
data class PersistedPlayerState(
    val currentSongMediaId: String? = null,
    val currentPositionMs: Long = 0L,
    val queuePosition: Int = 0,
    val shuffleEnabled: Boolean = false,
    val repeatMode: String = RepeatMode.OFF.name,
)

/**
 * Manages persistence and restoration of player state.
 * Uses Room for queue storage (via QueueSongCrossRef referencing SongEntity) and DataStore for simple settings.
 */
@Singleton
class PlayerStatePersistence @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val crossRefDao: CrossRefDao,
    private val songDao: SongDao,
    private val mediaLibraryRepository: MediaLibraryRepository
) {
    companion object {
        private val Context.playerStateDataStore: DataStore<Preferences> by preferencesDataStore(
            name = "player_state"
        )

        private val CURRENT_SONG_MEDIA_ID_KEY = stringPreferencesKey("current_song_media_id")
        private val CURRENT_POSITION_MS_KEY = longPreferencesKey("current_position_ms")
        private val QUEUE_POSITION_KEY = intPreferencesKey("queue_position")
        private val SHUFFLE_ENABLED_KEY = booleanPreferencesKey("shuffle_enabled")
        private val REPEAT_MODE_KEY = stringPreferencesKey("repeat_mode")
    }

    private val dataStore = context.playerStateDataStore

    /**
     * Saves the current player state.
     * Queue is saved to Room (songs must already exist in SongEntity), simple settings to DataStore.
     * Only MediaIds are saved - full song data is loaded from database on restoration.
     */
    suspend fun saveState(state: PersistedPlayerState, queue: List<MediaId>) =
        withContext(Dispatchers.IO) {
            try {
                // Clear existing queue
                crossRefDao.clearQueue()

                // Save queue to Room - songs should already be saved via MediaLibraryRepository.saveSong()
                // when they were played or added to queue. We persist only the ordered ids (straight from
                // the controller), never hydrated Songs.
                if (queue.isNotEmpty()) {
                    val queueCrossRefs = queue.mapIndexedNotNull { index, mediaId ->
                        try {
                            // Get song entity (should already exist from play/addToQueue)
                            val songEntity =
                                songDao.getByMediaId(mediaId.pluginId, mediaId.sourceId)

                            if (songEntity == null) {
                                Timber.w("Song not found in database when saving queue: $mediaId. It should have been saved when played/added to queue.")
                                return@mapIndexedNotNull null
                            }

                            QueueSongCrossRef(
                                songId = songEntity.id,
                                position = index
                            )
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to create queue cross-ref for song: $mediaId")
                            null
                        }
                    }

                    if (queueCrossRefs.isNotEmpty()) {
                        crossRefDao.insertQueueSongs(queueCrossRefs)
                    }
                }

                // Save simple settings to DataStore
                dataStore.edit { preferences ->
                    preferences[CURRENT_SONG_MEDIA_ID_KEY] = state.currentSongMediaId ?: ""
                    preferences[CURRENT_POSITION_MS_KEY] = state.currentPositionMs
                    preferences[QUEUE_POSITION_KEY] = state.queuePosition
                    preferences[SHUFFLE_ENABLED_KEY] = state.shuffleEnabled
                    preferences[REPEAT_MODE_KEY] = state.repeatMode
                }
                Timber.d("PlayerStatePersistence: Saved state - song=${state.currentSongMediaId}, position=${state.currentPositionMs}ms, queueSize=${queue.size}")
            } catch (e: Exception) {
                Timber.e(e, "Failed to save player state")
            }
        }

    /**
     * Loads the persisted player state.
     * Queue is loaded from Room (via QueueSongCrossRef -> SongEntity), simple settings from DataStore.
     * Returns the state and the full queue of Song objects loaded from database.
     */
    suspend fun loadState(): Pair<PersistedPlayerState?, List<Song>> = withContext(Dispatchers.IO) {
        try {
            val preferences = dataStore.data.first()
            val currentSongMediaId =
                preferences[CURRENT_SONG_MEDIA_ID_KEY]?.takeIf { it.isNotEmpty() }

            if (currentSongMediaId == null) {
                Timber.d("PlayerStatePersistence: No saved state found")
                return@withContext Pair(null, emptyList())
            }

            // Load queue from Room via cross-refs - get full Song objects from database
            val queueSongIds = crossRefDao.getSongIdsForQueue()
            val queue = queueSongIds.mapNotNull { songId ->
                val songEntity = songDao.getByIdSync(songId) ?: return@mapNotNull null
                val mediaId = MediaId(songEntity.pluginId, songEntity.sourceId)
                // Load full song from MediaLibraryRepository (includes artists, album, etc.)
                mediaLibraryRepository.getSong(mediaId).first()
            }

            val state = PersistedPlayerState(
                currentSongMediaId = currentSongMediaId,
                currentPositionMs = preferences[CURRENT_POSITION_MS_KEY] ?: 0L,
                queuePosition = preferences[QUEUE_POSITION_KEY] ?: 0,
                shuffleEnabled = preferences[SHUFFLE_ENABLED_KEY] ?: false,
                repeatMode = preferences[REPEAT_MODE_KEY] ?: RepeatMode.OFF.name,
            )

            Timber.d("PlayerStatePersistence: Loaded state - song=$currentSongMediaId, position=${state.currentPositionMs}ms, queueSize=${queue.size}")
            return@withContext Pair(state, queue)
        } catch (e: Exception) {
            Timber.e(e, "Failed to load player state")
            return@withContext Pair(null, emptyList())
        }
    }
}

