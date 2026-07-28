package com.viperplayer.data.rec

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.security.MessageDigest

/**
 * The recommender-model manifest served by the backend at `GET /v1/models/manifest`. Only the
 * `clap_audio_int8` entry is consumed here; unknown keys are ignored so the backend can add more
 * models later without breaking the app.
 *
 * Mirrors the backend `internal/models` manifest DTO (github.com/iscle/viper-backend):
 * `{ "clap_audio_int8": { "version": "...", "sha256": "<hex>", "bytes": <n>, "path": "/v1/models/..." } }`.
 */
@Serializable
data class ClapModelManifest(
    @SerialName("clap_audio_int8") val clapAudioInt8: ClapModelManifestEntry,
)

/** One model's manifest record: the version handshake key, its sha256 digest, size, and download path. */
@Serializable
data class ClapModelManifestEntry(
    val version: String,
    val sha256: String,
    val bytes: Long,
    /** Server-relative download path, e.g. `/v1/models/clap-audio-int8.onnx`. */
    val path: String,
)

/**
 * Pure, Android-free helpers for the CLAP model download: the version handshake decision, sha256
 * verification, and the local file-name convention. Kept side-effect-free so they unit-test directly
 * on the JVM.
 */
object ClapModelHandshake {

    /**
     * Decides whether the model must be (re)downloaded, given the currently installed version (or
     * `null` when nothing is installed) and the version the backend now advertises.
     *
     * A download is needed when:
     *  - nothing is installed ([installedVersion] is null/blank), OR
     *  - the installed version differs from the manifest [manifestVersion] (a checkpoint/front-end
     *    bump — the old file is unusable and its embeddings are stale), OR
     *  - the manifest version does not match the app's compiled [expectedVersion]
     *    ([ClapModel.MODEL_VERSION]) — the app cannot use a model it was not built to consume, so we
     *    do NOT install a mismatched one (the caller surfaces this as an out-of-date app rather than
     *    silently downloading an incompatible model).
     *
     * Returns `false` only when an installed version matches BOTH the manifest and the app
     * expectation — i.e. the on-disk model is exactly what this build wants.
     *
     * @param installedVersion the version stamped on the installed model, or null if none installed.
     * @param manifestVersion  the version the backend currently advertises.
     * @param expectedVersion  the version this app build was compiled against (default
     *                         [ClapModel.MODEL_VERSION]).
     */
    fun shouldDownload(
        installedVersion: String?,
        manifestVersion: String,
        expectedVersion: String = ClapModel.MODEL_VERSION,
    ): Boolean {
        // The app can only consume the version it was built for. If the backend advertises something
        // else, there is nothing safe to download for THIS build.
        if (manifestVersion != expectedVersion) return false
        // App-compatible manifest: (re)download iff the installed file isn't already that version.
        return installedVersion.isNullOrBlank() || installedVersion != manifestVersion
    }

    /**
     * `true` iff the backend's advertised [manifestVersion] is a version this app build can consume
     * ([expectedVersion] == [ClapModel.MODEL_VERSION]). When `false`, the manifest is for a newer/
     * different model than this app knows about — a [ClapModelState.VersionMismatch] the user
     * resolves by updating the app, not by downloading.
     */
    fun isManifestCompatible(
        manifestVersion: String,
        expectedVersion: String = ClapModel.MODEL_VERSION,
    ): Boolean = manifestVersion == expectedVersion

    /**
     * `true` iff the installed [installedVersion] is stale relative to the app's [expectedVersion]
     * (present but different) — a signal that stored embeddings of [installedVersion] must be
     * recomputed (the P1c indexer re-embeds). A null/blank installed version is "nothing to
     * invalidate" and returns `false`.
     */
    fun isInstalledStale(
        installedVersion: String?,
        expectedVersion: String = ClapModel.MODEL_VERSION,
    ): Boolean = !installedVersion.isNullOrBlank() && installedVersion != expectedVersion

    /**
     * The canonical on-disk file name for an installed model of [version], e.g.
     * `clap_audio_laion-larger_clap_music-mel1.onnx`. Versioning the name lets a new version land
     * beside an old one and makes an atomic rename-into-place unambiguous.
     */
    fun installedFileName(version: String): String = "clap_audio_${version}.onnx"

    /** The partial-download file name for [version] (streamed to, then atomically renamed). */
    fun partFileName(version: String): String = "${installedFileName(version)}.part"

    /**
     * Verifies [actualBytes] against the manifest's lowercase-hex [expectedSha256]. Case-insensitive
     * on the hex; returns `false` on any length/format mismatch rather than throwing.
     */
    fun verifySha256(actualBytes: ByteArray, expectedSha256: String): Boolean {
        val digest = sha256Hex(actualBytes)
        return digest.equals(expectedSha256.trim(), ignoreCase = true)
    }

    /** Lowercase-hex sha256 of [bytes]. */
    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.toHex()
    }

    private fun ByteArray.toHex(): String {
        val sb = StringBuilder(size * 2)
        for (b in this) {
            val v = b.toInt() and 0xFF
            sb.append(HEX[v ushr 4])
            sb.append(HEX[v and 0x0F])
        }
        return sb.toString()
    }

    private val HEX = "0123456789abcdef".toCharArray()
}
