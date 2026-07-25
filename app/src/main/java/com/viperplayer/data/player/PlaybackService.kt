package com.viperplayer.data.player

import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.IBinder
import androidx.annotation.OptIn
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ServiceLifecycleDispatcher
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Timeline
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessorChain
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.PlaybackStats
import androidx.media3.exoplayer.analytics.PlaybackStatsListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.viperplayer.R
import com.viperplayer.data.player.MediaItemMapper.toMediaItem
import com.viperplayer.data.player.resumption.LastSession
import com.viperplayer.data.player.resumption.LastSessionCodec
import com.viperplayer.data.player.resumption.LastSessionItem
import com.viperplayer.data.player.resumption.LastSessionMediaMapper
import com.viperplayer.data.player.resumption.LastSessionStore
import com.viperplayer.data.lastfm.LastfmScrobbler
import com.viperplayer.data.local.dao.SongDao
import com.viperplayer.data.source.PluginDataSource
import com.viperplayer.data.stats.PlayHistoryRecorder
import com.viperplayer.domain.audio.NetworkType
import com.viperplayer.domain.audio.PlaybackTuning
import com.viperplayer.domain.audio.ReplayGainCalculator
import com.viperplayer.domain.audio.ReplayGainMode as CalcReplayGainMode
import com.viperplayer.domain.audio.ReplayGainSettings
import com.viperplayer.domain.audio.ReplayGainTags
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.RepeatMode
import com.viperplayer.domain.repository.MediaLibraryRepository
import com.viperplayer.domain.repository.ReplayGainMode
import com.viperplayer.domain.repository.SettingsRepository
import com.viperplayer.presentation.widget.LyricWidgetUpdater
import com.viperplayer.presentation.widget.PlayerWidgetUpdater
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import javax.inject.Inject

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : MediaLibraryService(), LifecycleOwner, Player.Listener,
    PlaybackStatsListener.Callback {
    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var pluginDataSource: PluginDataSource

    @Inject
    lateinit var exoPlayerCache: ExoPlayerCache

    @Inject
    lateinit var playerStatePersistence: PlayerStatePersistence

    @Inject
    lateinit var viperAudioProcessor: ViperAudioProcessor

    @Inject
    lateinit var mediaLibrarySessionCallback: ViperMediaLibrarySessionCallback

    @Inject
    lateinit var mediaSessionServiceListener: ViperMediaSessionServiceListener

    @Inject
    lateinit var mediaLibraryRepository: MediaLibraryRepository

    @Inject
    lateinit var songDao: SongDao

    @Inject
    lateinit var lastSessionStore: LastSessionStore

    @Inject
    lateinit var playHistoryRecorder: PlayHistoryRecorder

    @Inject
    lateinit var lastfmScrobbler: LastfmScrobbler

    private val dispatcher = ServiceLifecycleDispatcher(this)
    override val lifecycle: Lifecycle
        get() = dispatcher.lifecycle

    private lateinit var player: ExoPlayer
    private lateinit var mediaLibrarySession: MediaLibrarySession

    private val mediaMetadata = MutableStateFlow(MediaMetadata.EMPTY)

    /**
     * Ticks whenever the last-session state meaningfully changes (item transition, play/pause,
     * timeline change). Debounced and collected in [observeAndPersistLastSession]; the snapshot is
     * taken on the player (main) thread, the write happens on IO inside [LastSessionStore].
     *
     * A [MutableSharedFlow] (not a seeded [MutableStateFlow]): a StateFlow would replay its initial
     * value to the collector immediately on start, firing a spurious empty snapshot before restore
     * populates the queue and clearing a previously-persisted resume card. This flow only emits on
     * real player events.
     */
    private val lastSessionTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private var isAudioEffectControlSessionOpen = false

    /**
     * Receiver for media-volume changes (STREAM_MUSIC). When "Pause when muted" is on and the music
     * stream hits zero while playing, we pause. Registered with the service lifecycle. Nullable so
     * unregister is a no-op if registration failed.
     */
    private var volumeReceiver: BroadcastReceiver? = null

    /**
     * Receiver for Bluetooth audio device connection. When "Resume on Bluetooth" is on and playback is
     * currently paused, a connect resumes it. Registered with the service lifecycle.
     */
    private var bluetoothReceiver: BroadcastReceiver? = null

    /** Last track we auto-loaded related songs for, so we don't re-fetch on repeated transitions. */
    private var lastAutoLoadSongId: String? = null

    /**
     * The ReplayGain base volume for the current track (1.0 = no attenuation), the single source of
     * truth for the player's *steady-state* volume. The crossfade loop multiplies this by a fade
     * factor near track boundaries so the two volume owners never clobber each other — see
     * [applyVolume]. Read/written on the player (main) thread only.
     */
    private var replayGainVolume: Float = 1f

    /** Current crossfade fade duration in ms (0 = crossfade off). Updated from settings. */
    @Volatile
    private var crossfadeMs: Long = 0L

    /**
     * Cached [SettingsRepository.skipOnError] value so [onPlayerError] (main thread) can decide the
     * recovery path without blocking on the flow. Kept in sync by a collector in [observeAudioSettings].
     * Defaults to the setting's own default (skip on error) until the first emission lands.
     */
    @Volatile
    private var skipOnError: Boolean = true

    /**
     * Cached [SettingsRepository.pauseWhenMuted] value, read by the volume observer to decide whether a
     * zero media-stream volume should pause playback. Kept in sync by a collector in [observeAudioSettings].
     */
    @Volatile
    private var pauseWhenMuted: Boolean = false

    /**
     * Cached [SettingsRepository.resumeOnBluetooth] value, read by the Bluetooth-connect receiver to
     * decide whether to resume paused playback. Kept in sync by a collector in [observeAudioSettings].
     */
    @Volatile
    private var resumeOnBluetooth: Boolean = false

    /**
     * mediaId of the item we last attempted an out-of-range seek recovery on, so a recovery that
     * itself keeps failing can't loop forever — we retry recovery once per item and then fall back
     * to the normal skip. Cleared on a real item transition (see [onMediaItemTransition]). Player
     * (main) thread only.
     */
    private var seekRecoveryMediaId: String? = null

    override fun onCreate() {
        dispatcher.onServicePreSuperOnCreate()
        super.onCreate()

        Timber.d("onCreate() called")

        // Configure MediaNotificationProvider
        setMediaNotificationProvider(createMediaNotificationProvider())

        // Create ExoPlayer
        player = createExoPlayer()

        // Create MediaSession
        mediaLibrarySession = createMediaLibrarySession()

        // Set MediaSessionService listener
        setListener(mediaSessionServiceListener)

        // Restore player state if available
        restorePlayerState()

        // Wire audio settings (skip-silence + ReplayGain) reactively to the player.
        observeAudioSettings()

        // Pause-when-muted + resume-on-Bluetooth: gated inside the receivers by their cached settings
        // so registration is cheap and the toggles take effect without re-registering.
        registerVolumeReceiver()
        registerBluetoothReceiver()

        // Drive the crossfade track-boundary fade (kept consistent with gapless) as the single
        // volume owner alongside ReplayGain.
        observeCrossfade()

        // Persist the last playback state (for Android 11+ media resumption), debounced.
        observeAndPersistLastSession()

        // Keep any home-screen widget in sync while the service is alive (no-op if no widget added).
        runCatching { PlayerWidgetUpdater.ensureStarted(this) }
            .onFailure { Timber.w(it, "Failed to start widget updater") }
        runCatching { LyricWidgetUpdater.ensureStarted(this) }
            .onFailure { Timber.w(it, "Failed to start lyric widget updater") }
    }

    /**
     * Restores the player state from persistence.
     * Songs are loaded from database with full metadata (artists, album, etc.).
     */
    private fun restorePlayerState() {
        lifecycleScope.launch {
            // Persistent queue (default on): when the user turns it off, cold start with an empty
            // player and never rehydrate the saved queue into the in-app now-playing/queue.
            if (!settingsRepository.persistentQueueEnabled.first()) {
                Timber.d("Persistent queue disabled; starting with an empty player")
                player.prepare()
                return@launch
            }

            val (savedState, queueSongs) = playerStatePersistence.loadState()
            if (savedState == null || queueSongs.isEmpty()) {
                Timber.d("No saved player state or queue to restore")
                player.prepare()
                return@launch
            }

            Timber.d("Restoring player state: song=${savedState.currentSongMediaId}, position=${savedState.currentPositionMs}ms, queueSize=${queueSongs.size}")

            try {
                // Convert Songs to MediaItems using MediaItemMapper (songs already have full metadata from database)
                val mediaItems = queueSongs.mapNotNull { song ->
                    try {
                        song.toMediaItem()
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to create MediaItem from Song: ${song.title}")
                        null
                    }
                }

                if (mediaItems.isNotEmpty()) {
                    val startIndex = savedState.queuePosition.coerceIn(0, mediaItems.lastIndex)
                    player.setMediaItems(mediaItems, startIndex, savedState.currentPositionMs)

                    // Restore shuffle and repeat mode
                    player.shuffleModeEnabled = savedState.shuffleEnabled
                    player.repeatMode = when (savedState.repeatMode) {
                        RepeatMode.OFF.name -> Player.REPEAT_MODE_OFF
                        RepeatMode.ONE.name -> Player.REPEAT_MODE_ONE
                        RepeatMode.ALL.name -> Player.REPEAT_MODE_ALL
                        else -> Player.REPEAT_MODE_OFF
                    }

                    Timber.d("Restored player state successfully")
                } else {
                    Timber.w("No valid songs found in queue to restore")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to restore player state")
            }

            player.prepare()
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        dispatcher.onServicePreSuperOnBind()
        return super.onBind(intent)
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onStart(intent: Intent?, startId: Int) {
        dispatcher.onServicePreSuperOnStart()
        super.onStart(intent, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession {
        Timber.d("onGetSession() called with: controllerInfo = $controllerInfo")
        return mediaLibrarySession
    }

    /**
     * When "Stop when app is closed" is enabled, swiping the app away from Recents pauses playback and
     * stops the service (which releases the player via [onDestroy]). When disabled we keep media3's
     * default (playback survives the task removal so the notification/session stays alive). The setting
     * is read with a short [runBlocking] first() — this is a one-shot lifecycle callback, not a hot path.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        Timber.d("onTaskRemoved() called")
        val stop = runCatching { runBlocking { settingsRepository.stopOnTaskRemoved.first() } }
            .getOrDefault(false)
        if (stop) {
            Timber.d("Stop-on-task-removed enabled; pausing and stopping the service")
            runCatching {
                player.pause()
                player.stop()
            }.onFailure { Timber.w(it, "Failed to stop player on task removed") }
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        Timber.d("onDestroy() called")
        dispatcher.onServicePreSuperOnDestroy()
        // Best-effort synchronous flush of the last session before releasing the player. The
        // debounced observer may have a pending write that gets cancelled with lifecycleScope, so
        // this guarantees the final position/queue is persisted for the resume card — the case that
        // matters most (the app is being swept away). runBlocking is acceptable here: the service is
        // dying and a single DataStore write is cheap.
        runCatching {
            val snapshot = buildLastSessionSnapshot()
            runBlocking {
                if (snapshot == null) lastSessionStore.clear() else lastSessionStore.save(snapshot)
            }
        }.onFailure { Timber.w(it, "Failed to flush last session on destroy") }
        // Close the global audio-effect session before releasing the player, otherwise it stays
        // registered with system equalizer apps (leak).
        closeAudioEffectControlSession()
        unregisterVolumeReceiver()
        unregisterBluetoothReceiver()
        mediaLibrarySession.release()
        player.release()
        super.onDestroy()
    }

    private fun createMediaNotificationProvider(): MediaNotification.Provider {
        return DefaultMediaNotificationProvider.Builder(this)
            .build()
            .apply {
                setSmallIcon(R.drawable.ic_notification)
            }
    }

    /**
     * Builds the player. The forward/back seek increment is read once here from
     * [SettingsRepository.seekIncrementSeconds] because media3 fixes both increments at build time
     * ([ExoPlayer.Builder.setSeekForwardIncrementMs]/[ExoPlayer.Builder.setSeekBackIncrementMs] have
     * no live setter). A change to that setting therefore takes effect on the NEXT service start, not
     * mid-session — an acceptable trade for not tearing down and rebuilding the player on every edit.
     */
    private fun createExoPlayer(): ExoPlayer {
        val seekIncrementMs = runBlocking { settingsRepository.seekIncrementSeconds.first() } * 1000L
        return ExoPlayer.Builder(this)
            .setSeekForwardIncrementMs(seekIncrementMs)
            .setSeekBackIncrementMs(seekIncrementMs)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setAudioAttributes(
                // handleAudioFocus=true delegates transient-loss ducking/pausing and focus-regain
                // resume to ExoPlayer (mirrors PlaybackTuning.focusReaction). Combined with
                // setHandleAudioBecomingNoisy above, playback pauses on headphone unplug / BT drop.
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setMediaSourceFactory(createExoPlayerMediaSourceFactory())
            .setRenderersFactory(createExoPlayerRenderersFactory())
            .setLoadControl(createLoadControl())
            .build()
            .apply {
                addListener(this@PlaybackService)
                addAnalyticsListener(PlaybackStatsListener(false, this@PlaybackService))
            }
    }

    /**
     * A [LoadControl] sized for the current network via the pure [PlaybackTuning.selectBuffer]:
     * metered/cellular links buffer less (less wasted mobile data on skipped tracks), unmetered links
     * buffer more for jitter resilience. `prioritizeTimeOverSizeThresholds(true)` keeps the time-based
     * buffer targets even for high-bitrate lossless, so the next item is prefetched for a smooth
     * (gapless) transition rather than being capped by a byte budget.
     *
     * The network type is sampled once here at player construction (a LoadControl is fixed for the
     * player's lifetime and can't be swapped without rebuilding it). A mid-session Wi-Fi↔cellular
     * change therefore keeps the buffer profile chosen at start — an acceptable trade for not tearing
     * down and rebuilding the player on every connectivity change.
     */
    private fun createLoadControl(): LoadControl {
        val profile = PlaybackTuning.selectBuffer(currentNetworkType())
        return DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                profile.minBufferMs,
                profile.maxBufferMs,
                profile.bufferForPlaybackMs,
                profile.bufferForPlaybackAfterRebufferMs,
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
    }

    /** Current network type for buffer sizing; conservative [NetworkType.UNKNOWN] on any failure. */
    private fun currentNetworkType(): NetworkType {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return NetworkType.UNKNOWN
        return runCatching {
            val network = cm.activeNetwork ?: return NetworkType.UNKNOWN
            val caps = cm.getNetworkCapabilities(network) ?: return NetworkType.UNKNOWN
            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) {
                NetworkType.UNMETERED
            } else {
                NetworkType.METERED
            }
        }.getOrDefault(NetworkType.UNKNOWN)
    }

    private fun createExoPlayerMediaSourceFactory(): MediaSource.Factory {
        val dataSourceFactory = createExoPlayerDataSourceFactory()
        // Constant-bitrate seeking makes seeks in CBR MP3s (and other CBR streams that lack a seek
        // table) land accurately instead of snapping to the nearest coarse index point. We use the
        // plain (non-ALWAYS) variant on purpose: it enables CBR-assumption seeking only when the
        // stream length IS known, so seeks stay in-bounds. The ALWAYS variant would additionally
        // assume CBR for unknown-length streams (e.g. plugin HTTP streams without a reliable
        // Content-Length), where a computed seek target can overshoot and throw
        // ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE — abandoning the track. VBR accuracy is
        // unaffected either way.
        val extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
        val base = DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)
        val dash = DashMediaSource.Factory(dataSourceFactory)
        return ViperMediaSource.Factory(this, pluginDataSource, songDao, base, dash, dataSourceFactory)
    }

    private fun createExoPlayerDataSourceFactory(): DataSource.Factory {
        return CacheDataSource.Factory()
            .setCache(exoPlayerCache.cache)
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(this))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    private fun createExoPlayerRenderersFactory(): RenderersFactory {
        return object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioOutputPlaybackParams: Boolean
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
                    .setAudioProcessorChain(createAudioProcessorChain())
                    .build()
            }
        }
            .setEnableAudioFloatOutput(true)
    }

    private fun createAudioProcessorChain(): AudioProcessorChain {
        // DefaultAudioProcessorChain auto-inserts media3's SilenceSkippingAudioProcessor that only
        // supports 16-bit PCM and throws on our float output (e.g. an MP3 decoded to
        // PCM_FLOAT). FloatSilenceSkippingAudioProcessor is a float-capable port, so the chain is
        // ViPER + silence skipper + Sonic (all handle float).
        val sonic = SonicAudioProcessor()
        val floatSilenceSkipper = FloatSilenceSkippingAudioProcessor()
        return object : AudioProcessorChain {
            override fun getAudioProcessors(): Array<AudioProcessor> =
                arrayOf<AudioProcessor>(viperAudioProcessor, floatSilenceSkipper, sonic)

            override fun applyPlaybackParameters(playbackParameters: PlaybackParameters): PlaybackParameters {
                sonic.setSpeed(playbackParameters.speed)
                sonic.setPitch(playbackParameters.pitch)
                return playbackParameters
            }

            override fun applySkipSilenceEnabled(skipSilenceEnabled: Boolean): Boolean {
                floatSilenceSkipper.setEnabled(skipSilenceEnabled)
                return skipSilenceEnabled
            }

            override fun getMediaDuration(playoutDuration: Long): Long =
                sonic.getMediaDuration(playoutDuration)

            override fun getSkippedOutputFrameCount(): Long = floatSilenceSkipper.getSkippedFrames()
        }
    }

    private fun createMediaLibrarySession(): MediaLibrarySession {
        return MediaLibrarySession.Builder(this, player, mediaLibrarySessionCallback)
            .setBitmapLoader(CoilBitmapLoader(this))
            .setSessionActivity(createMediaLibrarySessionSessionActivity())
            .build()
    }

    private fun createMediaLibrarySessionSessionActivity(): PendingIntent {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        return PendingIntent.getActivity(
            this, 0, launchIntent, PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun observeMediaMetadata() {
        lifecycleScope.launch {
            mediaMetadata
                .debounce(100)
                .collect { mediaMetadata ->
                    val isLiked = mediaMetadata.extras?.getBoolean("liked") == true
                    mediaLibrarySession.setMediaButtonPreferences(
                        listOf(
                            CommandButton.Builder(if (isLiked) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED)
                                .setDisplayName(if (isLiked) "Remove like" else "Like")
                                .build()
                        )
                    )
                }
        }
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (events.containsAny(
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_PLAY_WHEN_READY_CHANGED,
                Player.EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED
            )
        ) {
            val isPlaying =
                (player.playbackState == Player.STATE_BUFFERING || player.playbackState == Player.STATE_READY)
                        && player.playWhenReady
                        && player.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE
            Timber.d("onEvents() called with: isPlaying = $isPlaying")
            if (isPlaying) {
                openAudioEffectControlSession()
            } else {
                closeAudioEffectControlSession()
            }
        }

        // Persist the last-session snapshot on any change that affects what a resume card restores:
        // the queue/current item, play-when-ready, position jumps (seek/track boundary), and
        // shuffle/repeat. The trigger is debounced in observeAndPersistLastSession().
        if (events.containsAny(
                Player.EVENT_TIMELINE_CHANGED,
                Player.EVENT_MEDIA_ITEM_TRANSITION,
                Player.EVENT_PLAY_WHEN_READY_CHANGED,
                Player.EVENT_POSITION_DISCONTINUITY,
                Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                Player.EVENT_REPEAT_MODE_CHANGED
            )
        ) {
            lastSessionTrigger.tryEmit(Unit)
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        Timber.d("onMediaItemTransition() called with: mediaItem = $mediaItem, reason = $reason")

        // A real item transition means the previous seek-recovery attempt (if any) is no longer
        // relevant: arm recovery again for the newly-started item.
        seekRecoveryMediaId = null

        // Record a play whenever a new media item starts (manual, skip, or auto-advance): bumps the
        // song's play count/lastPlayed and inserts a per-play event that powers History and Stats.
        // The song is persisted before playback begins (saveSong precedes setMediaItem), so the
        // event's foreign key resolves; if it somehow isn't, the repository simply skips the event.
        if (mediaItem != null) {
            MediaId.decode(mediaItem.mediaId)?.let { mediaId ->
                lifecycleScope.launch {
                    runCatching { mediaLibraryRepository.incrementSongPlayCount(mediaId) }
                        .onFailure { Timber.e(it, "Failed to record play for $mediaId") }
                }
            }
        }

        // Last.fm now-playing: on every new track start, record the start timestamp and (if enabled +
        // authed) fire track.updateNowPlaying. Off-main; no-ops when not configured/authed. The scrobble
        // itself is submitted later from onPlaybackStatsReady using the REAL listened time.
        runCatching { lastfmScrobbler.onTrackStarted(mediaItem) }
            .onFailure { Timber.w(it, "Failed to notify Last.fm of track start") }

        // Re-apply ReplayGain for the newly-started track. (It is also re-applied live when the
        // ReplayGain settings change — see observeAudioSettings.)
        if (mediaItem != null) {
            lifecycleScope.launch { applyReplayGain() }
        }

        // Endless playback: top up the queue with related tracks when we reach the end.
        maybeAutoLoadMore()
    }

    /**
     * Auto Load More: when enabled and the current track is the last in the queue, append related
     * tracks (de-duped against what's already queued) so playback continues endlessly. Guarded by
     * [lastAutoLoadSongId] so a single track triggers at most one fetch even if the transition fires
     * more than once.
     */
    private fun maybeAutoLoadMore() {
        lifecycleScope.launch {
            if (!settingsRepository.autoLoadMore.first()) return@launch
            val current = player.currentMediaItem ?: return@launch
            if (player.currentMediaItemIndex < player.mediaItemCount - 1) return@launch
            if (lastAutoLoadSongId == current.mediaId) return@launch
            lastAutoLoadSongId = current.mediaId

            val mediaId = MediaId.decode(current.mediaId) ?: return@launch
            val related = pluginDataSource.getRelatedSongs(mediaId).getOrNull()?.items.orEmpty()
            if (related.isEmpty()) return@launch

            val existing = (0 until player.mediaItemCount)
                .mapNotNull { player.getMediaItemAt(it).mediaId }
                .toSet()
            val toAdd = related
                .filter { it.id.encode() !in existing }
                .map { it.toMediaItem() }
            if (toAdd.isNotEmpty()) {
                player.addMediaItems(toAdd)
                Timber.d("Auto Load More: appended ${toAdd.size} related tracks after ${current.mediaId}")
            }
        }
    }

    /**
     * Collects debounced [lastSessionTrigger] ticks and persists a [LastSession] snapshot for media
     * resumption. The snapshot is built on the player (main) thread; [LastSessionStore] does the
     * actual write on IO. Clears the store when the queue is empty so no stale resume card lingers.
     */
    private fun observeAndPersistLastSession() {
        lifecycleScope.launch {
            lastSessionTrigger
                .debounce(2000) // at most one write / 2s, matching the primary state persistence
                .collect {
                    val snapshot = buildLastSessionSnapshot()
                    if (snapshot == null) {
                        lastSessionStore.clear()
                    } else {
                        lastSessionStore.save(snapshot)
                    }
                }
        }
    }

    /** Builds the current [LastSession] from the player, or `null` when the queue is empty. */
    private fun buildLastSessionSnapshot(): LastSession? {
        val count = player.mediaItemCount
        if (count == 0) return null

        // Map raw player indices to the surviving items. An entry is dropped when it lacks the
        // plugin/source id extras (fromMediaItem returns null). Because entries can be dropped, the
        // raw currentMediaItemIndex would point at the wrong surviving item if anything before it is
        // dropped — so we track the current item's position AMONG THE KEPT items instead of reusing
        // the raw index.
        val playerCurrentIndex = player.currentMediaItemIndex
        val items = ArrayList<LastSessionItem>(count)
        var currentIndex = -1 // index of the current item within `items`, or -1 if it was dropped
        var survivorsBeforeCurrent = 0 // kept items with a raw index < playerCurrentIndex
        for (i in 0 until count) {
            val mapped = LastSessionMediaMapper.fromMediaItem(player.getMediaItemAt(i)) ?: continue
            if (i == playerCurrentIndex) currentIndex = items.size
            if (i < playerCurrentIndex) survivorsBeforeCurrent++
            items.add(mapped)
        }
        if (items.isEmpty()) return null

        // If the current item itself was dropped, fall back to the nearest kept item that precedes it
        // (the number of survivors before the raw index), clamped into range — 0 when nothing before
        // it survived.
        if (currentIndex < 0) {
            currentIndex = survivorsBeforeCurrent.coerceIn(0, items.lastIndex)
        }

        val repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_ONE -> RepeatMode.ONE.name
            Player.REPEAT_MODE_ALL -> RepeatMode.ALL.name
            else -> RepeatMode.OFF.name
        }
        val snapshot = LastSession(
            items = items,
            currentIndex = currentIndex,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            playWhenReady = player.playWhenReady,
            shuffleEnabled = player.shuffleModeEnabled,
            repeatMode = repeatMode,
        )
        // Cap the persisted queue to a bounded window around the current item so a huge queue does
        // not serialize fully into the Preferences DataStore blob on every debounced write.
        return LastSessionCodec.capForPersist(snapshot)
    }

    private fun observeAudioSettings() {
        // Skip silence -> ExoPlayer's flag (forced off in DSP-bypass for a clean path).
        lifecycleScope.launch {
            combine(
                settingsRepository.skipSilence,
                settingsRepository.dspBypass,
            ) { skip, bypass -> skip && !bypass }.collect { player.skipSilenceEnabled = it }
        }
        // DSP bypass -> ViPER processor passthrough (no native processing).
        lifecycleScope.launch {
            settingsRepository.dspBypass.collect { viperAudioProcessor.bypassed = it }
        }
        // ReplayGain -> re-apply to the current track whenever any RG setting / bypass changes
        // (track changes re-apply via onMediaItemTransition). The first emission applies the initial state.
        lifecycleScope.launch {
            combine(
                settingsRepository.replayGainEnabled,
                settingsRepository.replayGainMode,
                settingsRepository.replayGainPreampDb,
                settingsRepository.replayGainUntaggedPreampDb,
                settingsRepository.dspBypass,
            ) { _, _, _, _, _ -> }.collect { applyReplayGain() }
        }
        lifecycleScope.launch {
            combine(
                settingsRepository.replayGainDrcEnabled,
                settingsRepository.replayGainPostAmpDb,
            ) { _, _ -> }.collect { applyReplayGain() }
        }
        // Cache small booleans consumed by main-thread callbacks / broadcast receivers so they never
        // block on the flow at the point of use.
        lifecycleScope.launch {
            settingsRepository.skipOnError.collect { skipOnError = it }
        }
        lifecycleScope.launch {
            settingsRepository.pauseWhenMuted.collect { pauseWhenMuted = it }
        }
        lifecycleScope.launch {
            settingsRepository.resumeOnBluetooth.collect { resumeOnBluetooth = it }
        }
    }

    /**
     * Sets the player volume from the current track's ReplayGain tags and the user's RG settings
     * (tagged/untagged preamp, smart/album/track mode, DRC clip-guard, post-amp). All arithmetic lives
     * in the pure [ReplayGainCalculator]; this method only gathers Android state and applies the result.
     */
    private suspend fun applyReplayGain() {
        if (settingsRepository.dspBypass.first()) {
            replayGainVolume = 1f // clean path: no ReplayGain scaling
            applyVolume()
            return
        }
        val settings = ReplayGainSettings(
            enabled = settingsRepository.replayGainEnabled.first(),
            mode = settingsRepository.replayGainMode.first().toCalcMode(),
            preampDb = settingsRepository.replayGainPreampDb.first(),
            untaggedPreampDb = settingsRepository.replayGainUntaggedPreampDb.first(),
            drcEnabled = settingsRepository.replayGainDrcEnabled.first(),
            postAmpDb = settingsRepository.replayGainPostAmpDb.first(),
        )
        val extras = player.currentMediaItem?.mediaMetadata?.extras
        val tags = ReplayGainTags(
            trackGainDb = extras?.takeIf { it.containsKey("replayGainDb") }?.getFloat("replayGainDb"),
            trackPeak = extras?.takeIf { it.containsKey("peakAmplitude") }?.getFloat("peakAmplitude"),
            albumGainDb = extras?.takeIf { it.containsKey("albumReplayGainDb") }?.getFloat("albumReplayGainDb"),
            albumPeak = extras?.takeIf { it.containsKey("albumPeakAmplitude") }?.getFloat("albumPeakAmplitude"),
        )
        val sequentialAlbum = isPlayingSequentialAlbum()
        val volume = ReplayGainCalculator.computeVolume(tags, settings, sequentialAlbum)
        Timber.d("applyReplayGain: volume=$volume (settings=$settings, tags=$tags, sequentialAlbum=$sequentialAlbum)")
        replayGainVolume = volume
        applyVolume()
    }

    /**
     * Applies the effective player volume = ReplayGain base × current crossfade fade factor. This is
     * the single place that writes [player.volume]; both the ReplayGain path and the crossfade loop
     * funnel through here so they compose instead of overwriting each other (the bug where crossfade's
     * volume=1f wiped ReplayGain attenuation and vice-versa). Must run on the player (main) thread.
     */
    private fun applyVolume() {
        val factor = PlaybackTuning.crossfadeFactor(
            positionMs = player.currentPosition,
            durationMs = player.duration,
            crossfadeMs = crossfadeMs,
            // Matches the old loop: never fade a paused track, so pausing inside a fade window can't
            // leave the volume stuck low (it recovers to the RG base immediately, not on resume).
            isPlaying = player.isPlaying,
        )
        player.volume = PlaybackTuning.effectiveVolume(replayGainVolume, factor)
    }

    /**
     * Drives the crossfade track-boundary volume fade from inside the service (the single volume
     * owner). While crossfade is enabled it re-applies base×factor every 100ms so the outgoing track
     * fades out near its end and the incoming one fades in at its start. When crossfade is off it
     * resolves to the ReplayGain base (factor = 1.0), so nothing is left attenuated — this is the
     * gapless path, where ExoPlayer's native seamless transition (encoder delay/padding aware) runs
     * unimpeded. [PlaybackTuning.resolveTransition] keeps the two mutually exclusive.
     */
    private fun observeCrossfade() {
        lifecycleScope.launch {
            settingsRepository.crossfadeDurationSeconds
                .map { seconds -> PlaybackTuning.resolveTransition(seconds) }
                .collectLatest { policy ->
                    crossfadeMs = policy.crossfadeMs
                    if (policy.crossfadeMs <= 0L) {
                        // Off (gapless): settle back to the ReplayGain base and stop polling.
                        applyVolume()
                        return@collectLatest
                    }
                    try {
                        while (isActive) {
                            applyVolume()
                            delay(CROSSFADE_TICK_MS)
                        }
                    } finally {
                        // Never leave the track attenuated when the loop is cancelled (setting change).
                        crossfadeMs = 0L
                        applyVolume()
                    }
                }
        }
    }

    private fun ReplayGainMode.toCalcMode(): CalcReplayGainMode = when (this) {
        ReplayGainMode.TRACK -> CalcReplayGainMode.TRACK
        ReplayGainMode.ALBUM -> CalcReplayGainMode.ALBUM
        ReplayGainMode.SMART -> CalcReplayGainMode.SMART
    }

    /**
     * Whether the queue is currently playing an album in order: shuffle is off and the current track
     * shares its album with the track immediately before or after it in the queue. Used by smart mode
     * to choose album gain (sequential album) vs track gain (shuffle / mixed queue).
     */
    private fun isPlayingSequentialAlbum(): Boolean {
        if (player.shuffleModeEnabled) return false
        val count = player.mediaItemCount
        if (count <= 1) return false
        val index = player.currentMediaItemIndex
        val currentAlbum = albumKeyOf(player.currentMediaItem) ?: return false
        val prevAlbum = index.takeIf { it > 0 }?.let { albumKeyOf(player.getMediaItemAt(it - 1)) }
        val nextAlbum = index.takeIf { it < count - 1 }?.let { albumKeyOf(player.getMediaItemAt(it + 1)) }
        return currentAlbum == prevAlbum || currentAlbum == nextAlbum
    }

    /** Album identity for sequential-album detection: album name is the only album key in the extras. */
    private fun albumKeyOf(item: MediaItem?): String? =
        item?.mediaMetadata?.extras?.getString("albumName")?.takeIf { it.isNotBlank() }

    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
        Timber.d("onMediaMetadataChanged() called with: mediaMetadata = $mediaMetadata")
        this.mediaMetadata.value = mediaMetadata
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        Timber.d("onIsPlayingChanged() called with: isPlaying = $isPlaying")
    }

    override fun onRepeatModeChanged(repeatMode: Int) {
        Timber.d("onRepeatModeChanged() called with: repeatMode = $repeatMode")
    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        Timber.d("onShuffleModeEnabledChanged() called with: shuffleModeEnabled = $shuffleModeEnabled")
    }

    override fun onPlayerError(error: PlaybackException) {
        // A read-position-out-of-range error is a recoverable seek mishap, not a dead item: a seek
        // computed a byte offset past the end of the stream (e.g. an unknown-length HTTP stream, or a
        // stream whose real length was shorter than assumed). Skipping to the next track here would
        // abandon a perfectly playable song. Instead re-clamp to a safe position on the CURRENT item
        // and re-prepare, once per item so a genuinely broken item can still fall through to a skip.
        if (error.errorCode == PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE &&
            tryRecoverFromOutOfRangeSeek(error)
        ) {
            return
        }

        // Call out an unplayable-format error distinctly so it's obvious in logs that the item can
        // never render on this device (missing codec / unsupported container) — not a transient
        // network blip. Either way we skip past it below so the queue never gets stuck.
        val unplayable = error.errorCode in UNPLAYABLE_FORMAT_ERROR_CODES
        if (unplayable) {
            Timber.e(
                error,
                "Unplayable format for %s (errorCode=%d); skipping",
                player.currentMediaItem?.mediaId, error.errorCode,
            )
        } else {
            Timber.e(error, "onPlayerError() called with: error = $error")
        }

        // Skip-on-error (default on): advance past the broken track. After a fatal error the player is
        // in STATE_IDLE, so it must be re-prepared or it stays frozen on the next item. When the user
        // turns this off, don't auto-advance — leave the queue on the failed item (the player has
        // already stopped) so it doesn't silently jump tracks.
        if (!skipOnError) {
            Timber.d("Skip-on-error disabled; leaving playback stopped on the failed item")
            return
        }
        if (player.hasNextMediaItem()) {
            Timber.d("Skipping to next song due to playback error")
            player.seekToNextMediaItem()
            player.prepare()
        } else {
            Timber.d("No next song available, stopping playback")
            player.stop()
        }
    }

    /**
     * Recover from an out-of-range seek by re-preparing the CURRENT item at a safe position instead
     * of abandoning it. Returns true if recovery was attempted (caller should stop), false to let the
     * normal skip path run.
     *
     * Guarded by [seekRecoveryMediaId] so a single item is recovered at most once between real
     * transitions: if the re-prepare itself errors out, the next [onPlayerError] sees the same
     * mediaId already recorded and falls through to the skip, so there is no infinite loop.
     */
    private fun tryRecoverFromOutOfRangeSeek(error: PlaybackException): Boolean {
        val mediaId = player.currentMediaItem?.mediaId ?: return false
        if (seekRecoveryMediaId == mediaId) {
            Timber.w("Out-of-range seek recurred for %s; giving up and skipping", mediaId)
            return false
        }
        seekRecoveryMediaId = mediaId

        // Land a bit before the end when the duration is known (the overshoot means the true end is at
        // or before this point); otherwise restart the item from the beginning, which is always valid.
        val duration = player.duration
        val safePosition = if (duration != C.TIME_UNSET && duration > SEEK_RECOVERY_END_MARGIN_MS) {
            duration - SEEK_RECOVERY_END_MARGIN_MS
        } else {
            0L
        }
        Timber.w(
            error,
            "Out-of-range seek for %s; re-clamping to %dms and re-preparing instead of skipping",
            mediaId, safePosition,
        )
        player.seekTo(safePosition)
        player.prepare()
        return true
    }

    override fun onSkipSilenceEnabledChanged(skipSilenceEnabled: Boolean) {
        Timber.d("onSkipSilenceEnabledChanged() called with: skipSilenceEnabled = $skipSilenceEnabled")
    }

    override fun onPlaybackStatsReady(
        eventTime: AnalyticsListener.EventTime,
        playbackStats: PlaybackStats
    ) {
        Timber.d("onPlaybackStatsReady() called with: eventTime = $eventTime, playbackStats = $playbackStats")

        // A playback session finished: record how long the track was ACTUALLY playing (excludes
        // pauses/buffering) so Stats reflect real listening instead of a full-duration estimate.
        val listenedMs = playbackStats.totalPlayTimeMs
        if (listenedMs <= 0L) return

        val timeline = eventTime.timeline
        if (timeline.isEmpty || eventTime.windowIndex >= timeline.windowCount) return
        val mediaItem = timeline.getWindow(eventTime.windowIndex, Timeline.Window()).mediaItem

        // Feed the dedicated listening-stats DB from this session-end signal FIRST and independently of
        // the library-play recording below, so a failure in one can never skip the other. The recorder
        // applies its own play threshold (>=30s or >=50% of the track) and de-dupes via this
        // once-per-session callback, so scrubbing / brief skips aren't counted. It reads all metadata
        // off the item's extras (streaming sources included) and writes off the main thread.
        runCatching { playHistoryRecorder.onSessionEnded(mediaItem, listenedMs) }
            .onFailure { Timber.e(it, "Failed to record listening-stats play") }

        // Reuse the SAME once-per-session signal (real listened time) to decide a Last.fm scrobble:
        // the scrobbler applies the Last.fm threshold (>=4min OR >=50%, track >30s) and stamps the
        // scrobble with the track's recorded start time. Independent of the calls above so one failing
        // never skips another. No-ops when not configured/authed/disabled.
        runCatching { lastfmScrobbler.onSessionEnded(mediaItem, listenedMs) }
            .onFailure { Timber.e(it, "Failed to submit Last.fm scrobble") }

        runCatching { MediaId.decode(mediaItem.mediaId) }.getOrNull()?.let { mediaId ->
            lifecycleScope.launch {
                runCatching { mediaLibraryRepository.recordListenedTime(mediaId, listenedMs) }
                    .onFailure { Timber.e(it, "Failed to record listened time for $mediaId") }
            }
        }
    }

    /**
     * Registers a receiver for the (hidden but broadcast) media-volume change action. On each change
     * we re-read STREAM_MUSIC; when "Pause when muted" is on, the stream is at zero, and we're playing,
     * we pause. The receiver is always registered — the toggle is checked per-event via [pauseWhenMuted]
     * — so flipping the setting takes effect immediately without re-registration.
     */
    private fun registerVolumeReceiver() {
        val audioManager = getSystemService(AudioManager::class.java) ?: return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != VOLUME_CHANGED_ACTION) return
                // Only react to the music stream. The extra key is stable across versions even though
                // the action itself is not part of the public SDK.
                val streamType = intent.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, -1)
                if (streamType != AudioManager.STREAM_MUSIC) return
                if (!pauseWhenMuted) return
                val volume = runCatching { audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) }
                    .getOrDefault(-1)
                if (volume == 0 && player.isPlaying) {
                    Timber.d("Media volume muted; pausing playback (pause-when-muted)")
                    player.pause()
                }
            }
        }
        runCatching {
            registerReceiver(receiver, IntentFilter(VOLUME_CHANGED_ACTION))
            volumeReceiver = receiver
        }.onFailure { Timber.w(it, "Failed to register volume receiver") }
    }

    private fun unregisterVolumeReceiver() {
        volumeReceiver?.let { receiver ->
            runCatching { unregisterReceiver(receiver) }
                .onFailure { Timber.w(it, "Failed to unregister volume receiver") }
        }
        volumeReceiver = null
    }

    /**
     * Registers a receiver for Bluetooth audio device connection ([BluetoothDevice.ACTION_ACL_CONNECTED]).
     * When "Resume on Bluetooth" is on and playback is currently paused (has a queue but isn't playing),
     * a connect resumes it. Gated per-event via [resumeOnBluetooth] so the toggle takes effect without
     * re-registration. Uses the already-held BLUETOOTH_CONNECT permission (see the A2DP codec reader).
     */
    private fun registerBluetoothReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != BluetoothDevice.ACTION_ACL_CONNECTED) return
                if (!resumeOnBluetooth) return
                // Resume only when we're stopped/paused mid-session (something is queued but not playing);
                // never start from an empty queue.
                if (!player.isPlaying && player.mediaItemCount > 0) {
                    Timber.d("Bluetooth device connected; resuming playback (resume-on-Bluetooth)")
                    if (player.playbackState == Player.STATE_IDLE) player.prepare()
                    player.play()
                }
            }
        }
        runCatching {
            registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED))
            bluetoothReceiver = receiver
        }.onFailure { Timber.w(it, "Failed to register Bluetooth receiver") }
    }

    private fun unregisterBluetoothReceiver() {
        bluetoothReceiver?.let { receiver ->
            runCatching { unregisterReceiver(receiver) }
                .onFailure { Timber.w(it, "Failed to unregister Bluetooth receiver") }
        }
        bluetoothReceiver = null
    }

    private fun openAudioEffectControlSession() {
        if (isAudioEffectControlSessionOpen) return
        isAudioEffectControlSessionOpen = true

        Timber.d("openAudioEffectControlSession() called")

        val intent = Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
            .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player.audioSessionId)
            .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
        sendBroadcast(intent)
    }

    private fun closeAudioEffectControlSession() {
        if (!isAudioEffectControlSessionOpen) return
        isAudioEffectControlSessionOpen = false

        Timber.d("closeAudioEffectControlSession() called")

        val intent = Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
            .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
            .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player.audioSessionId)
        sendBroadcast(intent)
    }

    private companion object {
        /** How often the crossfade loop re-applies the fade volume while crossfade is active. */
        private const val CROSSFADE_TICK_MS = 100L

        /**
         * System media-volume change broadcast. Not part of the public SDK but has been broadcast by
         * AudioManager for many releases; used (best-effort) to detect the user muting the music stream.
         */
        private const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"

        /** Extra on [VOLUME_CHANGED_ACTION] carrying the affected stream type (AudioManager.STREAM_*). */
        private const val EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"

        /**
         * How far before a known duration to re-clamp when recovering from an out-of-range seek —
         * far enough to stay clear of the (over-assumed) end, small enough to feel like the same spot.
         */
        private const val SEEK_RECOVERY_END_MARGIN_MS = 1_000L

        /**
         * Media3 error codes that mean the item can never decode on this device (missing codec /
         * unsupported container / malformed media), as opposed to a transient network error. Used to
         * log a clearer message before skipping so the queue is never left stuck.
         */
        private val UNPLAYABLE_FORMAT_ERROR_CODES = setOf(
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
        )
    }
}
