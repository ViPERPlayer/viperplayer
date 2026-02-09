package com.viperplayer.data.repository

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import com.viperplayer.data.player.MediaControllerManager
import com.viperplayer.data.player.MediaItemMapper
import com.viperplayer.data.player.PlayerStateMapper
import com.viperplayer.data.player.PlayerStatePersistence
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.PlaybackContext
import com.viperplayer.domain.model.PlaybackInfo
import com.viperplayer.domain.model.PlayerState
import com.viperplayer.domain.model.RepeatMode
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.repository.AudioFormat
import com.viperplayer.domain.repository.MediaLibraryRepository
import com.viperplayer.domain.repository.PlayerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of PlayerRepository that uses MediaController to interact with ExoPlayer
 * in the PlaybackService. This follows clean architecture by keeping Media3/ExoPlayer
 * details in the data layer.
 */
@OptIn(UnstableApi::class)
@Singleton
class PlayerRepositoryImpl @Inject constructor(
    private val mediaControllerManager: MediaControllerManager,
    private val playerStatePersistence: PlayerStatePersistence,
    private val mediaLibraryRepository: MediaLibraryRepository
) : PlayerRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _playbackContext = MutableStateFlow<PlaybackContext?>(null)

    // Shared controller state extraction
    private val controllerStateFlow = mediaControllerManager.controllerFlow
        .flatMapLatest { controller ->
            // ... callbackFlow implementation same as before ...
            callbackFlow {
                val listener = object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) { trySend(controller) }
                    override fun onIsPlayingChanged(isPlaying: Boolean) { trySend(controller) }
                    override fun onPlayerError(error: PlaybackException) { trySend(controller) }
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { trySend(controller) }
                    override fun onRepeatModeChanged(repeatMode: Int) { trySend(controller) }
                    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) { trySend(controller) }
                    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) { trySend(controller) }
                    override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) { trySend(controller) }
                }
                controller.addListener(listener)
                trySend(controller)
                awaitClose { controller.removeListener(listener) }
            }
        }

    // Playback state (state, shuffle, repeat, volume, queue info) - NO position, song, or duration
    override val playbackState: StateFlow<PlaybackInfo> =
        combine(controllerStateFlow, _playbackContext) { controller, context ->
                PlayerStateMapper.createPlaybackInfo(controller).copy(playbackContext = context)
            }
            .distinctUntilChanged()
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = PlaybackInfo()
            )

    // ... queue implementation same as before ...

    // ... init block ...

    // ... observeAndPersistPlayerState same logic ...


    override suspend fun play(song: Song, context: PlaybackContext?) {
        _playbackContext.value = context
        
        // Save song with full metadata (album, artists, etc.)
        mediaLibraryRepository.saveSong(song)
        
        val controller = mediaControllerManager.controllerFlow.first()
        val mediaItem = song.toMediaItem()
        controller.setMediaItem(mediaItem)
        controller.prepare()
        controller.play()
    }

    override suspend fun playAll(songs: List<Song>, startIndex: Int, context: PlaybackContext?) {
        if (songs.isEmpty()) return
        
        _playbackContext.value = context

        val safeStartIndex = startIndex.coerceIn(0, songs.lastIndex)
        val startSong = songs[safeStartIndex]
        
        // 1. Save and play the selected song immediately
        try {
            mediaLibraryRepository.saveSong(startSong)
        } catch (e: Exception) {
            Timber.e(e, "Failed to save start song to database: ${startSong.title}")
        }
        
        val controller = mediaControllerManager.controllerFlow.first()
        val startMediaItem = startSong.toMediaItem()
        
        controller.setMediaItem(startMediaItem)
        controller.prepare()
        controller.play()
        
        // 2. Build the rest of the queue asynchronously
        scope.launch {
            // Save all songs to database (skip the start song since it's already saved)
            songs.forEachIndexed { index, song ->
                if (index != safeStartIndex) {
                    try {
                        mediaLibraryRepository.saveSong(song)
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to save song to database: ${song.title}")
                    }
                }
            }
            
            // Add all media items to the queue
            val allMediaItems = songs.toMediaItems()
            
            // Add items before the start song
            for (i in 0 until safeStartIndex) {
                controller.addMediaItem(i, allMediaItems[i])
            }
            
            // Add items after the start song
            for (i in (safeStartIndex + 1) until allMediaItems.size) {
                controller.addMediaItem(allMediaItems[i])
            }
        }
    }


    // Current song - loads from database as single source of truth
    override val currentSong: StateFlow<Song?> =
        mediaControllerManager.controllerFlow
            .flatMapLatest { controller ->
                // Flow of MediaId changes from controller
                callbackFlow<MediaId?> {
                    val listener = object : Player.Listener {
                        override fun onMediaItemTransition(
                            mediaItem: MediaItem?,
                            reason: Int
                        ) {
                            val mediaId = extractMediaIdFromController(controller)
                            trySend(mediaId)
                        }
                        
                        override fun onTimelineChanged(
                            timeline: Timeline,
                            reason: Int
                        ) {
                            val mediaId = extractMediaIdFromController(controller)
                            trySend(mediaId)
                        }
                        
                        override fun onTracksChanged(tracks: Tracks) {
                            // Format information becomes available when tracks are loaded
                            val mediaId = extractMediaIdFromController(controller)
                            // MediaId doesn't change, but we want to reload to get updated format
                            trySend(mediaId)
                        }
                    }
                    controller.addListener(listener)
                    
                    // Initial MediaId
                    val initialMediaId = extractMediaIdFromController(controller)
                    trySend(initialMediaId)
                    
                    awaitClose { controller.removeListener(listener) }
                }
            }
            .distinctUntilChanged() // Only reload when MediaId actually changes
            .flatMapLatest { mediaId: MediaId? ->
                // Load song from database for each MediaId
                if (mediaId != null) {
                    mediaLibraryRepository.getSong(mediaId)
                } else {
                    flowOf<Song?>(null)
                }
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = null
            )
    
    /**
     * Extracts MediaId from the controller's current MediaItem.
     * Returns null if no MediaItem is available or MediaId cannot be parsed.
     */
    private fun extractMediaIdFromController(player: Player): MediaId? {
        val mediaItem = player.currentMediaItem ?: return null
        
        return try {
            MediaId.fromString(mediaItem.mediaId)
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse MediaId from controller: ${mediaItem.mediaId}")
            null
        }
    }
    
    /**
     * Extracts audio Format information from ExoPlayer's current tracks.
     * This provides the actual playback format (sample rate, bitrate, channels, etc.)
     */
    private fun getAudioFormatFromPlayer(player: Player): Format? {
        return try {
            val tracks = player.currentTracks
            // Find the audio track that's currently selected/playing
            tracks.groups.forEach { trackGroup ->
                if (trackGroup.type == C.TRACK_TYPE_AUDIO && trackGroup.isSelected) {
                    // Get the format for the selected track
                    val mediaTrackGroup = trackGroup.mediaTrackGroup
                    for (i in 0 until mediaTrackGroup.length) {
                        val format = mediaTrackGroup.getFormat(i)
                        if (format != null) {
                            Timber.d("getAudioFormatFromPlayer: sampleRate=${format.sampleRate}, bitrate=${format.bitrate}, channels=${format.channelCount}, pcmEncoding=${format.pcmEncoding}")
                            return format
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            Timber.e(e, "Failed to get audio format from player")
            null
        }
    }
    

    // Duration - only emits when track changes
    override val duration: StateFlow<Long> =
        mediaControllerManager.controllerFlow
            .flatMapLatest { controller ->
                callbackFlow {
                    val listener = object : Player.Listener {
                        override fun onMediaItemTransition(
                            mediaItem: MediaItem?,
                            reason: Int
                        ) {
                            trySend(controller.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0) ?: 0L)
                        }
                        
                        override fun onTimelineChanged(
                            timeline: Timeline,
                            reason: Int
                        ) {
                            trySend(controller.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0) ?: 0L)
                        }
                    }
                    controller.addListener(listener)
                    trySend(controller.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0) ?: 0L)
                    awaitClose { controller.removeListener(listener) }
                }
            }
            .distinctUntilChanged()
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = 0L
            )

    // Legacy combined state for backward compatibility
    // Note: positionMs is set to 0 since position is now polled on-demand via getCurrentPosition()
    override val playerState: StateFlow<PlayerState> =
        combine(playbackState, currentSong, duration) { playback, song, dur ->
            PlayerState(
                state = playback.state,
                currentSong = song,
                positionMs = 0L, // Position is now polled on-demand, not included in flow
                durationMs = dur,
                shuffleEnabled = playback.shuffleEnabled,
                repeatMode = playback.repeatMode,
                volume = playback.volume,
                queueSize = playback.queueSize,
                queuePosition = playback.queuePosition,
                playbackContext = playback.playbackContext
            )
        }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = PlayerState()
            )

    override val queue: Flow<List<Song>> =
        mediaControllerManager.controllerFlow
            .flatMapLatest { controller ->
                // Flow of MediaId lists from controller
                callbackFlow {
                    val listener = object : Player.Listener {
                        override fun onMediaItemTransition(
                            mediaItem: MediaItem?,
                            reason: Int
                        ) {
                            val mediaIds = (0 until controller.mediaItemCount)
                                .mapNotNull { index ->
                                    controller.getMediaItemAt(index)?.mediaId?.let { mediaIdString ->
                                        try {
                                            MediaId.fromString(mediaIdString)
                                        } catch (e: Exception) {
                                            Timber.e(e, "Failed to parse MediaId: $mediaIdString")
                                            null
                                        }
                                    }
                                }
                            trySend(mediaIds)
                        }
                        
                        override fun onTimelineChanged(
                            timeline: androidx.media3.common.Timeline,
                            reason: Int
                        ) {
                            val mediaIds = (0 until controller.mediaItemCount)
                                .mapNotNull { index ->
                                    controller.getMediaItemAt(index)?.mediaId?.let { mediaIdString ->
                                        try {
                                            MediaId.fromString(mediaIdString)
                                        } catch (e: Exception) {
                                            Timber.e(e, "Failed to parse MediaId: $mediaIdString")
                                            null
                                        }
                                    }
                                }
                            trySend(mediaIds)
                        }
                    }
                    controller.addListener(listener)
                    
                    // Initial MediaIds
                    val initialMediaIds = (0 until controller.mediaItemCount)
                        .mapNotNull { index ->
                            controller.getMediaItemAt(index)?.mediaId?.let { mediaIdString ->
                                try {
                                    MediaId.fromString(mediaIdString)
                                } catch (e: Exception) {
                                    Timber.e(e, "Failed to parse MediaId: $mediaIdString")
                                    null
                                }
                            }
                        }
                    trySend(initialMediaIds)
                    
                    awaitClose { controller.removeListener(listener) }
                }
            }
            .distinctUntilChanged()
            .flatMapLatest { mediaIds: List<MediaId> ->
                // Load all songs from database
                if (mediaIds.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    combine(
                        mediaIds.map { mediaId ->
                            mediaLibraryRepository.getSong(mediaId)
                        }
                    ) { songs: Array<Song?> -> songs.filterNotNull() }
                }
            }
    
    init {
        // Persist player state whenever it changes
        observeAndPersistPlayerState()
    }
    
    /**
     * Observes player state changes and persists them.
     */
    private fun observeAndPersistPlayerState() {
        // Combine all state that needs to be persisted
        combine(
            currentSong,
            playbackState,
            queue,
            mediaControllerManager.controllerFlow
        ) { song, playback, queueSongs, controller ->
            // Get current position and queue position from controller
            val position = controller.currentPosition.coerceAtLeast(0)
            val queuePosition = controller.currentMediaItemIndex.coerceAtLeast(0)
            
            com.viperplayer.data.player.PersistedPlayerState(
                currentSongMediaId = song?.id?.toString(),
                currentPositionMs = position,
                queuePosition = queuePosition,
                shuffleEnabled = playback.shuffleEnabled,
                repeatMode = playback.repeatMode.name,
            ) to queueSongs // Return state and queue together
        }
            .debounce(2000) // Save at most every 2 seconds to avoid excessive writes
            .onEach { (state, queueSongs) ->
                // saveState will save queue to Room and settings to DataStore
                // Songs should already be saved to database when played/added to queue
                playerStatePersistence.saveState(state, queueSongs)
            }
            .launchIn(scope)
    }



    override suspend fun pause() {
        mediaControllerManager.controllerFlow.first().pause()
    }

    override suspend fun resume() {
        mediaControllerManager.controllerFlow.first().play()
    }

    override suspend fun togglePlayPause() {
        val controller = mediaControllerManager.controllerFlow.first()
        if (controller.isPlaying) {
            controller.pause()
        } else {
            controller.play()
        }
    }

    override suspend fun stop() {
        val controller = mediaControllerManager.controllerFlow.first()
        controller.stop()
        controller.clearMediaItems()
    }

    override suspend fun skipToNext() {
        val controller = mediaControllerManager.controllerFlow.first()
        if (controller.hasNextMediaItem()) {
            controller.seekToNextMediaItem()
        }
    }

    override suspend fun skipToPrevious() {
        val controller = mediaControllerManager.controllerFlow.first()
        if (controller.hasPreviousMediaItem()) {
            controller.seekToPreviousMediaItem()
        } else {
            // If no previous item, seek to start of current item
            controller.seekTo(0)
        }
    }

    override suspend fun playFromQueue(index: Int) {
        val controller = mediaControllerManager.controllerFlow.first()
        if (index in 0 until controller.mediaItemCount) {
            controller.seekTo(index, 0)
            controller.prepare()
            controller.play()
        }
    }

    override suspend fun seekTo(positionMs: Long) {
        mediaControllerManager.controllerFlow.first().seekTo(positionMs.coerceAtLeast(0))
    }

    override suspend fun getCurrentPosition(): Long {
        val controller = mediaControllerManager.controllerFlow.first()
        return controller.currentPosition.coerceAtLeast(0)
    }

    override suspend fun addToQueue(song: Song) {
        // Save song with full metadata (album, artists, etc.)
        mediaLibraryRepository.saveSong(song)
        
        val controller = mediaControllerManager.controllerFlow.first()
        val mediaItem = song.toMediaItem()
        controller.addMediaItem(mediaItem)
    }

    override suspend fun playNext(song: Song) {
        // Save song with full metadata (album, artists, etc.)
        mediaLibraryRepository.saveSong(song)
        
        val controller = mediaControllerManager.controllerFlow.first()
        val mediaItem = song.toMediaItem()
        val nextIndex = controller.currentMediaItemIndex + 1
        controller.addMediaItem(nextIndex, mediaItem)
    }

    override suspend fun duplicateInQueue(index: Int) {
        val controller = mediaControllerManager.controllerFlow.first()
        if (index in 0 until controller.mediaItemCount) {
            val mediaItem = controller.getMediaItemAt(index)
            if (mediaItem != null) {
                val nextIndex = controller.currentMediaItemIndex + 1
                controller.addMediaItem(nextIndex, mediaItem)
            }
        }
    }

    override suspend fun removeFromQueue(index: Int) {
        val controller = mediaControllerManager.controllerFlow.first()
        if (index in 0 until controller.mediaItemCount) {
            controller.removeMediaItem(index)
        }
    }

    override suspend fun reorderQueue(fromIndex: Int, toIndex: Int) {
        val controller = mediaControllerManager.controllerFlow.first()
        val itemCount = controller.mediaItemCount
        if (fromIndex in 0 until itemCount && toIndex in 0 until itemCount && fromIndex != toIndex) {
            controller.moveMediaItem(fromIndex, toIndex)
        }
    }

    override suspend fun clearQueue() {
        mediaControllerManager.controllerFlow.first().clearMediaItems()
    }

    override suspend fun setShuffle(enabled: Boolean) {
        mediaControllerManager.controllerFlow.first().shuffleModeEnabled = enabled
    }

    override suspend fun setRepeatMode(mode: RepeatMode) {
        mediaControllerManager.controllerFlow.first().repeatMode = mode.toMedia3RepeatMode()
    }
    
    override suspend fun getAudioFormat(): AudioFormat? {
        val controller = mediaControllerManager.controllerFlow.first()
        val format = getAudioFormatFromPlayer(controller) ?: return null
        
        val sampleRate = format.sampleRate.takeIf { it > 0 }
        val bitDepth = format.pcmEncoding.let { encoding ->
            when (encoding) {
                C.ENCODING_PCM_16BIT -> 16
                C.ENCODING_PCM_24BIT -> 24
                C.ENCODING_PCM_32BIT -> 32
                C.ENCODING_PCM_FLOAT -> 32
                else -> null
            }
        }
        val bitrate = format.bitrate.takeIf { it > 0 }?.let { it / 1000 } // Convert to kbps
        val channelCount = format.channelCount.takeIf { it > 0 }
        
        return AudioFormat(
            sampleRate = sampleRate,
            bitDepth = bitDepth,
            bitrate = bitrate,
            channelCount = channelCount
        )
    }

    private fun Int.toRepeatMode(): RepeatMode = PlayerStateMapper.run {
        this@toRepeatMode.toRepeatMode()
    }

    private fun RepeatMode.toMedia3RepeatMode(): Int = PlayerStateMapper.run {
        this@toMedia3RepeatMode.toMedia3RepeatMode()
    }

    private fun Song.toMediaItem() = MediaItemMapper.run {
        this@toMediaItem.toMediaItem()
    }

    private fun List<Song>.toMediaItems() = MediaItemMapper.run {
        this@toMediaItems.toMediaItems()
    }

}
