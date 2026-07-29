package com.viperplayer.data.rec

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.viperplayer.data.local.dao.SongDao
import com.viperplayer.domain.repository.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Background worker that computes on-device CLAP audio embeddings for the user's local library. It is
 * the P1c "indexer": the last step before the (P1d) kNN recommendation surfaces can query stored
 * vectors.
 *
 * ## What it does, per run
 *  1. Bails out early if smart recommendations are disabled or no compatible model is [ClapModelState.Ready].
 *  2. Loads the ONNX **once** into a single [ClapEmbedder] (one ORT session reused across the whole
 *     batch — a session per song would be wasteful).
 *  3. Pulls `SongDao.getSongsMissingEmbedding()` in pages, orders each page
 *     downloaded > liked > recently-played > other via [SongEmbedCandidates], and embeds each one,
 *     **storing the result immediately** (checkpointing — an interrupted run resumes from the songs
 *     that are still missing an embedding). Streaming-only songs with no local bytes are skipped.
 *  4. Publishes a `processed`/`total` count via [setProgress] so the Settings row can show
 *     "Analyzing your library… N/M".
 *
 * ## Constraints & lifecycle
 * Enqueued with **battery-not-low** (no network — this is on-device compute) by
 * [RecommendationIndexRepository]. It is cooperatively cancellable (checks the coroutine on every song)
 * so WorkManager can stop it on constraint loss; the already-stored embeddings make the next run cheap.
 *
 * A [HiltWorker]: its collaborators are supplied by the app's [androidx.hilt.work.HiltWorkerFactory].
 */
@HiltWorker
class LibraryIndexWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val songDao: SongDao,
    private val embedder: LibraryEmbedder,
    private val clapModelRepository: ClapModelRepository,
    private val settingsRepository: SettingsRepository,
    private val audioDecoder: AudioDecoder,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
        // 1. Gate: enabled + a compatible model actually on disk.
        if (!settingsRepository.recommendationsEnabled.first()) {
            Timber.d("LibraryIndexWorker: recommendations disabled; nothing to index")
            return@withContext Result.success()
        }
        val modelFile = clapModelRepository.installedFile()
        if (modelFile == null || clapModelRepository.isInstalledStale()) {
            Timber.d("LibraryIndexWorker: model not ready/stale; deferring indexing")
            // Not a failure: the trigger re-enqueues once the model becomes Ready.
            return@withContext Result.success()
        }

        val modelVersion = ClapModel.MODEL_VERSION
        val total = runCatching { songDao.countSongsMissingEmbedding(modelVersion).first() }
            .getOrDefault(0)
        if (total <= 0) {
            Timber.d("LibraryIndexWorker: no songs missing an embedding")
            return@withContext Result.success()
        }

        // 2. One ORT session for the whole run.
        val clap = try {
            ClapEmbedder.fromModelFile(modelFile)
        } catch (e: Exception) {
            Timber.w(e, "LibraryIndexWorker: could not load ONNX model; retrying later")
            return@withContext Result.retry()
        }

        var processed = 0
        setProgress(progressData(processed, total))

        try {
            // 3. Page through the still-missing songs by an ADVANCING offset. Embedded rows drop out of
            //    the (version-gated) result set as they're stored (checkpointing → a killed run resumes
            //    from the songs still missing), while un-embeddable streaming-only rows never leave the
            //    set — so we advance the offset past the ones we SKIPPED this page to avoid re-reading
            //    them forever and starving later local songs.
            var offset = 0
            while (true) {
                currentCoroutineContext().ensureActive()
                val page = songDao.getSongsMissingEmbeddingPaged(modelVersion, PAGE_SIZE, offset)
                if (page.isEmpty()) break

                val ordered = SongEmbedCandidates.prioritize(page)
                var skippedThisPage = 0
                for (song in ordered) {
                    currentCoroutineContext().ensureActive()
                    val outcome = embedder.embedSong(song, audioDecoder, clap)
                    when (outcome) {
                        // Count ONLY rows that actually got an embedding — a skipped row stays in the
                        // missing set, so counting it in `processed` would double it in the status total.
                        LibraryEmbedder.Outcome.EMBEDDED -> processed++ // row leaves the result set on its own
                        else -> skippedThisPage++ // stays in the set; step the cursor past it
                    }
                    setProgress(progressData(processed.coerceAtMost(total), total))
                }
                offset += skippedThisPage
            }
        } catch (e: CancellationException) {
            throw e // WorkManager stopped us (constraint lost) — resumable next run.
        } catch (e: Exception) {
            Timber.w(e, "LibraryIndexWorker: indexing pass failed; will retry")
            return@withContext Result.retry()
        } finally {
            runCatching { clap.close() }
        }

        Result.success()
    }

    private fun progressData(processed: Int, total: Int): Data =
        workDataOf(KEY_PROCESSED to processed, KEY_TOTAL to total)

    companion object {
        /** Unique WorkManager name so at most one indexing run exists; a repeat enqueue is coalesced. */
        const val UNIQUE_WORK_NAME = "clap_library_index"

        /** Progress keys in [androidx.work.WorkInfo.progress]. */
        const val KEY_PROCESSED = "processed"
        const val KEY_TOTAL = "total"

        /**
         * Songs pulled per DB page. Kept modest so a run that is killed mid-page loses at most one
         * page's worth of un-checkpointed *progress* (embeddings are stored per-song, so no work is
         * actually lost — only the in-memory page).
         */
        private const val PAGE_SIZE = 50
    }
}
