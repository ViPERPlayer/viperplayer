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
import com.viperplayer.data.local.entity.SongEntity
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.RepeatMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Minimal song metadata for persistence (no network calls needed for restoration).
 * This is used as an intermediate format when saving/loading state.
 */
data class PersistedSongMetadata(
    val mediaId: String,
    val title: String,
    val artistName: String? = null,
    val albumName: String? = null,
    val artworkUrl: String? = null,
    val durationMs: Long? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null
)

/**
 * Persisted player state that can be restored on app restart.
 * Queue is stored in Room, simple settings in DataStore.
 */
data class PersistedPlayerState(
    val currentSong: PersistedSongMetadata? = null,
    val currentPositionMs: Long = 0L,
    val queue: List<PersistedSongMetadata> = emptyList(),
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
    private val songDao: SongDao
) {
    companion object {
        private val Context.playerStateDataStore: DataStore<Preferences> by preferencesDataStore(name = "player_state")
        
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
     */
    suspend fun saveState(state: PersistedPlayerState) = withContext(Dispatchers.IO) {
        try {
            // Clear existing queue
            crossRefDao.clearQueue()
            
            // Save queue to Room - ensure songs exist in SongEntity first
            if (state.queue.isNotEmpty()) {
                val queueCrossRefs = state.queue.mapIndexedNotNull { index, songMetadata ->
                    try {
                        val mediaId = MediaId.fromString(songMetadata.mediaId)
                        // Get or create song entity
                        var songEntity = songDao.getByMediaId(mediaId.pluginId, mediaId.sourceId)
                        if (songEntity == null) {
                            // Song doesn't exist, create minimal entity
                            val newEntity = SongEntity(
                                pluginId = mediaId.pluginId,
                                sourceId = mediaId.sourceId,
                                title = songMetadata.title,
                                durationMs = songMetadata.durationMs,
                                artworkUrl = songMetadata.artworkUrl,
                                trackNumber = songMetadata.trackNumber,
                                discNumber = songMetadata.discNumber
                            )
                            songDao.insert(newEntity)
                            songEntity = songDao.getByMediaId(mediaId.pluginId, mediaId.sourceId)
                        }
                        
                        songEntity?.let {
                            QueueSongCrossRef(
                                songId = it.id,
                                position = index
                            )
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to create queue cross-ref for song: ${songMetadata.title}")
                        null
                    }
                }
                
                if (queueCrossRefs.isNotEmpty()) {
                    crossRefDao.insertQueueSongs(queueCrossRefs)
                }
            }
            
            // Save simple settings to DataStore
            dataStore.edit { preferences ->
                preferences[CURRENT_SONG_MEDIA_ID_KEY] = state.currentSong?.mediaId ?: ""
                preferences[CURRENT_POSITION_MS_KEY] = state.currentPositionMs
                preferences[QUEUE_POSITION_KEY] = state.queuePosition
                preferences[SHUFFLE_ENABLED_KEY] = state.shuffleEnabled
                preferences[REPEAT_MODE_KEY] = state.repeatMode
            }
            Timber.d("PlayerStatePersistence: Saved state - song=${state.currentSong?.title}, position=${state.currentPositionMs}ms, queueSize=${state.queue.size}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to save player state")
        }
    }
    
    /**
     * Loads the persisted player state.
     * Queue is loaded from Room (via QueueSongCrossRef -> SongEntity), simple settings from DataStore.
     */
    suspend fun loadState(): PersistedPlayerState? = withContext(Dispatchers.IO) {
        try {
            val preferences = dataStore.data.first()
            val currentSongMediaId = preferences[CURRENT_SONG_MEDIA_ID_KEY]?.takeIf { it.isNotEmpty() }
            
            if (currentSongMediaId == null) {
                Timber.d("PlayerStatePersistence: No saved state found")
                return@withContext null
            }
            
            // Load queue from Room via cross-refs
            val queueSongIds = crossRefDao.getSongIdsForQueue()
            val queue = queueSongIds.mapNotNull { songId ->
                val songEntity = songDao.getByIdSync(songId)
                songEntity?.let {
                    PersistedSongMetadata(
                        mediaId = "${it.pluginId}:${it.sourceId}",
                        title = it.title,
                        artistName = null, // Will be loaded when needed from cross-refs
                        albumName = null, // Will be loaded when needed from album
                        artworkUrl = it.artworkUrl,
                        durationMs = it.durationMs,
                        trackNumber = it.trackNumber,
                        discNumber = it.discNumber
                    )
                }
            }
            
            // Find current song from queue based on mediaId
            val currentSong = queue.find { it.mediaId == currentSongMediaId }
            
            val state = PersistedPlayerState(
                currentSong = currentSong,
                currentPositionMs = preferences[CURRENT_POSITION_MS_KEY] ?: 0L,
                queue = queue,
                queuePosition = preferences[QUEUE_POSITION_KEY] ?: 0,
                shuffleEnabled = preferences[SHUFFLE_ENABLED_KEY] ?: false,
                repeatMode = preferences[REPEAT_MODE_KEY] ?: RepeatMode.OFF.name,
            )
            
            Timber.d("PlayerStatePersistence: Loaded state - song=${currentSong?.title}, position=${state.currentPositionMs}ms, queueSize=${queue.size}")
            return@withContext state
        } catch (e: Exception) {
            Timber.e(e, "Failed to load player state")
            return@withContext null
        }
    }
    
    /**
     * Clears the persisted state.
     */
    suspend fun clearState() = withContext(Dispatchers.IO) {
        try {
            // Clear queue from Room
            crossRefDao.clearQueue()
            
            // Optionally clean up orphaned songs (not liked, saved, downloaded, or in playlists)
            songDao.deleteOrphanedSongs()
            
            // Clear settings from DataStore
            dataStore.edit { preferences ->
                preferences.clear()
            }
            Timber.d("PlayerStatePersistence: Cleared state")
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear player state")
        }
    }
}

