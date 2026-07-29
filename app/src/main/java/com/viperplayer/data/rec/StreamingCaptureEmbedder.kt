package com.viperplayer.data.rec

import com.viperplayer.data.local.dao.SongDao
import com.viperplayer.data.local.dao.getByMediaId
import com.viperplayer.data.local.dao.setEmbedding
import com.viperplayer.data.local.entity.SongEntity
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Path A's back half: turns a mono PCM clip captured during playback (by [RecCaptureAudioProcessor]) into
 * a stored CLAP embedding, or — when the model is not yet installed — into a deferred [PendingCaptureStore]
 * entry to embed later. Runs entirely off the audio/main thread on an IO scope; every path swallows +
 * logs errors so a capture can NEVER crash or stall playback.
 *
 * Per captured clip it:
 *  1. Bails unless recommendations are enabled and the song still LACKS a current-version embedding (a
 *     capture and the background worker can race; the last writer wins harmlessly, but we avoid needless
 *     work). It also skips non-streaming songs (a downloaded/local song is the local worker's job).
 *  2. Resamples to 48kHz and computes the log-mel front-end ([MelSpectrogram]) — reusing the SAME mel
 *     contract as every other embed path.
 *  3. If the model is [ClapModelState.Ready]: runs the ONNX tower ([ClapAudioEncoder]) and stores the
 *     512-d embedding via [SongDao.setEmbedding], stamped with [ClapModel.MODEL_VERSION].
 *  4. If the model is NOT ready: writes the mel to [PendingCaptureStore] so a later drain (when the model
 *     installs) can embed it without re-decoding.
 *
 * The ONNX session is short-lived (created + closed per capture): captures are sparse and opportunistic,
 * so a per-capture session avoids holding a native session open for the app's lifetime. The background
 * batch worker (Path B) reuses one session because it processes many songs back-to-back.
 */
@Singleton
class StreamingCaptureEmbedder @Inject constructor(
    private val songDao: SongDao,
    private val settingsRepository: SettingsRepository,
    private val clapModelRepository: ClapModelRepository,
    private val pendingCaptureStore: PendingCaptureStore,
) {

    /**
     * Opens a short-lived [AudioEmbedder] over the model at [modelFile]. Seam so the model-ready embed
     * path is unit-testable with a fake (the production factory loads a real ONNX/ORT session).
     */
    fun interface MelEmbedderFactory {
        fun open(modelFile: File): AudioEmbedder
    }

    /** Injectable for tests; defaults to the real [ClapEmbedder]. */
    var embedderFactory: MelEmbedderFactory = MelEmbedderFactory { ClapEmbedder.fromModelFile(it) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Fire-and-forget entry point for [RecCaptureAudioProcessor.CaptureListener]: schedule [monoPcm]
     * (mono float at [sampleRate] Hz for the song encoded by [encodedMediaId]) to be embedded or cached.
     * Returns immediately; never throws.
     */
    fun submit(encodedMediaId: String, monoPcm: FloatArray, sampleRate: Int) {
        val mediaId = MediaId.decode(encodedMediaId)
        if (mediaId !is MediaId.Plugin) return // streaming-only capture is plugin-served
        scope.launch {
            runCatching { process(mediaId, encodedMediaId, monoPcm, sampleRate) }
                .onFailure { Timber.w(it, "StreamingCaptureEmbedder: capture handling failed") }
        }
    }

    /**
     * The actual embed-or-cache logic for an already-decoded [mediaId] (its [encodedMediaId] is the cache
     * key). Suspends; exposed at this granularity — with a pre-decoded [mediaId] — so it is unit-testable
     * without [MediaId.decode] (which needs `android.net.Uri`, unavailable in a plain JVM test).
     */
    suspend fun process(mediaId: MediaId.Plugin, encodedMediaId: String, monoPcm: FloatArray, sampleRate: Int) {
        if (!settingsRepository.recommendationsEnabled.first()) return
        if (monoPcm.isEmpty() || sampleRate <= 0) return

        // Re-check the row: only embed a streaming-only song that still lacks a current-version embedding.
        val song = runCatching { songDao.getByMediaId(mediaId) }.getOrNull() ?: return
        if (!SongEmbedCandidates.isStreamingCandidate(song)) return
        if (hasCurrentEmbedding(song)) return

        // Front-end: resample to 48k -> log-mel. Reuses the canonical mel contract.
        val mono48k = MelSpectrogram.resampleTo48k(monoPcm, sampleRate)
        if (mono48k.isEmpty()) return
        val mel = MelSpectrogram.computeInputFeatures(mono48k)

        val modelFile = clapModelRepository.installedFile()
        if (modelFile == null || clapModelRepository.isInstalledStale()) {
            // Path C: model not ready — save the mel for a later drain.
            pendingCaptureStore.put(encodedMediaId, mel)
            Timber.d("StreamingCaptureEmbedder: model not ready; cached capture for $mediaId")
            return
        }

        // Model ready: embed now and store. A per-capture embedder (captures are sparse).
        val audioEmbedder = embedderFactory.open(modelFile)
        val embedding = try {
            audioEmbedder.embedMel(mel)
        } finally {
            (audioEmbedder as? AutoCloseable)?.let { runCatching { it.close() } }
        }
        songDao.setEmbedding(
            id = mediaId,
            embedding = embeddingToBytes(embedding),
            modelVersion = ClapModel.MODEL_VERSION,
            computedAtMs = System.currentTimeMillis(),
        )
        Timber.d("StreamingCaptureEmbedder: embedded captured stream for $mediaId")
    }

    private fun hasCurrentEmbedding(song: SongEntity): Boolean =
        song.audioEmbedding != null && song.embeddingModelVersion == ClapModel.MODEL_VERSION
}
