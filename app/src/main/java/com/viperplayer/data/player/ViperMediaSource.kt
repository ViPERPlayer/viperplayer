package com.viperplayer.data.player

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
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
import com.viperplayer.data.source.PluginDataSource
import com.viperplayer.domain.model.MediaId
import com.viperplayer.plugin.v1.StreamSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException

class ViperMediaSource(
    private val context: Context,
    private val pluginDataSource: PluginDataSource,
    private val defaultMediaSource: MediaSource,
    private val dashMediaSource: DashMediaSource,
) : MediaSource {
    private val sourceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var chosenMediaSource: MediaSource? = null
    private val chosenOrDefaultMediaSource: MediaSource
        get() = chosenMediaSource ?: defaultMediaSource
    private var sourceInfoRefreshError: Exception? = null

    class Factory(
        private val context: Context,
        private val pluginDataSource: PluginDataSource,
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
            return ViperMediaSource(context, pluginDataSource, defaultMediaSource, dashMediaSource)
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

    override fun getInitialTimeline(): Timeline? {
        return chosenOrDefaultMediaSource.initialTimeline
    }

    override fun isSingleWindow(): Boolean {
        return chosenOrDefaultMediaSource.isSingleWindow
    }

    override fun getMediaItem(): MediaItem {
        return chosenOrDefaultMediaSource.mediaItem
    }

    override fun canUpdateMediaItem(mediaItem: MediaItem): Boolean {
        return chosenOrDefaultMediaSource.canUpdateMediaItem(mediaItem)
    }

    override fun updateMediaItem(mediaItem: MediaItem) {
        chosenOrDefaultMediaSource.updateMediaItem(mediaItem)
    }

    @OptIn(UnstableApi::class)
    override fun prepareSource(
        caller: MediaSource.MediaSourceCaller,
        mediaTransferListener: TransferListener?,
        playerId: PlayerId
    ) {
        Timber.d("prepareSource() called")
        val playbackLooper = Looper.myLooper() ?: throw IllegalStateException("playbackLooper is null!")
        val playbackDispatcher = Handler(playbackLooper).asCoroutineDispatcher()

        sourceScope.launch {
            try {
                val mediaId = MediaId.fromString(mediaItem.mediaId)
                val stream = pluginDataSource.getStream(mediaId).getOrThrow()

                val extras = Bundle().apply {
                    mediaItem.mediaMetadata.extras?.let { putAll(it) }
                    stream.replayGainDb?.let { putFloat("replayGainDb", it) }
                    stream.peakAmplitude?.let { putFloat("peakAmplitude", it) }
                }

                val updatedMediaItemBuilder = mediaItem.buildUpon()
                    .setMediaMetadata(
                        mediaItem.mediaMetadata.buildUpon()
                            .setExtras(extras)
                            .build()
                    )

                    when (stream.type) {
                        StreamSource.Type.URL -> {
                            val url = stream.url ?: throw IllegalArgumentException("URL is null")
                            updatedMediaItemBuilder.setUri(url.toUri())
                        }
                        StreamSource.Type.DASH -> {
                            val xml = stream.dashXml ?: throw IllegalArgumentException("DASH XML is null")
                            val dashUri = saveDashXmlToFile(xml) // Assuming this is a blocking IO function
                            updatedMediaItemBuilder.setUri(dashUri)
                        }
                        StreamSource.Type.AUDIO_STREAM -> {
                            val audioStream = stream.audioStream ?: throw IllegalArgumentException("Audio stream is null")
                            updatedMediaItemBuilder.setUri("viper://stream/${audioStream.streamId}".toUri())
                        }
                        else -> throw IllegalArgumentException("Unknown stream type: ${stream.type}")
                    }

                    val updatedMediaItem = updatedMediaItemBuilder.build()

                    withContext(playbackDispatcher) {
                        chosenMediaSource = when (stream.type) {
                            StreamSource.Type.URL -> {
                                defaultMediaSource.updateMediaItem(updatedMediaItem)
                                defaultMediaSource
                            }

                            StreamSource.Type.DASH -> {
                                dashMediaSource.updateMediaItem(updatedMediaItem)
                                dashMediaSource.replaceManifestUri(updatedMediaItem.localConfiguration!!.uri)
                                dashMediaSource
                            }

                            StreamSource.Type.AUDIO_STREAM -> {
                                sourceInfoRefreshError = UnsupportedOperationException("Audio stream not supported")
                                return@withContext
                            }

                            else -> {
                                sourceInfoRefreshError = IllegalArgumentException("Unknown stream type: ${stream.type}")
                                return@withContext
                            }
                        }
                    chosenOrDefaultMediaSource.prepareSource(caller, mediaTransferListener, playerId)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to prepare source")
                sourceInfoRefreshError = IOException("Failed to prepare source")
            }
        }
    }

    override fun maybeThrowSourceInfoRefreshError() {
        sourceInfoRefreshError?.let { throw it }
        chosenOrDefaultMediaSource.maybeThrowSourceInfoRefreshError()
    }

    override fun enable(caller: MediaSource.MediaSourceCaller) {
        chosenOrDefaultMediaSource.enable(caller)
    }

    override fun createPeriod(
        id: MediaSource.MediaPeriodId,
        allocator: Allocator,
        startPositionUs: Long
    ): MediaPeriod {
        return chosenOrDefaultMediaSource.createPeriod(id, allocator, startPositionUs)
    }

    override fun releasePeriod(mediaPeriod: MediaPeriod) {
        chosenOrDefaultMediaSource.releasePeriod(mediaPeriod)
    }

    override fun disable(caller: MediaSource.MediaSourceCaller) {
        chosenOrDefaultMediaSource.disable(caller)
    }

    override fun releaseSource(caller: MediaSource.MediaSourceCaller) {
        defaultMediaSource.releaseSource(caller)
        dashMediaSource.releaseSource(caller)
        sourceScope.cancel()
    }

    /**
     * Save DASH XML content to a temporary file and return the file URI.
     */
    private fun saveDashXmlToFile(dashXml: String): Uri {
        val dashDir = context.cacheDir.resolve("dash_manifests").apply {
            if (!exists()) mkdirs()
        }

        // Generate unique filename based on current time to avoid collisions
        val filename = "manifest_${System.currentTimeMillis()}.mpd"
        val file = File(dashDir, filename)

        // Write DASH XML to file
        file.writeText(dashXml)

        Timber.d("saveDashXmlToFile: DASH XML saved to: ${file.absolutePath}")

        return file.toUri()
    }
}