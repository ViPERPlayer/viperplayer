package com.viperplayer.data.repository

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import com.viperplayer.data.player.MediaControllerManager
import com.viperplayer.data.player.MediaItemMapper
import com.viperplayer.data.player.PlayerStateMapper
import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.PlaybackInfo
import com.viperplayer.domain.model.PlayerState
import com.viperplayer.domain.model.RepeatMode
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.repository.PlayerRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
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
    @param:ApplicationContext private val context: Context,
    private val mediaControllerManager: MediaControllerManager
) : PlayerRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Shared controller state extraction
    private val controllerStateFlow = mediaControllerManager.controllerFlow
        .flatMapLatest { controller ->
            callbackFlow {
                val listener = object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        trySend(controller)
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        trySend(controller)
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        trySend(controller)
                    }

                    override fun onMediaItemTransition(
                        mediaItem: MediaItem?,
                        reason: Int
                    ) {
                        trySend(controller)
                    }

                    override fun onRepeatModeChanged(repeatMode: Int) {
                        trySend(controller)
                    }

                    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                        trySend(controller)
                    }

                    override fun onPositionDiscontinuity(
                        oldPosition: Player.PositionInfo,
                        newPosition: Player.PositionInfo,
                        reason: Int
                    ) {
                        trySend(controller)
                    }

                    override fun onTimelineChanged(
                        timeline: androidx.media3.common.Timeline,
                        reason: Int
                    ) {
                        trySend(controller)
                    }
                }

                controller.addListener(listener)
                trySend(controller)

                awaitClose {
                    controller.removeListener(listener)
                }
            }
        }

    // Playback state (state, shuffle, repeat, volume, queue info) - NO position, song, or duration
    override val playbackState: StateFlow<PlaybackInfo> =
        controllerStateFlow
            .map { controller ->
                PlayerStateMapper.createPlaybackInfo(controller)
            }
            .distinctUntilChanged()
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = PlaybackInfo()
            )

    // Current song - only emits when track changes or metadata updates
    override val currentSong: StateFlow<Song?> =
        mediaControllerManager.controllerFlow
            .flatMapLatest { controller ->
                callbackFlow {
                    val listener = object : Player.Listener {
                        override fun onMediaItemTransition(
                            mediaItem: MediaItem?,
                            reason: Int
                        ) {
                            trySend(createSongFromController(controller))
                        }
                        
                        override fun onTimelineChanged(
                            timeline: Timeline,
                            reason: Int
                        ) {
                            trySend(createSongFromController(controller))
                        }
                        
                        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                            // Metadata might be populated asynchronously by ExoPlayer
                            // Update the song when metadata becomes available
                            trySend(createSongFromController(controller))
                        }
                    }
                    controller.addListener(listener)
                    trySend(createSongFromController(controller))
                    awaitClose { controller.removeListener(listener) }
                }
            }
