package com.viperplayer.data.player

import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.drm.DrmSessionEventListener
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaPeriod
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MediaSourceEventListener
import androidx.media3.exoplayer.upstream.Allocator
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.viperplayer.plugin.v1.StreamSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

class ViperMediaSource(
    private val resolver: ViperPlayerResolver,
    private val defaultMediaSource: MediaSource,
    private val dashMediaSource: MediaSource,
) : MediaSource {
    private lateinit var chosenMediaSource: MediaSource

    class Factory(
        private val resolver: ViperPlayerResolver,
        private val defaultMediaSourceFactory: DefaultMediaSourceFactory,
        private val dashMediaSourceFactory: DashMediaSource.Factory,
    ) : MediaSource.Factory {
        override fun setDrmSessionManagerProvider(drmSessionManagerProvider: DrmSessionManagerProvider): MediaSource.Factory {
            defaultMediaSourceFactory.setDrmSessionManagerProvider(drmSessionManagerProvider)
            dashMediaSourceFactory.setDrmSessionManagerProvider(drmSessionManagerProvider)
            return this
        }

        override fun setLoadErrorHandlingPolicy(loadErrorHandlingPolicy: LoadErrorHandlingPolicy): MediaSource.Factory {
            defaultMediaSourceFactory.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
            dashMediaSourceFactory.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
            return this
        }

        override fun getSupportedTypes(): IntArray {
            val base = defaultMediaSourceFactory.supportedTypes
            val dash = dashMediaSourceFactory.supportedTypes
            return (base + dash).distinct().toIntArray()
        }

        override fun createMediaSource(mediaItem: MediaItem): MediaSource {
            val defaultMediaSource = defaultMediaSourceFactory.createMediaSource(mediaItem)
            val dashMediaSource = dashMediaSourceFactory.createMediaSource(mediaItem)
            return ViperMediaSource(resolver, defaultMediaSource, dashMediaSource)
        }
    }

    override fun addEventListener(
        handler: Handler,
        eventListener: MediaSourceEventListener
    ) {
        defaultMediaSource.addEventListener(handler, eventListener)
        dashMediaSource.addEventListener(handler, eventListener)
    }

    override fun removeEventListener(eventListener: MediaSourceEventListener) {
        defaultMediaSource.removeEventListener(eventListener)
        dashMediaSource.removeEventListener(eventListener)
    }

    override fun addDrmEventListener(
        handler: Handler,
        eventListener: DrmSessionEventListener
    ) {
        defaultMediaSource.addDrmEventListener(handler, eventListener)
        dashMediaSource.addDrmEventListener(handler, eventListener)
    }

    override fun removeDrmEventListener(eventListener: DrmSessionEventListener) {
        defaultMediaSource.removeDrmEventListener(eventListener)
        dashMediaSource.removeDrmEventListener(eventListener)
    }

    override fun getMediaItem(): MediaItem {
        Timber.d("getMediaItem() called")
        return if (::chosenMediaSource.isInitialized) {
            chosenMediaSource.mediaItem
        } else {
            defaultMediaSource.mediaItem
        }
    }

    override fun prepareSource(
        caller: MediaSource.MediaSourceCaller,
        mediaTransferListener: TransferListener?,
        playerId: PlayerId
    ) {
        val exoPlayerLooper = Looper.myLooper() ?: Looper.getMainLooper()
        CoroutineScope(Dispatchers.Default).launch {
            val uri = mediaItem.localConfiguration?.uri ?: throw IllegalStateException("URI is null")
            val response = resolver.resolve(uri)
            Handler(exoPlayerLooper).post {
                chosenMediaSource = response.getOrThrow().let { source ->
                    when (source.type) {
                        StreamSource.Type.URL -> defaultMediaSource
                        StreamSource.Type.DASH -> dashMediaSource
                        StreamSource.Type.AUDIO_STREAM -> throw UnsupportedOperationException("Audio stream not supported")
                    }
                }
                chosenMediaSource.prepareSource(caller, mediaTransferListener, playerId)
            }
        }
    }

    override fun maybeThrowSourceInfoRefreshError() {
        if (::chosenMediaSource.isInitialized) {
            chosenMediaSource.maybeThrowSourceInfoRefreshError()
        } else {
            defaultMediaSource.maybeThrowSourceInfoRefreshError()
        }
    }

    override fun enable(caller: MediaSource.MediaSourceCaller) {
        chosenMediaSource.enable(caller)
    }

    override fun createPeriod(
        id: MediaSource.MediaPeriodId,
        allocator: Allocator,
        startPositionUs: Long
    ): MediaPeriod {
        return chosenMediaSource.createPeriod(id, allocator, startPositionUs)
    }

    override fun releasePeriod(mediaPeriod: MediaPeriod) {
        chosenMediaSource.releasePeriod(mediaPeriod)
    }

    override fun disable(caller: MediaSource.MediaSourceCaller) {
        chosenMediaSource.disable(caller)
    }

    override fun releaseSource(caller: MediaSource.MediaSourceCaller) {
        chosenMediaSource.releaseSource(caller)
    }
}