package com.viperplayer.data.player

import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.drm.DrmSessionEventListener
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.MediaPeriod
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MediaSourceEventListener
import androidx.media3.exoplayer.upstream.Allocator
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.viperplayer.plugin.v1.StreamSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

class ViperMediaSource(
    private val resolver: ViperPlayerResolver,
    private val base: MediaSource,
    private val dash: MediaSource,
) : MediaSource {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var real: MediaSource

    class Factory(
        private val resolver: ViperPlayerResolver,
        private val base: MediaSource.Factory,
        private val dash: MediaSource.Factory,
    ) : MediaSource.Factory {
        override fun setDrmSessionManagerProvider(drmSessionManagerProvider: DrmSessionManagerProvider): MediaSource.Factory {
            base.setDrmSessionManagerProvider(drmSessionManagerProvider)
            dash.setDrmSessionManagerProvider(drmSessionManagerProvider)
            return this
        }

        override fun setLoadErrorHandlingPolicy(loadErrorHandlingPolicy: LoadErrorHandlingPolicy): MediaSource.Factory {
            base.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
            dash.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
            return this
        }

        override fun getSupportedTypes(): IntArray {
            val base = base.supportedTypes
            val dash = dash.supportedTypes
            return (base + dash).distinct().toIntArray()
        }

        override fun createMediaSource(mediaItem: MediaItem): MediaSource {
            val base = base.createMediaSource(mediaItem)
            val dash = dash.createMediaSource(mediaItem)
            return ViperMediaSource(resolver, base, dash)
        }
    }

    override fun addEventListener(
        handler: Handler,
        eventListener: MediaSourceEventListener
    ) {
        base.addEventListener(handler, eventListener)
        dash.addEventListener(handler, eventListener)
    }

    override fun removeEventListener(eventListener: MediaSourceEventListener) {
        base.removeEventListener(eventListener)
        dash.removeEventListener(eventListener)
    }

    override fun addDrmEventListener(
        handler: Handler,
        eventListener: DrmSessionEventListener
    ) {
        base.addDrmEventListener(handler, eventListener)
        dash.addDrmEventListener(handler, eventListener)
    }

    override fun removeDrmEventListener(eventListener: DrmSessionEventListener) {
        base.removeDrmEventListener(eventListener)
        dash.removeDrmEventListener(eventListener)
    }

    override fun getMediaItem(): MediaItem {
        Timber.d("getMediaItem() called")
        return if (::real.isInitialized) {
            real.mediaItem
        } else {
            base.mediaItem
        }
    }

    override fun prepareSource(
        caller: MediaSource.MediaSourceCaller,
        mediaTransferListener: TransferListener?,
        playerId: PlayerId
    ) {
        val looper = Looper.myLooper() ?: Looper.getMainLooper()
        val handler = Handler(looper)

        scope.launch {
            val uri = mediaItem.localConfiguration?.uri ?: throw IllegalStateException("URI is null")
            val response = resolver.resolve(uri).getOrNull()
            handler.post {
                real = when (response?.type) {
                    StreamSource.Type.URL -> base
                    StreamSource.Type.DASH -> dash
                    else -> throw IllegalStateException("Unknown stream type")
                }
                real.prepareSource(caller, mediaTransferListener, playerId)
            }
        }
    }

    override fun maybeThrowSourceInfoRefreshError() {
        if (::real.isInitialized) {
            real.maybeThrowSourceInfoRefreshError()
        } else {
            base.maybeThrowSourceInfoRefreshError()
        }
    }

    override fun enable(caller: MediaSource.MediaSourceCaller) {
        real.enable(caller)
    }

    override fun createPeriod(
        id: MediaSource.MediaPeriodId,
        allocator: Allocator,
        startPositionUs: Long
    ): MediaPeriod {
        return real.createPeriod(id, allocator, startPositionUs)
    }

    override fun releasePeriod(mediaPeriod: MediaPeriod) {
        real.releasePeriod(mediaPeriod)
    }

    override fun disable(caller: MediaSource.MediaSourceCaller) {
        real.disable(caller)
    }

    override fun releaseSource(caller: MediaSource.MediaSourceCaller) {
        real.releaseSource(caller)
    }
}