package com.viperplayer.data.player

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.DrmSessionEventListener
import androidx.media3.exoplayer.drm.DrmSessionManager
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaPeriod
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MediaSourceEventListener
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.Allocator
import androidx.media3.exoplayer.upstream.BandwidthMeter
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.viperplayer.data.source.PluginDataSource
import com.viperplayer.domain.model.MediaId
import com.viperplayer.plugin.model.DashStream
import com.viperplayer.plugin.model.DrmConfig
import com.viperplayer.plugin.model.HlsStream
import com.viperplayer.plugin.model.PcmStream
import com.viperplayer.plugin.model.UnknownStream
import com.viperplayer.plugin.model.UrlStream
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
import java.util.UUID

/**
 * A [MediaSource] that lazily resolves the real stream for a plugin-backed [MediaItem] (URL, DASH,
 * HLS, or raw PCM over an FD) and delegates to the matching concrete source. The host owns the
 * player; the plugin only tells it where/how to get the audio.
 */
class ViperMediaSource(
    private val context: Context,
    private val pluginDataSource: PluginDataSource,
    private val defaultMediaSource: MediaSource,
    private val dashMediaSource: DashMediaSource,
    private val defaultMediaSourceFactory: DefaultMediaSourceFactory,
    private val dataSourceFactory: DataSource.Factory,
) : MediaSource {
    private val sourceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var chosenMediaSource: MediaSource? = null
    private val chosenOrDefaultMediaSource: MediaSource
        get() = chosenMediaSource ?: defaultMediaSource

    // Written from the IO resolution coroutine, read on the playback thread.
    @Volatile
    private var sourceInfoRefreshError: Exception? = null

    class Factory(
        private val context: Context,
        private val pluginDataSource: PluginDataSource,
        private val defaultMediaSourceFactory: DefaultMediaSourceFactory,
        private val dashMediaSourceFactory: DashMediaSource.Factory,
        private val dataSourceFactory: DataSource.Factory,
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
            return ViperMediaSource(
                context, pluginDataSource, defaultMediaSource, dashMediaSource, defaultMediaSourceFactory,
                dataSourceFactory,
            )
        }
    }

    override fun addEventListener(handler: Handler, eventListener: MediaSourceEventListener) {
        defaultMediaSource.addEventListener(handler, eventListener)
        dashMediaSource.addEventListener(handler, eventListener)
    }

    override fun removeEventListener(eventListener: MediaSourceEventListener) {
        defaultMediaSource.removeEventListener(eventListener)
        dashMediaSource.removeEventListener(eventListener)
    }

    override fun addDrmEventListener(handler: Handler, eventListener: DrmSessionEventListener) {
        defaultMediaSource.addDrmEventListener(handler, eventListener)
        dashMediaSource.addDrmEventListener(handler, eventListener)
    }

    override fun removeDrmEventListener(eventListener: DrmSessionEventListener) {
        defaultMediaSource.removeDrmEventListener(eventListener)
        dashMediaSource.removeDrmEventListener(eventListener)
    }

    override fun getInitialTimeline(): Timeline? = chosenOrDefaultMediaSource.initialTimeline

    override fun isSingleWindow(): Boolean = chosenOrDefaultMediaSource.isSingleWindow

    override fun getMediaItem(): MediaItem = chosenOrDefaultMediaSource.mediaItem

    override fun canUpdateMediaItem(mediaItem: MediaItem): Boolean =
        chosenOrDefaultMediaSource.canUpdateMediaItem(mediaItem)

    override fun updateMediaItem(mediaItem: MediaItem) {
        chosenOrDefaultMediaSource.updateMediaItem(mediaItem)
    }

    override fun prepareSource(
        caller: MediaSource.MediaSourceCaller,
        playerId: PlayerId,
        bandwidthMeter: BandwidthMeter,
    ) {
        val playbackLooper = Looper.myLooper() ?: throw IllegalStateException("playbackLooper is null!")
        val playbackDispatcher = Handler(playbackLooper).asCoroutineDispatcher()

        sourceScope.launch {
            try {
                val mediaId = MediaId.decode(mediaItem.mediaId)
                    ?: throw IllegalStateException("Unresolvable mediaId: ${mediaItem.mediaId}")
                val isVideo = mediaItem.mediaMetadata.extras?.getBoolean("isVideo") == true
                val resolved = pluginDataSource.getStream(mediaId, isVideo).getOrThrow()
                val source = resolved.source
                Timber.i(
                    "ViperMediaSource resolved %s for %s%s",
                    source::class.simpleName, mediaId,
                    if (source is UrlStream) " (headers=${source.headers.keys})" else "",
                )

                val extras = Bundle().apply {
                    mediaItem.mediaMetadata.extras?.let { putAll(it) }
                    source.replayGainDb?.let { putFloat("replayGainDb", it) }
                    source.peakAmplitude?.let { putFloat("peakAmplitude", it) }
                    source.albumReplayGainDb?.let { putFloat("albumReplayGainDb", it) }
                    source.albumPeakAmplitude?.let { putFloat("albumPeakAmplitude", it) }
                }
                val baseBuilder = mediaItem.buildUpon().setMediaMetadata(
                    mediaItem.mediaMetadata.buildUpon().setExtras(extras).build()
                )

                val prepared: MediaSource = when (source) {
                    is UrlStream -> {
                        val item = baseBuilder.setUri(source.url.toUri()).build()
                        if (source.headers.isEmpty()) {
                            defaultMediaSource.apply { updateMediaItem(item) }
                        } else {
                            // Some providers allowlist by Origin (e.g. a proxy 403s without
                            // it), so the plugin-supplied request headers must ride along on the actual
                            // media fetch — not just resolution. Wrap the cache data source so every
                            // request for this item carries them.
                            val headerFactory = ResolvingDataSource.Factory(dataSourceFactory) { dataSpec ->
                                dataSpec.withRequestHeaders(source.headers)
                            }
                            ProgressiveMediaSource.Factory(headerFactory).createMediaSource(item)
                        }
                    }

                    is HlsStream -> {
                        // HLS always needs a fresh source from the factory: the pre-created
                        // `defaultMediaSource` is progressive and updateMediaItem() can't change a
                        // source's type to HLS; and an HlsMediaSource captures any DrmSessionManager at
                        // construction. The factory picks HlsMediaSource from the .m3u8 and, for DRM
                        // tracks (Widevine), its default DrmSessionManagerProvider reads the
                        // DrmConfiguration and POSTs key requests to the license URL. Needs the media3
                        // HLS module at runtime.
                        val builder = baseBuilder.setUri(source.url.toUri())
                        source.drm?.licenseUrl?.let { licenseUrl ->
                            builder.setDrmConfiguration(
                                MediaItem.DrmConfiguration.Builder(drmSchemeUuid(source.drm!!.scheme))
                                    .setLicenseUri(licenseUrl)
                                    .setLicenseRequestHeaders(source.drm!!.licenseHeaders)
                                    .build()
                            )
                        }
                        defaultMediaSourceFactory.createMediaSource(builder.build())
                    }

                    is DashStream -> {
                        val uri: Uri = source.manifest?.let { saveDashXmlToFile(it) }
                            ?: source.manifestUrl?.toUri()
                            ?: throw IllegalArgumentException("DASH stream has no manifest or URL")
                        val item = baseBuilder.setUri(uri).build()
                        val clearKeyDrm = source.drm?.takeIf { it.clearKeys.isNotEmpty() }
                        if (clearKeyDrm != null) {
                            // Inline ClearKey (e.g. a single-file CENC MP4 the plugin holds the key for).
                            // The pre-built `dashMediaSource` fixed its DrmSessionManager at construction and
                            // can't be switched to DRM via updateMediaItem, so build a fresh DASH source whose
                            // DrmSessionManager serves the keys locally (no network license request).
                            val drmSessionManager = clearKeyDrmSessionManager(clearKeyDrm.clearKeys)
                            DashMediaSource.Factory(dataSourceFactory)
                                .setDrmSessionManagerProvider { drmSessionManager }
                                .createMediaSource(item)
                        } else {
                            dashMediaSource.apply {
                                updateMediaItem(item)
                                replaceManifestUri(uri)
                            }
                        }
                    }

                    is PcmStream -> {
                        val fd = resolved.fd
                            ?: throw IllegalArgumentException("PCM stream is missing its descriptor")
                        val item = baseBuilder.setUri("viper://pcm/${source.streamId}".toUri()).build()
                        ProgressiveMediaSource.Factory(
                            PcmStreamDataSource.Factory(fd, source.format, source.durationMs)
                        ).createMediaSource(item)
                    }

                    is UnknownStream -> throw IllegalArgumentException(
                        "Plugin returned a stream type this host version doesn't support"
                    )
                }

                withContext(playbackDispatcher) {
                    chosenMediaSource = prepared
                    prepared.prepareSource(caller, playerId, bandwidthMeter)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to prepare source")
                sourceInfoRefreshError = IOException("Failed to prepare source", e)
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
        startPositionUs: Long,
    ): MediaPeriod = chosenOrDefaultMediaSource.createPeriod(id, allocator, startPositionUs)

    override fun releasePeriod(mediaPeriod: MediaPeriod) {
        chosenOrDefaultMediaSource.releasePeriod(mediaPeriod)
    }

    override fun disable(caller: MediaSource.MediaSourceCaller) {
        chosenOrDefaultMediaSource.disable(caller)
    }

    override fun releaseSource(caller: MediaSource.MediaSourceCaller) {
        // Cancel any in-flight resolution first so it can't prepare a source after release.
        sourceScope.cancel()
        // Only the chosen source was ever prepared; releasing default/dash/pcm unconditionally
        // would be an unbalanced releaseSource() (Media3 requires one release per prepare).
        chosenMediaSource?.releaseSource(caller)
    }

    /** Map a plugin [com.viperplayer.plugin.model.DrmConfig] scheme name to its content-protection UUID. */
    private fun drmSchemeUuid(scheme: String): UUID = when (scheme.lowercase()) {
        "widevine" -> C.WIDEVINE_UUID
        "playready" -> C.PLAYREADY_UUID
        "clearkey" -> C.CLEARKEY_UUID
        else -> throw IllegalArgumentException("Unsupported DRM scheme: $scheme")
    }

    /**
     * A local (offline) ClearKey [DrmSessionManager] that serves the plugin-supplied keys directly,
     * with no network license request. [clearKeys] is `keyId -> key`, both lowercase hex; we convert
     * to the W3C ClearKey license-response JSON (base64url key/kid) that a [LocalMediaDrmCallback]
     * hands back for every key request.
     */
    private fun clearKeyDrmSessionManager(clearKeys: Map<String, String>): DrmSessionManager {
        val keyObjects = clearKeys.entries.joinToString(",") { (kid, key) ->
            """{"kty":"oct","kid":"${hexToBase64Url(kid)}","k":"${hexToBase64Url(key)}"}"""
        }
        val response = """{"keys":[$keyObjects],"type":"temporary"}"""
        return DefaultDrmSessionManager.Builder()
            .setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
            .setMultiSession(false)
            .build(LocalMediaDrmCallback(response.toByteArray(Charsets.UTF_8)))
    }

    /** Convert a hex string (16-byte key/kid) to unpadded base64url, as ClearKey JSON requires. */
    private fun hexToBase64Url(hex: String): String {
        val clean = hex.trim().removePrefix("0x").replace("-", "")
        val bytes = ByteArray(clean.length / 2) { i ->
            ((Character.digit(clean[i * 2], 16) shl 4) + Character.digit(clean[i * 2 + 1], 16)).toByte()
        }
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun saveDashXmlToFile(dashXml: String): Uri {
        val dashDir = context.cacheDir.resolve("dash_manifests").apply { if (!exists()) mkdirs() }
        val file = File(dashDir, "manifest_${System.currentTimeMillis()}.mpd")
        file.writeText(dashXml)
        return file.toUri()
    }
}
