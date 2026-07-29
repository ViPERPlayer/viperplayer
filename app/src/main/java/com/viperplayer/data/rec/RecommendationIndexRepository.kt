package com.viperplayer.data.rec

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
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
) {

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
                        // enabled but model not ready yet: wait — a later Ready emission re-triggers.
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

    /** Enqueues the unique, battery-not-low indexing work (KEEP-coalesced). */
    private fun enqueue() {
        val request = OneTimeWorkRequestBuilder<LibraryIndexWorker>()
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
            request,
        )
    }

    /** Cancels any pending/running indexing run (e.g. recommendations turned off). */
    fun cancel() {
        workManager.cancelUniqueWork(LibraryIndexWorker.UNIQUE_WORK_NAME)
    }

    /**
     * Live indexing status for the Settings row: [IndexingStatus.Indexing] with the number of songs
     * still missing an embedding (`total`) and how many the active run has processed (`processed`),
     * else [IndexingStatus.Idle]. Derived by [deriveStatus] from the missing-count + the worker's
     * WorkInfo so the derivation is pure and unit-testable.
     */
    val indexingStatus: Flow<IndexingStatus> =
        combine(
            songDao.countSongsMissingEmbedding(ClapModel.MODEL_VERSION),
            workManager.getWorkInfosForUniqueWorkFlow(LibraryIndexWorker.UNIQUE_WORK_NAME),
        ) { missing, workInfos ->
            deriveStatus(missing, workInfos.firstOrNull().toIndexSnapshot())
        }.distinctUntilChanged()

    /** Adapts a WorkManager [WorkInfo] into the framework-free [IndexWorkSnapshot] (null when none). */
    private fun WorkInfo?.toIndexSnapshot(): IndexWorkSnapshot? = this?.let {
        IndexWorkSnapshot(
            active = it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED,
            processed = it.progress.getInt(LibraryIndexWorker.KEY_PROCESSED, -1).takeIf { p -> p >= 0 },
            batchTotal = it.progress.getInt(LibraryIndexWorker.KEY_TOTAL, -1).takeIf { t -> t >= 0 },
        )
    }

    companion object {
        /**
         * Pure derivation of [IndexingStatus] from the count of songs still missing an embedding and a
         * framework-free worker snapshot. Kept static + free of Android so it unit-tests directly.
         *
         * We show [IndexingStatus.Indexing] whenever a run is active (so "N/M" appears immediately) OR
         * there is still work outstanding. `processed` is the worker's live per-run count when known;
         * otherwise we fall back to `(total - missing)` so the line is still meaningful before the
         * first progress tick. `total` is `missing + processed` — the full amount this pass will cover.
         */
        fun deriveStatus(missing: Int, work: IndexWorkSnapshot?): IndexingStatus {
            val active = work?.active == true
            if (missing <= 0 && !active) return IndexingStatus.Idle
            val processed = work?.processed ?: 0
            // Total = what's left to embed + what this run has already embedded.
            val total = (missing + processed).coerceAtLeast(processed).coerceAtLeast(1)
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
