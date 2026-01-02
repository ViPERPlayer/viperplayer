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
import androidx.media3.common.audio.AudioProcessorChain
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.PlaybackStats
import androidx.media3.exoplayer.analytics.PlaybackStatsListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.viperplayer.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : MediaLibraryService(), LifecycleOwner, MediaLibraryService.MediaLibrarySession.Callback, Player.Listener, PlaybackStatsListener.Callback {
    @Inject
    lateinit var viperPlayerResolver: ViperPlayerResolver

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

        player.addMediaItem(
            MediaItem.Builder()
                // PELIGROSA
                .setUri("https://files.catbox.moe/umacii.flac")
                .build()
        )
        player.addMediaItem(
            MediaItem.Builder()
                // Gata Only
                .setUri("https://files.catbox.moe/e05tep.flac")
                .build()
        )
        player.addMediaItem(
            MediaItem.Builder()
                // Uptown Funk
                .setUri("https://files.catbox.moe/xl4c54.flac")
                .build()
        )
        player.addMediaItem(
            MediaItem.Builder()
                // Perfect (Exceeder)
                .setUri("https://files.catbox.moe/9axqe4.flac")
                .build()
        )
        player.addMediaItem(
            MediaItem.Builder()
                // Alejandro
                .setUri("https://files.catbox.moe/ul596p.flac")
                .build()
        )
        player.addMediaItem(
            MediaItem.Builder()
                // Telephone
                .setUri("https://files.catbox.moe/9cd4t7.flac")
                .build()
        )
        player.addMediaItem(
            MediaItem.Builder()
                // Just Dance
                .setUri("https://files.catbox.moe/nqk69n.flac")
                .build()
        )
//        player.shuffleModeEnabled = true
        player.prepare()
        player.playWhenReady = true
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
        mediaLibrarySession.release()
        player.release()
        super.onDestroy()
    }

    private fun createMediaNotificationProvider(): MediaNotification.Provider {
        return DefaultMediaNotificationProvider.Builder(this)
            .build()
            .apply {
                setSmallIcon(R.drawable.ic_launcher_foreground)
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
        return DefaultMediaSourceFactory(
            createExoPlayerDataSourceFactory()
        )
    }

    private fun createExoPlayerDataSourceFactory(): DataSource.Factory {
        return ResolvingDataSource.Factory(
            DefaultDataSource.Factory(this),
            viperPlayerResolver
        )
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
    }

    private fun createAudioProcessorChain(): AudioProcessorChain {
        return DefaultAudioSink.DefaultAudioProcessorChain()
    }

    private fun createMediaLibrarySession(): MediaLibrarySession {
        return MediaLibrarySession.Builder(this, player, this)
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
                            CommandButton.Builder()
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
        )) {
            val isPlaying = (player.playbackState == Player.STATE_BUFFERING || player.playbackState == Player.STATE_READY)
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
        Timber.d("onPlayerError() called with: error = $error")
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
