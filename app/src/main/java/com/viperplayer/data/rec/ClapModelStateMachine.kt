package com.viperplayer.data.rec

import androidx.work.WorkInfo
import com.viperplayer.domain.rec.ClapModelState
import com.viperplayer.domain.rec.FailureReason
import java.io.File

/**
 * A framework-free snapshot of the download worker's status, fed into [ClapModelStateMachine] so the
 * state derivation is pure and unit-testable without WorkManager. Mirrors the fields
 * [ClapModelRepository] reads off a `WorkInfo`.
 */
data class ModelWorkSnapshot(
    val phase: WorkPhase,
    /** In-progress fraction (0f..1f), or null when unknown/indeterminate. */
    val progress: Float? = null,
    /** Failure reason on [WorkPhase.FAILED], else null. */
    val failureReason: FailureReason? = null,
)

/** The relevant lifecycle phases of the download worker (a subset of WorkManager's states). */
enum class WorkPhase {
    /** Enqueued but not started (e.g. waiting on the Wi-Fi constraint). */
    ENQUEUED,

    /** Actively running (streaming bytes). */
    RUNNING,

    /** The last run failed. */
    FAILED,

    /** No active/pending run (succeeded, cancelled, or never enqueued). */
    IDLE,
}

/**
 * Pure derivation of [ClapModelState] from the installed model version + the download worker
 * snapshot + the resolved installed file (null when the file is absent on disk). Kept free of
 * Android/WorkManager so it unit-tests directly; [ClapModelRepository] adapts a `WorkInfo` into
 * [ModelWorkSnapshot] and resolves the [installedFile].
 */
object ClapModelStateMachine {

    /**
     * @param installedVersion the persisted installed version, or null when none.
     * @param installedFile the on-disk file for [installedVersion], or null when it is absent.
     * @param work the current worker snapshot (null when no work has ever been scheduled).
     * @param expectedVersion the app's compiled expectation (default [ClapModel.MODEL_VERSION]).
     */
    fun derive(
        installedVersion: String?,
        installedFile: File?,
        work: ModelWorkSnapshot?,
        expectedVersion: String = ClapModel.MODEL_VERSION,
    ): ClapModelState {
        // A running/enqueued worker overrides the resting state so the toggle shows live progress.
        if (work != null && (work.phase == WorkPhase.RUNNING || work.phase == WorkPhase.ENQUEUED)) {
            return ClapModelState.Downloading(progress = work.progress, version = installedVersion)
        }
        if (work != null && work.phase == WorkPhase.FAILED) {
            return ClapModelState.Failed(work.failureReason ?: FailureReason.NETWORK_OR_IO)
        }

        // Resting state from the installed version.
        if (installedVersion.isNullOrBlank()) return ClapModelState.Absent
        if (installedVersion != expectedVersion) {
            return ClapModelState.VersionMismatch(
                installedVersion = installedVersion,
                expectedVersion = expectedVersion,
            )
        }
        // Recorded version matches the app, but the file is gone → treat as absent (needs re-download).
        if (installedFile == null) return ClapModelState.Absent
        return ClapModelState.Ready(version = installedVersion, file = installedFile)
    }
}
