package com.viperplayer.data.repository

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import com.viperplayer.data.player.MediaControllerManager
import com.viperplayer.data.player.MediaItemMapper
import com.viperplayer.data.player.PersistedPlayerState
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
import com.viperplayer.domain.repository.PluginRepository
import com.viperplayer.domain.repository.SettingsRepository
import com.viperplayer.presentation.player.PlayerQueueLogic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
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
    private val mediaLibraryRepository: MediaLibraryRepository,
    private val pluginRepository: PluginRepository,
    private val settingsRepository: SettingsRepository
) : PlayerRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // The async "build the rest of the queue" coroutine; cancel it before starting new playback so
    // a previous playAll can't keep adding its songs into the new queue.
    private var queueBuildJob: Job? = null

    private val _playbackContext = MutableStateFlow<PlaybackContext?>(null)

    // Shared controller state extraction
    private val controllerStateFlow = mediaControllerManager.controllerFlow
        .flatMapLatest { controller ->
            // ... callbackFlow implementation same as before ...
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

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        trySend(controller)
                    }

                    override fun onRepeatModeChanged(repeatMode: Int) {
                        trySend(controller)
                    }

                    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                        trySend(controller)
                    }

                    override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
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
                        timeline: Timeline,
                        reason: Int
                    ) {
                        trySend(controller)
                    }
                }
                controller.addListener(listener)
                trySend(controller)
                awaitClose { controller.removeListener(listener) }
            }
        }

    // Radio/autoplay: queue-tail song ids we've already extended from (avoids duplicate fetches/loops).
    private val autoplaySeeds = mutableSetOf<String>()
    private var autoplayJob: Job? = null

    // Follower mode (Listen-together): while true, the autoplay/radio queue-extension is suppressed so a
    // follower's queue never auto-grows beyond the host's shared track (see maybeExtendQueue).
    @Volatile
    private var followerMode = false

    init {
        // Keep playback going: whenever the queue nears its end, append songs related to its tail.
        controllerStateFlow
            .onEach { maybeExtendQueue(it) }
            .launchIn(scope)

        // NOTE: the crossfade track-boundary volume fade lives in PlaybackService now (see
        // PlaybackService.observeCrossfade), not here. It is the single owner of player.volume so it
        // composes with ReplayGain (base × fade) instead of the two clobbering each other through
        // separate handles (controller.volume vs player.volume set to the same underlying value).
    }

    // Playback state (state, shuffle, repeat, volume, queue info) - NO position, song, or duration
    override val playbackState: StateFlow<PlaybackInfo> =
        combine(controllerStateFlow, _playbackContext) { controller, context ->
            // While an auto-loaded suggestion is playing, surface that instead of the original
            // context; skipping back into the original queue restores it automatically.
            val effectiveContext = if (
                controller.currentMediaItem?.let { MediaItemMapper.run { it.isSuggestion } } == true
            ) PlaybackContext.Suggestions else context
            PlayerStateMapper.createPlaybackInfo(controller).copy(playbackContext = effectiveContext)
        }
            .distinctUntilChanged()
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = PlaybackInfo()
            )

    // Casting state is owned by the MediaControllerManager (it holds the MediaController.Listener
    // that receives the service's custom casting-state broadcast). Delegate straight through.
    override val isCasting: StateFlow<Boolean> = mediaControllerManager.isCasting

    override val playbackSpeed: StateFlow<Float> =
        controllerStateFlow.map { it.playbackParameters.speed }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(5000), 1f)

    override val playbackPitch: StateFlow<Float> =
        controllerStateFlow.map { it.playbackParameters.pitch }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(5000), 1f)

    // ... queue implementation same as before ...

    // ... init block ...

    // ... observeAndPersistPlayerState same logic ...


    override suspend fun play(song: Song, context: PlaybackContext?) {
        queueBuildJob?.cancel()
        resetAutoplay()
        _playbackContext.value = context

        // Save song with full metadata (album, artists, etc.)
        mediaLibraryRepository.saveSong(song)

        val controller = mediaControllerManager.controllerFlow.first()
        val mediaItem = song.toMediaItem()
        controller.setMediaItem(mediaItem)
        controller.prepare()
        controller.play()
    }

    override suspend fun playRemote(
        mediaId: MediaId,
        title: String,
        artist: String,
        artworkUrl: String,
        playWhenReady: Boolean,
    ) {
        // A Listen-together follower loads the host's shared track. The stream is still resolved lazily
        // by ViperMediaSource from the bare mediaId + isVideo extra, so we only need the identity + the
        // display metadata (so the now-playing UI shows the remote track even if it's not in the local DB).
        val extras = Bundle().apply {
            putString("pluginId", mediaId.routingPluginId)
            putString("sourceId", mediaId.sourceId)
            putString("title", title)
            putString("artistName", artist)
            putString("artworkUrl", artworkUrl.ifBlank { null })
            putBoolean("isVideo", false)
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist.ifBlank { null })
            .setArtworkUri(artworkUrl.takeIf { it.isNotBlank() }?.toUri())
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setExtras(extras)
            .build()
        val mediaItem = MediaItem.Builder()
            .setMediaId(mediaId.encode())
            .setUri("") // required so ExoPlayer doesn't crash on an item with no direct URI
            .setMediaMetadata(metadata)
            .build()

        val controller = mediaControllerManager.controllerFlow.first()
        controller.setMediaItem(mediaItem)
        controller.prepare()
        controller.playWhenReady = playWhenReady
    }

    override fun setFollowerMode(enabled: Boolean) {
        followerMode = enabled
    }

    override suspend fun playAll(songs: List<Song>, startIndex: Int, context: PlaybackContext?) {
        if (songs.isEmpty()) return

        queueBuildJob?.cancel()
        resetAutoplay()
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
        queueBuildJob = scope.launch {
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

    /**
     * Autoplay/radio: when the queue is within ~2 items of the end, append songs related to the
     * queue's tail ([PluginRepository.getRelatedSongs]) so playback never stops. Seeded from the
     * tail, so a playlist that ends continues with playlist-flavoured radio and a lone song
     * continues with similar songs. Skipped when repeat-all is on (the queue already loops).
     */
    private fun maybeExtendQueue(controller: Player) {
        // Follower mode (Listen-together): the queue mirrors the host's single shared track — never grow it.
        if (followerMode) return
        // Never run while playAll is still building the queue — appends would interleave with its
        // adds and skew the indices (tapping next would jump around).
        if (queueBuildJob?.isActive == true) return
        val count = controller.mediaItemCount
        if (count == 0 || controller.repeatMode == Player.REPEAT_MODE_ALL) return
        if (controller.currentMediaItemIndex < count - 2) return
        if (autoplayJob?.isActive == true) return

        val tailMediaId = controller.getMediaItemAt(count - 1)?.mediaId ?: return
        if (tailMediaId in autoplaySeeds) return
        val seed = MediaId.decode(tailMediaId) ?: return
        autoplaySeeds.add(tailMediaId)

        autoplayJob = scope.launch {
            val related = pluginRepository.getRelatedSongs(seed).getOrNull()?.items.orEmpty()
            val existing = (0 until controller.mediaItemCount)
                .mapNotNull { controller.getMediaItemAt(it)?.mediaId }
                .toSet()
            val fresh = related.filter { it.id.encode() !in existing }
            Timber.d("autoplay: tail=$tailMediaId related=${related.size} new=${fresh.size} queue=${controller.mediaItemCount}")
            if (fresh.isEmpty()) return@launch
            fresh.forEach { runCatching { mediaLibraryRepository.saveSong(it) } }
            // Marked so the player can flip its context to "Suggested songs" once these play.
            fresh.toMediaItems().forEach { controller.addMediaItem(MediaItemMapper.run { it.asSuggestion() }) }
        }
    }

    /** A new queue or stop invalidates the radio: cancel any in-flight related-songs fetch and clear
     *  the seed history so it can't append into the replaced queue or grow unbounded for the session. */
    private fun resetAutoplay() {
        autoplayJob?.cancel()
        autoplaySeeds.clear()
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
            // The DB record also re-emits on library-state writes (like / download), but the UI reads
            // those from their own flows. Ignore library-only changes here so liking the current track
            // doesn't churn currentSong and recompose everything that renders it.
            .distinctUntilChanged { old, new ->
                old?.copy(isLiked = false, isDownloaded = false) ==
                    new?.copy(isLiked = false, isDownloaded = false)
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = null
            )

    /**
     * Extracts MediaId from the controller's current MediaItem.
     * Returns null if no MediaItem is available or MediaId cannot be parsed.
     */
    private fun extractMediaIdFromController(player: Player): MediaId? {
        val mediaItem = player.currentMediaItem ?: return null

        return try {
            MediaId.decode(mediaItem.mediaId)
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse MediaId from controller: ${mediaItem.mediaId}")
            null
        }
    }

    /**
     * Extracts the ordered queue MediaIds straight from the controller's timeline, without hydrating
     * them to full Songs — so the persistence collector no longer needs the heavy WhileSubscribed
     * queue flow, letting that flow stop when no UI is bound.
     */
    private fun extractQueueMediaIds(player: Player): List<MediaId> {
        return (0 until player.mediaItemCount).mapNotNull { i ->
            try {
                MediaId.decode(player.getMediaItemAt(i).mediaId)
            } catch (e: Exception) {
                null
            }
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
                            trySend(controller.duration.takeIf { it != C.TIME_UNSET }
                                ?.coerceAtLeast(0) ?: 0L)
                        }

                        override fun onTimelineChanged(
                            timeline: Timeline,
                            reason: Int
                        ) {
                            trySend(controller.duration.takeIf { it != C.TIME_UNSET }
                                ?.coerceAtLeast(0) ?: 0L)
                        }
                    }
                    controller.addListener(listener)
                    trySend(
                        controller.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0) ?: 0L
                    )
                    awaitClose { controller.removeListener(listener) }
                }
            }
            .distinctUntilChanged()
            .stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = 0L
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
                                            MediaId.decode(mediaIdString)
                                        } catch (e: Exception) {
                                            Timber.e(e, "Failed to parse MediaId: $mediaIdString")
                                            null
                                        }
                                    }
                                }
                            trySend(mediaIds)
                        }

                        override fun onTimelineChanged(
                            timeline: Timeline,
                            reason: Int
                        ) {
                            val mediaIds = (0 until controller.mediaItemCount)
                                .mapNotNull { index ->
                                    controller.getMediaItemAt(index)?.mediaId?.let { mediaIdString ->
                                        try {
                                            MediaId.decode(mediaIdString)
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
                                    MediaId.decode(mediaIdString)
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
            // Same as currentSong: don't re-emit the queue (which drives the player's artwork pager)
            // when a track's library state changes — only when the queue's songs/identity change.
            .distinctUntilChanged { old, new ->
                old.map { it.copy(isLiked = false, isDownloaded = false) } ==
                    new.map { it.copy(isLiked = false, isDownloaded = false) }
            }
            // Share ONE collection across all consumers (UI + state persistence) instead of each
            // independently re-running the per-row hydration — this is the heaviest flow in the app.
            .stateIn(scope, SharingStarted.WhileSubscribed(5000L), emptyList())

    init {
        // Persist player state whenever it changes
        observeAndPersistPlayerState()
    }

    /**
     * Observes player state changes and persists them, gated by the persistent-queue setting.
     * When the setting is off we never write the queue and clear any previously-saved state, so a
     * later cold start begins with an empty player and no stale queue is left behind.
     */
    private fun observeAndPersistPlayerState() {
        // Combine all state that needs to be persisted.
        // Deliberately collects NEITHER currentSong NOR the queue flow — both the current media id and
        // the ordered queue ids come straight from the controller, so those heavy WhileSubscribed flows
        // stop hydrating when no UI is bound. (playbackState fires on add/remove/skip and on a reorder
        // that moves the current item; a pure non-current reorder persists on the next playback event.)
        combine(
            playbackState,
            mediaControllerManager.controllerFlow,
            settingsRepository.persistentQueueEnabled,
        ) { playback, controller, persistentQueueEnabled ->
            // Get current position and queue position from controller
            val position = controller.currentPosition.coerceAtLeast(0)
            val queuePosition = controller.currentMediaItemIndex.coerceAtLeast(0)

            val state = PersistedPlayerState(
                currentSongMediaId = extractMediaIdFromController(controller)?.toString(),
                currentPositionMs = position,
                queuePosition = queuePosition,
                shuffleEnabled = playback.shuffleEnabled,
                repeatMode = playback.repeatMode.name,
            )
            // state + ordered queue ids (no hydration), plus the gate for the write below
            Triple(state, extractQueueMediaIds(controller), persistentQueueEnabled)
        }
            .debounce(2000) // Save at most every 2 seconds to avoid excessive writes
            .onEach { (state, queueMediaIds, persistentQueueEnabled) ->
                if (persistentQueueEnabled) {
                    // saveState writes the queue order (ids) to Room and settings to DataStore
                    playerStatePersistence.saveState(state, queueMediaIds)
                } else {
                    // Setting off: drop any previously-persisted queue so nothing is restored later.
                    playerStatePersistence.clear()
                }
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
        resetAutoplay()
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

    override suspend fun getBufferedPosition(): Long {
        val controller = mediaControllerManager.controllerFlow.first()
        return controller.bufferedPosition.coerceAtLeast(0)
    }

    override suspend fun addToQueue(song: Song) {
        // Save song with full metadata (album, artists, etc.)
        mediaLibraryRepository.saveSong(song)

        val controller = mediaControllerManager.controllerFlow.first()
        val mediaItem = song.toMediaItem()
        if (!allowQueueAdd(controller, mediaItem.mediaId)) {
            Timber.d("Skipping add-to-queue: ${mediaItem.mediaId} already queued (prevent-duplicates on)")
            return
        }
        controller.addMediaItem(mediaItem)
    }

    override suspend fun playNext(song: Song) {
        // Save song with full metadata (album, artists, etc.)
        mediaLibraryRepository.saveSong(song)

        val controller = mediaControllerManager.controllerFlow.first()
        val mediaItem = song.toMediaItem()
        if (!allowQueueAdd(controller, mediaItem.mediaId)) {
            Timber.d("Skipping play-next: ${mediaItem.mediaId} already queued (prevent-duplicates on)")
            return
        }
        val nextIndex = controller.currentMediaItemIndex + 1
        controller.addMediaItem(nextIndex, mediaItem)
    }

    /**
     * Whether [mediaId] may be added to the queue, honoring the prevent-duplicate-in-queue setting.
     * Delegates the decision to the pure [PlayerQueueLogic.shouldAddToQueue]; this only gathers the
     * current queue ids and the setting value.
     */
    private suspend fun allowQueueAdd(controller: Player, mediaId: String): Boolean {
        val preventDuplicates = settingsRepository.preventDuplicateQueue.first()
        if (!preventDuplicates) return true
        val existing = (0 until controller.mediaItemCount)
            .map { controller.getMediaItemAt(it).mediaId }
        return PlayerQueueLogic.shouldAddToQueue(mediaId, existing, preventDuplicates = true)
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

    override suspend fun setPlaybackSpeed(speed: Float) {
        val controller = mediaControllerManager.controllerFlow.first()
        controller.playbackParameters = controller.playbackParameters
            .withSpeed(PlayerQueueLogic.clampSpeed(speed))
    }

    override suspend fun setPlaybackPitch(pitch: Float) {
        val controller = mediaControllerManager.controllerFlow.first()
        val current = controller.playbackParameters
        // Independent of speed — Sonic applies pitch separately from tempo.
        controller.playbackParameters = PlaybackParameters(current.speed, PlayerQueueLogic.clampPitch(pitch))
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
        val (codec, lossless) = codecInfo(format.sampleMimeType)

        return AudioFormat(
            sampleRate = sampleRate,
            bitDepth = bitDepth,
            bitrate = bitrate,
            channelCount = channelCount,
            codec = codec,
            lossless = lossless,
        )
    }

    /** Map an ExoPlayer sample MIME type to a friendly codec name + whether it's lossless. */
    private fun codecInfo(mime: String?): Pair<String?, Boolean?> = when (mime?.lowercase()) {
        "audio/flac" -> "FLAC" to true
        "audio/alac", "audio/x-alac" -> "ALAC" to true
        "audio/raw", "audio/wav", "audio/x-wav" -> "PCM" to true
        "audio/mpeg", "audio/mpeg-l1", "audio/mpeg-l2" -> "MP3" to false
        "audio/mp4a-latm", "audio/aac" -> "AAC" to false
        "audio/opus" -> "Opus" to false
        "audio/vorbis" -> "Vorbis" to false
        "audio/ac3" -> "AC-3" to false
        "audio/eac3", "audio/eac3-joc" -> "E-AC-3" to false
        null -> null to null
        else -> mime.substringAfter('/').uppercase() to null
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
