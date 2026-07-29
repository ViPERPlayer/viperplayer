package com.viperplayer.data.rec

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.viperplayer.data.local.dao.SongDao
import com.viperplayer.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * UI-facing snapshot of the background library-embedding indexer. Kept string-free (the Settings layer
 * localizes it) so it stays a pure data type.
 *
 *  - [Idle]     — nothing to do / not enabled (no status line).
 *  - [Indexing] — a run is active or pending; [total] songs still need embeddings and [processed] of
 *                 the current run's batch have been handled. The "Analyzing your library… N/M" line.
 */
sealed interface IndexingStatus {
    data object Idle : IndexingStatus
    data class Indexing(val processed: Int, val total: Int) : IndexingStatus
}

/**
 * The narrow read-only slice of the indexer that the recommender ([RecommendationRepository]) needs: the
 * live [indexingStatus]. Extracted as an interface (implemented by [RecommendationIndexRepository]) so
 * the recommender can be unit-tested with a trivial fake instead of a WorkManager-backed singleton.
 */
interface IndexingStatusProvider {
    val indexingStatus: Flow<IndexingStatus>
}

/**
 * Owns the WorkManager-backed [LibraryIndexWorker]: enqueues it (unique work, KEEP, battery-not-low)
 * when the model is ready + recommendations enabled, and surfaces a live [indexingStatus] the Settings
 * "Smart recommendations" row can render. All WorkManager/DB glue lives here (not the ViewModel), per
 * the app's MVVM rule.
 *
 * The trigger contract: call [enqueueIfEligible] whenever the model becomes [ClapModelState.Ready] and
 * recommendations are on, on app start under those conditions, and (optionally, debounced) after a
 * library change. It is idempotent — [ExistingWorkPolicy.KEEP] coalesces repeated calls into the one
 * in-flight run.
 */
