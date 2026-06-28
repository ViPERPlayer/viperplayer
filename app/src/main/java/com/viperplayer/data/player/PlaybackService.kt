package com.viperplayer.data.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
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
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessorChain
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory
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
import com.viperplayer.data.source.PluginDataSource
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.RepeatMode
import com.viperplayer.domain.repository.MediaLibraryRepository
import com.viperplayer.domain.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.min
import kotlin.math.pow

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

    private val dispatcher = ServiceLifecycleDispatcher(this)
    override val lifecycle: Lifecycle
        get() = dispatcher.lifecycle

    private lateinit var player: ExoPlayer
    private lateinit var mediaLibrarySession: MediaLibrarySession

    private val mediaMetadata = MutableStateFlow(MediaMetadata.EMPTY)

    private var isAudioEffectControlSessionOpen = false

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
    }

    /**
     * Restores the player state from persistence.
     * Songs are loaded from database with full metadata (artists, album, etc.).
     */
    private fun restorePlayerState() {
        lifecycleScope.launch {
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

    override fun onDestroy() {
        Timber.d("onDestroy() called")
        dispatcher.onServicePreSuperOnDestroy()
        // Close the global audio-effect session before releasing the player, otherwise it stays
        // registered with system equalizer apps (leak).
        closeAudioEffectControlSession()
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

    private fun createExoPlayer(): ExoPlayer {
        return ExoPlayer.Builder(this)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setMediaSourceFactory(createExoPlayerMediaSourceFactory())
            .setRenderersFactory(createExoPlayerRenderersFactory())
            .build()
            .apply {
                addListener(this@PlaybackService)
                addAnalyticsListener(PlaybackStatsListener(false, this@PlaybackService))
            }
    }

    private fun createExoPlayerMediaSourceFactory(): MediaSource.Factory {
        val dataSourceFactory = createExoPlayerDataSourceFactory()
        val base = DefaultMediaSourceFactory(dataSourceFactory)
        val dash = DashMediaSource.Factory(dataSourceFactory)
        return ViperMediaSource.Factory(this, pluginDataSource, base, dash)
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
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        Timber.d("onMediaItemTransition() called with: mediaItem = $mediaItem, reason = $reason")

        // Record a play whenever a new media item starts (manual, skip, or auto-advance): bumps the
        // song's play count/lastPlayed and inserts a per-play event that powers History and Stats.
        // The song is persisted before playback begins (saveSong precedes setMediaItem), so the
        // event's foreign key resolves; if it somehow isn't, the repository simply skips the event.
        if (mediaItem != null) {
            MediaId.fromString(mediaItem.mediaId)?.let { mediaId ->
                lifecycleScope.launch {
                    runCatching { mediaLibraryRepository.incrementSongPlayCount(mediaId) }
                        .onFailure { Timber.e(it, "Failed to record play for $mediaId") }
                }
            }
        }

        // Apply ReplayGain as volume when a new media item starts playing
        // Convert from dB to linear: linear = 10^(dB/20)
        if (mediaItem != null) {
            // TODO: Move to flow in order to observe replayGainEnabled and replayGainPreampDb
            val replayGainDb = mediaItem.mediaMetadata.extras?.getFloat("replayGainDb")
            val peakAmplitude = mediaItem.mediaMetadata.extras?.getFloat("peakAmplitude")

            // Check if ReplayGain is enabled
            lifecycleScope.launch {
                val replayGainEnabled = settingsRepository.replayGainEnabled.first()

                val volume = if (replayGainEnabled && replayGainDb != null) {
                    // Get preamp from settings and add it to ReplayGain
                    val preampDb = settingsRepository.replayGainPreampDb.first()
                    val finalGainDb = replayGainDb + preampDb

                    // Convert from dB to linear: linear = 10^(dB/20)
                    val replayGain = if (finalGainDb == 0f) {
                        1.0f // 0 dB = 1.0 linear
                    } else {
                        10f.pow(finalGainDb / 20f)
                    }

                    // Apply peak amplitude limiting if available
                    if (peakAmplitude != null && peakAmplitude > 0f) {
                        min(replayGain, 1f / peakAmplitude)
                    } else {
                        replayGain
                    }
                } else {
                    1f
                }

                Timber.d("onMediaItemTransition: volume=$volume (replayGainEnabled=$replayGainEnabled, replayGainDb=$replayGainDb, preampDb=${settingsRepository.replayGainPreampDb.first()}, peakAmplitude=$peakAmplitude)")

                player.volume = volume.coerceIn(0f, 1f)
            }
        }
    }

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
        Timber.e(error, "onPlayerError() called with: error = $error")

        // Try to skip to next song if available. After a fatal error the player is in STATE_IDLE,
        // so it must be re-prepared or it stays frozen on the next item.
        if (player.hasNextMediaItem()) {
            Timber.d("Skipping to next song due to playback error")
            player.seekToNextMediaItem()
            player.prepare()
        } else {
            Timber.d("No next song available, stopping playback")
            player.stop()
        }
    }

    override fun onSkipSilenceEnabledChanged(skipSilenceEnabled: Boolean) {
        Timber.d("onSkipSilenceEnabledChanged() called with: skipSilenceEnabled = $skipSilenceEnabled")
    }

    override fun onPlaybackStatsReady(
        eventTime: AnalyticsListener.EventTime,
        playbackStats: PlaybackStats
    ) {
        Timber.d("onPlaybackStatsReady() called with: eventTime = $eventTime, playbackStats = $playbackStats")
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
}