//            .distinctUntilChanged { old, new ->
//                // Only emit if song ID changed OR if title/artist changed (metadata update)
//                old?.id != new?.id || old?.title != new?.title || old?.artistName != new?.artistName
//            }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = null
            )
    
    /**
     * Creates a Song from the controller's current state, using both MediaItem and player metadata.
     * This ensures we get metadata that ExoPlayer might have loaded asynchronously.
     */
    private fun createSongFromController(player: Player): Song? {
        val mediaItem = player.currentMediaItem
        if (mediaItem == null) {
            Timber.d("createSongFromController: currentMediaItem is null")
            return null
        }

        val mediaMetadata = player.mediaMetadata
        if (mediaMetadata == MediaMetadata.EMPTY) {
            Timber.d("createSongFromController: mediaMetadata is empty")
            return null
        }

        Timber.d("createSongFromController: mediaId=${mediaItem.mediaId}")
        Timber.d("createSongFromController: title=${mediaMetadata.title}")
        Timber.d("createSongFromController: artist=${mediaMetadata.artist}")

        // Get the URI from the MediaItem - try localConfiguration first (actual loaded URI), 
        // then requestMetadata (original request), then mediaId as fallback
        val uriString = try {
            mediaItem.localConfiguration?.uri?.toString()
                ?: mediaItem.requestMetadata.mediaUri?.toString()
                ?: mediaItem.mediaId
        } catch (e: Exception) {
            mediaItem.mediaId
        }
        
        Timber.d("createSongFromController: uriString=$uriString, mediaId=${mediaItem.mediaId}")
        
        // Try to parse MediaId from string format "pluginId:sourceId"
        val mediaId = try {
            MediaId.fromString(uriString)
        } catch (e: Exception) {
            // If parsing fails, it might be a URI - create a default MediaId
            val identifier = try {
                val uri = uriString.toUri()
                uri.lastPathSegment ?: uri.host ?: uriString.hashCode().toString()
            } catch (e2: Exception) {
                uriString.hashCode().toString()
            }
            MediaId("unknown", identifier)
        }

        // Try to get title from merged metadata first (most up-to-date), then fallbacks
        val title = mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() }
            ?: "Unknown Title"
        
        Timber.d("createSongFromController: extracted title=$title")
        
        // Try to get artist from merged metadata first (most up-to-date), then fallbacks
        val artistName = mediaMetadata.artist?.toString()?.takeIf { it.isNotBlank() }
            ?: "Unknown Artist"
        
        Timber.d("createSongFromController: extracted artist=$artistName")
        
        val albumName = mediaMetadata.albumTitle?.toString()?.takeIf { it.isNotBlank() }
        
        // Handle artwork: prefer artworkUri, but if null, save artworkData to cache and return file URI
        val artworkUrl = mediaMetadata.artworkUri
            ?: mediaMetadata.artworkData?.let { artworkData ->
                saveArtworkToCache(artworkData, mediaId)
            }

        Timber.d("createSongFromController: extracted album=$albumName, artwork=$artworkUrl")
        
        val durationMs = mediaMetadata.extras?.getLong("durationMs")?.takeIf { it > 0 }
            ?: player.duration.takeIf { it != C.TIME_UNSET && it > 0 }

        // Create minimal Artist and Album objects
        val artist = Artist(
            id = MediaId(mediaId.pluginId, "artist_${artistName.hashCode()}"),
            name = artistName
        )

        val album = if (!albumName.isNullOrEmpty()) {
            Album(
                id = MediaId(mediaId.pluginId, "album_${albumName.hashCode()}"),
                name = albumName,
                artists = listOf(artist),
                artworkUrl = artworkUrl?.toString()
            )
        } else {
            null
        }

        val song = Song(
            id = mediaId,
            title = title,
            artists = listOf(artist),
            album = album,
            durationMs = durationMs,
            artworkUrl = artworkUrl?.toString(),
            trackNumber = mediaMetadata.trackNumber,
            discNumber = mediaMetadata.discNumber,
            isExplicit = mediaMetadata.extras?.getBoolean("isExplicit") ?: false
        )
        
        Timber.d("createSongFromController: created song - title=${song.title}, artist=${song.artistNames}")
        
        return song
    }
    
    /**
     * Saves artwork ByteArray to cache directory and returns a file:// URI.
     * This allows Coil to load the artwork even when only artworkData is available.
     * 
     * Note: We keep the domain model (Song) instead of exposing MediaMetadata directly
     * to maintain clean architecture - the domain layer should be framework-agnostic.
     */
    private fun saveArtworkToCache(artworkData: ByteArray, mediaId: MediaId): Uri? {
        return try {
            // Create a cache directory for artwork if it doesn't exist
            val artworkCacheDir = File(context.cacheDir, "artwork")
            if (!artworkCacheDir.exists()) {
                artworkCacheDir.mkdirs()
            }
            
            // Create a unique filename based on mediaId to avoid collisions
            val filename = "artwork_${mediaId.pluginId}_${mediaId.sourceId.hashCode()}.jpg"
            val artworkFile = artworkCacheDir.resolve(filename)

            // Write the artwork data to the file
            artworkFile.writeBytes(artworkData)
            
            // Return the file URI - using Uri.fromFile() for cache files
            // Note: This is safe for internal cache files, though FileProvider would be better for shared files
            artworkFile.toUri().also {
                Timber.d("saveArtworkToCache: saved artwork to $it (${artworkData.size} bytes)")
            }
        } catch (e: Exception) {
            Timber.e(e, "saveArtworkToCache: failed to save artwork to cache")
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
                queuePosition = playback.queuePosition
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
                callbackFlow {
                    val listener = object : Player.Listener {
                        override fun onMediaItemTransition(
                            mediaItem: MediaItem?,
                            reason: Int
                        ) {
                            trySend(
                                (0 until controller.mediaItemCount)
                                    .mapNotNull { index ->
                                        controller.getMediaItemAt(index)?.toSong()
                                    }
                            )
                        }
                        
                        override fun onTimelineChanged(
                            timeline: androidx.media3.common.Timeline,
                            reason: Int
                        ) {
                            trySend(
                                (0 until controller.mediaItemCount)
                                    .mapNotNull { index ->
                                        controller.getMediaItemAt(index)?.toSong()
                                    }
                            )
                        }
                    }
                    controller.addListener(listener)
                    trySend(
                        (0 until controller.mediaItemCount)
                            .mapNotNull { index ->
                                controller.getMediaItemAt(index)?.toSong()
                            }
                    )
                    awaitClose { controller.removeListener(listener) }
                }
            }
            .distinctUntilChanged()

    override suspend fun play(song: Song) {
        withContext(Dispatchers.Main) {
            val controller = mediaControllerManager.controllerFlow.first()
            val mediaItem = song.toMediaItem()
            controller.setMediaItem(mediaItem)
            controller.prepare()
            controller.play()
        }
    }

    override suspend fun playAll(songs: List<Song>, startIndex: Int) {
        if (songs.isEmpty()) return
        
        withContext(Dispatchers.Main) {
            val controller = mediaControllerManager.controllerFlow.first()
            val mediaItems = songs.toMediaItems()
            val safeStartIndex = startIndex.coerceIn(0, mediaItems.lastIndex)

            controller.setMediaItems(mediaItems, safeStartIndex, 0)
            controller.prepare()
            controller.play()
        }
    }

    override suspend fun pause() {
        withContext(Dispatchers.Main) {
            mediaControllerManager.controllerFlow.first().pause()
        }
    }

    override suspend fun resume() {
        withContext(Dispatchers.Main) {
            mediaControllerManager.controllerFlow.first().play()
        }
    }

    override suspend fun togglePlayPause() {
        withContext(Dispatchers.Main) {
            val controller = mediaControllerManager.controllerFlow.first()
            if (controller.isPlaying) {
                controller.pause()
            } else {
                controller.play()
            }
        }
    }

    override suspend fun stop() {
        withContext(Dispatchers.Main) {
            val controller = mediaControllerManager.controllerFlow.first()
            controller.stop()
            controller.clearMediaItems()
        }
    }

    override suspend fun skipToNext() {
        withContext(Dispatchers.Main) {
            val controller = mediaControllerManager.controllerFlow.first()
            if (controller.hasNextMediaItem()) {
                controller.seekToNextMediaItem()
            }
        }
    }

    override suspend fun skipToPrevious() {
        withContext(Dispatchers.Main) {
            val controller = mediaControllerManager.controllerFlow.first()
            if (controller.hasPreviousMediaItem()) {
                controller.seekToPreviousMediaItem()
            } else {
                // If no previous item, seek to start of current item
                controller.seekTo(0)
            }
        }
    }

    override suspend fun seekTo(positionMs: Long) {
        withContext(Dispatchers.Main) {
            mediaControllerManager.controllerFlow.first().seekTo(positionMs.coerceAtLeast(0))
        }
    }

    override suspend fun getCurrentPosition(): Long {
        return withContext(Dispatchers.Main) {
            val controller = mediaControllerManager.controllerFlow.first()
            controller.currentPosition.coerceAtLeast(0)
        }
    }

    override suspend fun addToQueue(song: Song) {
        withContext(Dispatchers.Main) {
            val controller = mediaControllerManager.controllerFlow.first()
            val mediaItem = song.toMediaItem()
            controller.addMediaItem(mediaItem)
        }
    }

    override suspend fun playNext(song: Song) {
        withContext(Dispatchers.Main) {
            val controller = mediaControllerManager.controllerFlow.first()
            val mediaItem = song.toMediaItem()
            val nextIndex = controller.currentMediaItemIndex + 1
            controller.addMediaItem(nextIndex, mediaItem)
        }
    }

    override suspend fun removeFromQueue(index: Int) {
        withContext(Dispatchers.Main) {
            val controller = mediaControllerManager.controllerFlow.first()
            if (index in 0 until controller.mediaItemCount) {
                controller.removeMediaItem(index)
            }
        }
    }

    override suspend fun clearQueue() {
        withContext(Dispatchers.Main) {
            mediaControllerManager.controllerFlow.first().clearMediaItems()
        }
    }

    override suspend fun setShuffle(enabled: Boolean) {
        withContext(Dispatchers.Main) {
            mediaControllerManager.controllerFlow.first().shuffleModeEnabled = enabled
        }
    }

    override suspend fun setRepeatMode(mode: RepeatMode) {
        withContext(Dispatchers.Main) {
            mediaControllerManager.controllerFlow.first().repeatMode = mode.toMedia3RepeatMode()
        }
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

    private fun MediaItem.toSong() = MediaItemMapper.run {
        this@toSong.toSong()
    }
}