@Singleton
class RecommendationIndexRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val clapModelRepository: ClapModelRepository,
    private val songDao: SongDao,
) : IndexingStatusProvider {

    private val workManager get() = WorkManager.getInstance(context)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val started = AtomicBoolean(false)

    /**
     * Starts the trigger: observes `recommendationsEnabled` + the model lifecycle and (re)enqueues the
     * indexer whenever the model becomes [ClapModelState.Ready] with recommendations on, and cancels it
     * when recommendations are turned off. Idempotent — safe to call once from `Application.onCreate`.
     */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            combine(
                settingsRepository.recommendationsEnabled,
                clapModelRepository.modelState,
            ) { enabled, modelState -> enabled to modelState }
                .distinctUntilChanged()
                .collect { (enabled, modelState) ->
                    when {
                        !enabled -> cancel()
                        modelState is ClapModelState.Ready -> enqueue()
                        // Enabled but no model yet (fresh, or a prior build that couldn't download): make
                        // sure a model is installed. With the model bundled in the APK this installs it
                        // instantly from the asset; otherwise it enqueues a download. A later Ready
                        // emission then enqueues indexing. Absent/Failed only (don't interrupt a download).
                        modelState is ClapModelState.Absent || modelState is ClapModelState.Failed ->
                            runCatching { clapModelRepository.ensureDownloaded() }
                        // Downloading / VersionMismatch: wait — a later Ready emission re-triggers.
                    }
                }
        }
    }

    /**
     * Enqueues the indexer iff recommendations are enabled AND a compatible model is installed (not
     * stale, present on disk). No-op otherwise. Safe to call repeatedly (unique work, KEEP). Used by
     * callers that don't observe the model state (e.g. a post-library-change nudge).
     */
    suspend fun enqueueIfEligible() {
        if (!isEligible()) return
        enqueue()
    }

    /** True when smart recommendations are on and a current, on-disk model is available. */
    private suspend fun isEligible(): Boolean {
        if (!settingsRepository.recommendationsEnabled.first()) return false
        val installed = clapModelRepository.installedFile() ?: return false
        return installed.isFile && !clapModelRepository.isInstalledStale()
    }

    /**
     * Enqueues BOTH indexing runs (each unique + KEEP-coalesced):
     *  - [LibraryIndexWorker] — LOCAL bytes (downloaded + local-library). NO network (on-device compute)
     *    + battery-not-low.
     *  - [StreamingIndexWorker] — STREAMING-only songs not played. UNMETERED (Wi-Fi) + battery-not-low,
     *    since decoding a live stream spends network + power.
     *
     * The two workers embed disjoint partitions of the missing-embedding set (see [SongEmbedCandidates]),
     * so running them together never double-embeds a song.
     */
    private fun enqueue() {
        val localRequest = OneTimeWorkRequestBuilder<LibraryIndexWorker>()
            .setConstraints(
                Constraints.Builder()
                    // On-device compute — no network required. Don't drain a low battery.
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .setInputData(Data.EMPTY)
            .build()
        workManager.enqueueUniqueWork(
            LibraryIndexWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            localRequest,
        )

        val streamingRequest = OneTimeWorkRequestBuilder<StreamingIndexWorker>()
            .setConstraints(
                Constraints.Builder()
                    // Streaming decode spends data + power: Wi-Fi only, battery-not-low.
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .setInputData(Data.EMPTY)
            .build()
        workManager.enqueueUniqueWork(
            StreamingIndexWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            streamingRequest,
        )
    }

    /** Cancels any pending/running indexing runs (both workers), e.g. recommendations turned off. */
    fun cancel() {
        workManager.cancelUniqueWork(LibraryIndexWorker.UNIQUE_WORK_NAME)
        workManager.cancelUniqueWork(StreamingIndexWorker.UNIQUE_WORK_NAME)
    }

    /**
     * Live indexing status for the Settings row: [IndexingStatus.Indexing] with the number of songs
     * still missing an embedding (`total`) and how many the active run has processed (`processed`),
     * else [IndexingStatus.Idle]. Derived by [deriveStatus] from the missing-count + the worker's
     * WorkInfo so the derivation is pure and unit-testable.
     */
    override val indexingStatus: Flow<IndexingStatus> =
        combine(
            // `countSongsMissingEmbedding` counts BOTH partitions (local + streaming), so `missing`
            // already reflects the streaming backlog; we merge BOTH workers' snapshots so an active
            // streaming run also keeps the "Analyzing…" line up.
            songDao.countSongsMissingEmbedding(ClapModel.MODEL_VERSION),
            workManager.getWorkInfosForUniqueWorkFlow(LibraryIndexWorker.UNIQUE_WORK_NAME),
            workManager.getWorkInfosForUniqueWorkFlow(StreamingIndexWorker.UNIQUE_WORK_NAME),
        ) { missing, localInfos, streamingInfos ->
            val local = localInfos.firstOrNull()
                .toIndexSnapshot(LibraryIndexWorker.KEY_PROCESSED, LibraryIndexWorker.KEY_TOTAL)
            val streaming = streamingInfos.firstOrNull()
                .toIndexSnapshot(StreamingIndexWorker.KEY_PROCESSED, StreamingIndexWorker.KEY_TOTAL)
            deriveStatus(missing, mergeSnapshots(local, streaming))
        }.distinctUntilChanged()

    /** Adapts a WorkManager [WorkInfo] into the framework-free [IndexWorkSnapshot] (null when none). */
    private fun WorkInfo?.toIndexSnapshot(processedKey: String, totalKey: String): IndexWorkSnapshot? = this?.let {
        IndexWorkSnapshot(
            active = it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED,
            processed = it.progress.getInt(processedKey, -1).takeIf { p -> p >= 0 },
            batchTotal = it.progress.getInt(totalKey, -1).takeIf { t -> t >= 0 },
        )
    }

    companion object {
        /**
         * Merge the local + streaming worker snapshots into one for [deriveStatus]: active if EITHER is
         * active, `processed` is the sum of both runs' processed counts (so the combined "N" advances as
         * either worker makes progress), and `batchTotal` is the sum of known batch totals. Null only
         * when both snapshots are null. Pure/unit-testable.
         */
        fun mergeSnapshots(a: IndexWorkSnapshot?, b: IndexWorkSnapshot?): IndexWorkSnapshot? {
            if (a == null) return b
            if (b == null) return a
            val processed = listOfNotNull(a.processed, b.processed).takeIf { it.isNotEmpty() }?.sum()
            val batchTotal = listOfNotNull(a.batchTotal, b.batchTotal).takeIf { it.isNotEmpty() }?.sum()
            return IndexWorkSnapshot(
                active = a.active || b.active,
                processed = processed,
                batchTotal = batchTotal,
            )
        }

        /**
         * Pure derivation of [IndexingStatus] from the count of songs still missing an embedding and a
         * framework-free worker snapshot. Kept static + free of Android so it unit-tests directly.
         *
         * The "Analyzing…" line is shown ONLY while a run is actually enqueued/running ([active]). When no
         * worker is active there is nothing being analyzed → [IndexingStatus.Idle], even if some rows
         * remain permanently un-embeddable (e.g. a DRM-only or repeatedly-failing streaming backlog) —
         * otherwise the line would stick forever because those rows never leave `missing`.
         *
         * `total` = `missing + processed` (rows still needing an embedding + rows embedded THIS run). The
         * workers count `processed` as ONLY the rows that actually got an embedding — skipped DRM/decode
         * failures are NOT counted — so a skipped backlog is never double-counted into the total, and the
         * numerator advances monotonically toward it.
         */
        fun deriveStatus(missing: Int, work: IndexWorkSnapshot?): IndexingStatus {
            if (work?.active != true) return IndexingStatus.Idle
            val processed = (work.processed ?: 0).coerceAtLeast(0)
            val total = (missing + processed).coerceAtLeast(1)
            return IndexingStatus.Indexing(processed = processed.coerceAtMost(total), total = total)
        }
    }
}

/**
 * Framework-free snapshot of the indexing worker's status, fed into
 * [RecommendationIndexRepository.deriveStatus] so the derivation is pure/unit-testable without
 * WorkManager. Mirrors the fields the repository reads off a `WorkInfo`.
 */
data class IndexWorkSnapshot(
    /** RUNNING or ENQUEUED. */
    val active: Boolean,
    /** Songs processed in the current run (from progress), or null when not yet reported. */
    val processed: Int?,
    /** The run's batch total (from progress), or null when not yet reported. */
    val batchTotal: Int?,
)
