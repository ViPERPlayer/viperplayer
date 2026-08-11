package com.viperplayer.domain.rec

import java.io.File

/**
 * Observable lifecycle of the on-device CLAP audio-tower model (the int8 ONNX served by the ViPER
 * backend at the `/v1/models/` routes). The recommender (P1c indexer / P1d UI) can only run once a [Ready]
 * model of the expected `ClapModel.MODEL_VERSION` is installed.
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
     * (or the app's `ClapModel.MODEL_VERSION` expectation). The installed file is unusable and a
     * (re)download is required; any stored embeddings of [installedVersion] are stale.
     */
    data class VersionMismatch(
        val installedVersion: String,
        val expectedVersion: String,
    ) : ClapModelState
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
