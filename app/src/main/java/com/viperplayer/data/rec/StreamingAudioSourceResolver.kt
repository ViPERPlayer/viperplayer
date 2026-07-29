package com.viperplayer.data.rec

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import com.viperplayer.data.source.PluginDataSource
import com.viperplayer.domain.model.MediaId
import com.viperplayer.plugin.model.DashStream
import com.viperplayer.plugin.model.HlsStream
import com.viperplayer.plugin.model.PcmStream
import com.viperplayer.plugin.model.StreamSource
import com.viperplayer.plugin.model.UnknownStream
import com.viperplayer.plugin.model.UrlStream
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves + decodes the head of a STREAMING-only song's live stream to mono PCM for the background
 * embedder ([StreamingIndexWorker], Path B). Abstracted behind an interface so the worker's paging /
 * embed orchestration is unit-testable with a fake — the production resolver touches the plugin binder,
 * `MediaExtractor`/`MediaCodec` and (for HLS/DASH) a headless ExoPlayer, all device-only.
 */
interface StreamingAudioSource {
    /** Decode ~10s of [mediaId]'s stream to PCM, or null when unavailable/undecodable/DRM-skipped. */
    suspend fun decode(mediaId: MediaId): DecodedAudio?
}

/**
 * The decode path a resolved [StreamSource] takes, or why it is skipped. Pulled out as a PURE (Android-
 * free) classifier so the per-type dispatch + DRM-skip policy is unit-testable on the JVM without a
 * device, codec or plugin binder (the actual decode execution is device-only). One value per branch of
 * [StreamingAudioSourceResolver.decode].
 */
enum class StreamDecodePlan {
    /** Progressive http(s): decode via MediaExtractor with the stream's request headers. */
    URL,
    /** Raw PCM over a live FD: decode the FD. */
    PCM,
    /** Non-DRM HLS: headless ExoPlayer decode. */
    HLS,
    /** Non-DRM DASH: headless ExoPlayer decode. */
    DASH,
    /** A DRM-protected HLS/DASH stream — skipped here (Path A / playback capture covers DRM). */
    SKIP_DRM,
    /** A stream kind this host doesn't understand, or a DASH with neither manifest nor URL. */
    SKIP_UNSUPPORTED,
}

/** Pure classifier for [StreamDecodePlan]; see [StreamingAudioSourceResolver] for the execution. */
object StreamDecodePlanner {
    fun plan(source: StreamSource): StreamDecodePlan = when (source) {
        is UrlStream -> StreamDecodePlan.URL
        is PcmStream -> StreamDecodePlan.PCM
        is HlsStream -> if (source.drm != null) StreamDecodePlan.SKIP_DRM else StreamDecodePlan.HLS
        is DashStream -> when {
            // Inline ClearKey OR a license server both mean DRM: skip in the background path.
            source.drm?.clearKeys?.isNotEmpty() == true || source.drm?.licenseUrl != null ->
                StreamDecodePlan.SKIP_DRM
            source.manifest == null && source.manifestUrl == null -> StreamDecodePlan.SKIP_UNSUPPORTED
            else -> StreamDecodePlan.DASH
        }
        is UnknownStream -> StreamDecodePlan.SKIP_UNSUPPORTED
    }
}

/**
 * Production [StreamingAudioSource]. Resolves the stream via [PluginDataSource.getStream] and dispatches
 * by [com.viperplayer.plugin.model.StreamSource] type, mirroring how [com.viperplayer.data.player.ViperMediaSource]
 * maps each kind to a media3 source:
 *
 *  - **UrlStream** — progressive http(s): decode via `MediaExtractor.setDataSource(context, uri, headers)`
 *    (the plugin's request headers ride along, as some providers allowlist by `Origin`/auth). Reuses the
 *    shared [AudioDecoder].
 *  - **PcmStream** — raw PCM over a live FD: decode the FD directly. The host owns + closes the FD.
 *  - **HlsStream / DashStream (NON-DRM only)** — adaptive manifests a plain `MediaExtractor` can't handle:
 *    build the same media3 [MediaSource] `ViperMediaSource` would (progressive-factory HLS / DASH factory)
 *    and hand it to the headless [ExoPlayerAudioClipDecoder]. **DRM streams are SKIPPED here** (a DASH
 *    stream carrying `clearKeys`, or an HLS stream with a `drm` config) — Path A (playback capture) covers
 *    DRM, since offline ClearKey-decrypt-to-PCM in a background worker is not attempted.
 *  - **UnknownStream** — a newer stream kind this host doesn't understand: skipped.
 *
 * Never throws for one song: any failure returns null and the worker skips it.
 */
