package com.viperplayer.data.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.annotation.OptIn
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ServiceLifecycleDispatcher
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessorChain
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.viperplayer.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService(), LifecycleOwner, MediaLibraryService.MediaLibrarySession.Callback, Player.Listener {
    @Inject
    lateinit var viperPlayerResolver: ViperPlayerResolver
    @Inject
    lateinit var viperAnalyticsListener: ViperAnalyticsListener

    private val dispatcher = ServiceLifecycleDispatcher(this)
    override val lifecycle: Lifecycle
        get() = dispatcher.lifecycle

    private lateinit var player: ExoPlayer
    private lateinit var mediaLibrarySession: MediaLibrarySession

    private val mediaMetadata = MutableStateFlow(MediaMetadata.EMPTY)
    
    override fun onCreate() {
        dispatcher.onServicePreSuperOnCreate()
        super.onCreate()

        // Configure MediaNotificationProvider
        setMediaNotificationProvider(createMediaNotificationProvider())
        
        // Create ExoPlayer
        player = createExoPlayer()
        
        // Create MediaSession
        mediaLibrarySession = createMediaLibrarySession()
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
        return mediaLibrarySession
    }
    
    override fun onDestroy() {
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
                addAnalyticsListener(viperAnalyticsListener)
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

                }
        }
    }

    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
        this.mediaMetadata.value = mediaMetadata
    }
}
