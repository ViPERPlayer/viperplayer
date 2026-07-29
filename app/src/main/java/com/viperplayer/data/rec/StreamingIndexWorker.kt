package com.viperplayer.data.rec

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.viperplayer.data.local.dao.SongDao
import com.viperplayer.data.local.dao.setEmbedding
import com.viperplayer.data.local.entity.SongEntity
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.repository.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * Background worker that computes on-device CLAP audio embeddings for STREAMING-only songs the user has
 * NOT played (so Path A's playback capture never saw them). It is the Path B "streaming indexer" — a
 * SEPARATE unique work from the local [LibraryIndexWorker], with DIFFERENT constraints:
 *  - **UNMETERED (Wi-Fi)** + **battery-not-low** — decoding a stream spends network + power, so unlike the
 *    no-network local indexer this must wait for Wi-Fi.
 *
 * ## What it does, per run
 *  1. Bails unless recommendations are enabled and a compatible model is [ClapModelState.Ready].
 *  2. Loads the ONNX ONCE into a reused [ClapEmbedder] (one ORT session for the whole batch).
 *  3. **Drains the deferred capture cache** (Path C): embeds any mels saved by [StreamingCaptureEmbedder]
 *     while the model was still downloading, then deletes them.
 *  4. Pages `SongDao.getStreamingSongsMissingEmbeddingPaged` — streaming-only songs still missing a
 *     current-version embedding — ordered liked/recent-first via [SongEmbedCandidates.prioritize]. Per
 *     song: resolve + decode ~10s via [StreamingAudioSource], mel, embed, store, publish progress.
 *     Cooperatively cancellable; never throws for one song (a failed/DRM-skipped song stays in the set,
 *     so the offset advances past it to avoid re-reading it forever).
 *
 * A [HiltWorker]: collaborators come from the app's [androidx.hilt.work.HiltWorkerFactory].
 */
@HiltWorker
class StreamingIndexWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val songDao: SongDao,
    private val streamingAudioSource: StreamingAudioSource,
    private val clapModelRepository: ClapModelRepository,
    private val settingsRepository: SettingsRepository,
    private val pendingCaptureStore: PendingCaptureStore,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
        if (!settingsRepository.recommendationsEnabled.first()) {
            Timber.d("StreamingIndexWorker: recommendations disabled; nothing to index")
            return@withContext Result.success()
        }
        val modelFile = clapModelRepository.installedFile()
        if (modelFile == null || clapModelRepository.isInstalledStale()) {
            Timber.d("StreamingIndexWorker: model not ready/stale; deferring")
            return@withContext Result.success()
        }

        val modelVersion = ClapModel.MODEL_VERSION
        val clap = try {
            ClapEmbedder.fromModelFile(modelFile)
        } catch (e: Exception) {
            Timber.w(e, "StreamingIndexWorker: could not load ONNX model; retrying later")
            return@withContext Result.retry()
        }

        try {
            // Path C: drain any captures saved before the model was installed.
            drainPendingCaptures(clap)

            val total = runCatching { songDao.countStreamingSongsMissingEmbedding(modelVersion).first() }
                .getOrDefault(0)
            var processed = 0
            setProgress(progressData(processed, total))

            var offset = 0
            while (true) {
                currentCoroutineContext().ensureActive()
                val page = songDao.getStreamingSongsMissingEmbeddingPaged(modelVersion, PAGE_SIZE, offset)
                if (page.isEmpty()) break

                val ordered = SongEmbedCandidates.prioritize(page)
                var skippedThisPage = 0
                for (song in ordered) {
                    currentCoroutineContext().ensureActive()
                    val embedded = embedStreamingSong(song, clap)
                    // Count ONLY embedded rows; a skipped (DRM/decode-failed) row stays in the missing set,
                    // so counting it in `processed` would inflate the status total (it would be counted in
                    // both `missing` and `processed`). Advance the page cursor past it either way.
                    if (embedded) processed++ else skippedThisPage++
                    setProgress(progressData(processed, total.coerceAtLeast(processed)))
                }
                offset += skippedThisPage
            }
        } catch (e: CancellationException) {
            throw e // WorkManager stopped us (constraint lost) — resumable next run.
        } catch (e: Exception) {
            Timber.w(e, "StreamingIndexWorker: indexing pass failed; will retry")
            return@withContext Result.retry()
        } finally {
            runCatching { clap.close() }
        }

        Result.success()
    }

    /**
     * Embed one streaming-only [song]: resolve + decode its stream head, mel, embed, store. Returns true
     * iff an embedding was stored. Never throws — a per-song failure (resolve error, DRM skip, decode
     * failure) is logged and returns false so the batch continues.
     */
    private suspend fun embedStreamingSong(song: SongEntity, embedder: AudioEmbedder): Boolean = try {
        val mediaId = mediaIdOf(song)
        if (mediaId == null) {
            false
        } else {
            // Hard per-song deadline: the decode paths are made cancellable (runInterruptible on IO), so a
            // stalled network read / adaptive-decode can't pin the run — on timeout the song is skipped.
            val decoded = withTimeoutOrNull(DECODE_DEADLINE_MS) { streamingAudioSource.decode(mediaId) }
            if (decoded == null) {
                false
            } else {
                val mono = MelSpectrogram.downmixToMono(decoded.interleaved, decoded.channels)
                val mono48k = MelSpectrogram.resampleTo48k(mono, decoded.sampleRate)
                val embedding = embedder.embed(mono48k)
                songDao.setEmbedding(
                    id = mediaId,
                    embedding = embeddingToBytes(embedding),
                    modelVersion = ClapModel.MODEL_VERSION,
                    computedAtMs = System.currentTimeMillis(),
                )
                true
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w(e, "StreamingIndexWorker: failed to embed streaming songId=${song.id}")
        false
    }

    /**
     * Path C drain: for every cached mel, if its song still lacks a current-version embedding, embed +
     * store it; then delete the cache entry regardless (a stale/gone song shouldn't keep a dangling file).
     */
    private suspend fun drainPendingCaptures(embedder: AudioEmbedder) {
        val pending = runCatching { pendingCaptureStore.readAll() }.getOrDefault(emptyList())
        if (pending.isEmpty()) return
        Timber.d("StreamingIndexWorker: draining ${pending.size} deferred captures")
        for (capture in pending) {
            currentCoroutineContext().ensureActive()
            runCatching {
                val mediaId = MediaId.decode(capture.mediaId)
                if (mediaId != null && capture.mel.size == MelSpectrogram.NUM_FRAMES * MelSpectrogram.N_MELS) {
                    val embedding = embedder.embedMel(capture.mel)
                    songDao.setEmbedding(
                        id = mediaId,
                        embedding = embeddingToBytes(embedding),
                        modelVersion = ClapModel.MODEL_VERSION,
                        computedAtMs = System.currentTimeMillis(),
                    )
                }
            }.onFailure { Timber.w(it, "StreamingIndexWorker: failed to drain capture ${capture.mediaId}") }
            pendingCaptureStore.remove(capture.mediaId)
        }
    }

    /** The song's persisted identity as a [MediaId], or null for a row that can't be routed. */
    private fun mediaIdOf(song: SongEntity): MediaId? =
        if (song.sourceId.isBlank()) null
        else MediaId.from(song.pluginId.ifBlank { MediaId.LOCAL_PLUGIN_ID }, song.sourceId)

    private fun progressData(processed: Int, total: Int): Data =
        workDataOf(KEY_PROCESSED to processed, KEY_TOTAL to total)

    companion object {
        /** Unique WorkManager name so at most one streaming-index run exists; a repeat enqueue coalesces. */
        const val UNIQUE_WORK_NAME = "clap_streaming_index"

        /** Progress keys in [androidx.work.WorkInfo.progress]. */
        const val KEY_PROCESSED = "processed"
        const val KEY_TOTAL = "total"

        private const val PAGE_SIZE = 25

        /**
         * Hard wall-clock deadline for resolving + decoding one song's stream head. A bit over the
         * headless ExoPlayer path's own 45s timeout so that path finishes on its own first; for the
         * progressive-URL/PCM path (no internal timeout) this is the sole guard against a stalled stream.
         */
        private const val DECODE_DEADLINE_MS = 60_000L
    }
}