@OptIn(UnstableApi::class)
@Singleton
class StreamingAudioSourceResolver @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val pluginDataSource: PluginDataSource,
    private val audioDecoder: AudioDecoder,
) : StreamingAudioSource {

    /** Lazily built so a JVM instantiation (should any occur) doesn't touch media3 factories eagerly. */
    private val dataSourceFactory: DataSource.Factory by lazy { DefaultDataSource.Factory(context) }
    private val clipDecoder: ExoPlayerAudioClipDecoder by lazy { ExoPlayerAudioClipDecoder(context) }

    override suspend fun decode(mediaId: MediaId): DecodedAudio? {
        val resolved = pluginDataSource.getStream(mediaId, isVideo = false).getOrNull() ?: run {
            Timber.d("StreamingAudioSource: no stream resolved for $mediaId")
            return null
        }
        val source = resolved.source
        return when (StreamDecodePlanner.plan(source)) {
            StreamDecodePlan.URL -> decodeUrl(source as UrlStream)
            StreamDecodePlan.PCM -> decodePcm(resolved.fd)
            StreamDecodePlan.HLS -> decodeAdaptive(buildHlsSource((source as HlsStream).url), mediaId)
            StreamDecodePlan.DASH -> decodeAdaptive(buildDashSourceFor(source as DashStream), mediaId)
            StreamDecodePlan.SKIP_DRM -> {
                Timber.d("StreamingAudioSource: DRM stream for $mediaId; skipping (Path A covers it)")
                null
            }
            StreamDecodePlan.SKIP_UNSUPPORTED -> {
                Timber.d("StreamingAudioSource: unsupported stream for $mediaId; skipping")
                null
            }
        }
    }

    /** Progressive URL: pass the plugin's request headers through to the extractor. */
    private fun decodeUrl(source: UrlStream): DecodedAudio? =
        runCatching { audioDecoder.decode(context, source.url.toUri(), source.headers) }
            .onFailure { Timber.w(it, "StreamingAudioSource: url decode failed") }
            .getOrNull()

    /** Raw PCM over a live FD (the host owns + closes the FD). */
    private fun decodePcm(fd: ParcelFileDescriptor?): DecodedAudio? {
        if (fd == null) return null
        return fd.use { runCatching { audioDecoder.decode(it.fileDescriptor) }.getOrNull() }
    }

    /** Headlessly decode ~10s from an already-built adaptive [mediaSource] (HLS/DASH), or null. */
    private fun decodeAdaptive(mediaSource: MediaSource?, mediaId: MediaId): DecodedAudio? {
        mediaSource ?: return null
        return runCatching { clipDecoder.decode(mediaSource) }
            .onFailure { Timber.w(it, "StreamingAudioSource: adaptive decode failed for $mediaId") }
            .getOrNull()
    }

    /** Resolve the DASH manifest to a URI (inline XML saved to a temp file, or the manifest URL). */
    private fun buildDashSourceFor(source: DashStream): MediaSource? {
        val uri = source.manifest?.let { saveDashXmlToFile(it) }?.toString()
            ?: source.manifestUrl
            ?: return null
        return buildDashSource(uri)
    }

    private fun buildHlsSource(url: String): MediaSource? = runCatching {
        // The DefaultMediaSourceFactory picks HlsMediaSource from the .m3u8 (needs the media3 HLS module).
        DefaultMediaSourceFactory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(url.toUri()))
    }.onFailure { Timber.w(it, "StreamingAudioSource: could not build HLS source") }.getOrNull()

    private fun buildDashSource(uri: String): MediaSource? = runCatching {
        DashMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(uri.toUri()))
    }.onFailure { Timber.w(it, "StreamingAudioSource: could not build DASH source") }.getOrNull()

    /** Persist inline DASH manifest XML to a temp file (mirrors ViperMediaSource). */
    private fun saveDashXmlToFile(dashXml: String): Uri {
        val dir = File(context.cacheDir, "rec_dash_manifests").apply { mkdirs() }
        val file = File(dir, "manifest_${System.currentTimeMillis()}.mpd")
        file.writeText(dashXml)
        return file.toUri()
    }
}
