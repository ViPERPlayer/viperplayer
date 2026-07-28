package com.viperplayer.data.rec

import java.io.File

/**
 * Observable lifecycle of the on-device CLAP audio-tower model (the int8 ONNX served by the ViPER
 * backend at the `/v1/models/` routes). The recommender (P1c indexer / P1d UI) can only run once a [Ready]
 * model of the expected [ClapModel.MODEL_VERSION] is installed.
 *
 * States are mutually exclusive and cover the whole opt-in download surface the Settings toggle
 * renders:
 *  - [Absent]           — recommendations are off, or on but nothing downloaded yet (idle).
 *  - [Downloading]      — a [ClapModelDownloadWorker] run is in flight; [progress] is 0f..1f (or
 *                         indeterminate when the total size is unknown).
 *  - [Ready]            — a verified model of [version] is installed at [file] and usable.
 *  - [Failed]           — the last download attempt failed ([reason] is a stable, user-safe code);
 *                         the toggle offers a retry.
 *  - [VersionMismatch]  — an OLD model is installed but the backend now advertises a different
 *                         version, so the installed file must NOT be used and a (re)download is
 *                         needed. Embeddings stamped with [installedVersion] are stale (P1c
 *                         re-embeds).
 */
sealed interface ClapModelState {

    /** No model installed (recommendations off, or on and idle before the first download). */
    data object Absent : ClapModelState

    /**
     * A download is running. [progress] is a fraction in 0f..1f, or `null` when the total size is
     * not yet known (indeterminate). [version] is the version being fetched (from the manifest).
     */
    data class Downloading(val progress: Float?, val version: String?) : ClapModelState

    /** A verified [version] model is installed at [file] and ready for the encoder. */
    data class Ready(val version: String, val file: File) : ClapModelState

    /** The last attempt failed. [reason] is a stable machine code the UI maps to a localized string. */
    data class Failed(val reason: FailureReason) : ClapModelState

    /**
     * An installed model's [installedVersion] no longer matches the backend's [expectedVersion]
     * (or the app's [ClapModel.MODEL_VERSION] expectation). The installed file is unusable and a
     * (re)download is required; any stored embeddings of [installedVersion] are stale.
     */
    data class VersionMismatch(
        val installedVersion: String,
        val expectedVersion: String,
    ) : ClapModelState
}

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

/**
 * Stable, machine-readable failure codes for [ClapModelState.Failed]. Kept UI-string-free (the
 * Settings layer maps each to a localized message) so this stays a pure domain type.
 */
enum class FailureReason {
    /** The backend URL is not built into this app (BuildConfig placeholder). */
    BACKEND_NOT_CONFIGURED,

    /** The manifest could not be fetched or parsed (offline, 5xx, malformed JSON, model 404). */
    MANIFEST_UNAVAILABLE,

    /** The bytes downloaded but their sha256 did not match the manifest (corrupt / tampered). */
    CHECKSUM_MISMATCH,

    /** A transport/IO error during the streamed download (connection dropped, disk full, etc.). */
    NETWORK_OR_IO,

    /** An unmetered (Wi-Fi) network was required but not available; the worker is waiting. */
    NEEDS_UNMETERED_NETWORK,
}
