package com.viperplayer.data.plugin.update

/**
 * The pure, Android-free decision core of the plugin update system. Everything here is a
 * side-effect-free function of its inputs so it can be exhaustively unit-tested on the JVM;
 * PackageManager / networking / PackageInstaller live in [PluginUpdateManager].
 */
object PluginUpdateLogic {

    /** Default cadence for the throttled app-start check: at most once every 12 hours. */
    const val DEFAULT_CHECK_INTERVAL_MS: Long = 12L * 60L * 60L * 1000L

    /**
     * True when [manifestVersionCode] is strictly newer than the [installedVersionCode]. Equal or
     * older manifests are not updates (never offer a downgrade or a reinstall of the same build).
     */
    fun isUpdateAvailable(installedVersionCode: Long, manifestVersionCode: Long): Boolean =
        manifestVersionCode > installedVersionCode

    /**
     * Whether the current host build satisfies the manifest's optional [minHostVersion] floor. A
     * `null` floor means "no requirement" → always compatible. When the host is older than the
     * floor the update is withheld (installing it would yield a plugin the host can't talk to).
     */
    fun isHostCompatible(hostVersionCode: Long, minHostVersion: Long?): Boolean =
        minHostVersion == null || hostVersionCode >= minHostVersion

    /**
     * Cadence decision for the throttled auto-check. Returns true when a check is due:
     * on first run ([lastCheckMs] `<= 0`, meaning never checked) or once at least [intervalMs] has
     * elapsed since [lastCheckMs]. A [nowMs] before [lastCheckMs] (clock moved backwards) is treated
     * as due, so a backwards clock can't wedge the checker forever.
     */
    fun shouldCheck(
        lastCheckMs: Long,
        nowMs: Long,
        intervalMs: Long = DEFAULT_CHECK_INTERVAL_MS,
    ): Boolean {
        if (lastCheckMs <= 0L) return true // never checked → first run
        if (nowMs < lastCheckMs) return true // clock went backwards → don't get stuck
        return nowMs - lastCheckMs >= intervalMs
    }

    /**
     * Whether the user has dismissed exactly this [manifestVersionCode]. A dismissal only suppresses
     * the version it targeted: a newer build ([manifestVersionCode] > [dismissedVersionCode])
     * re-surfaces. `null`/absent dismissal never suppresses.
     */
    fun isDismissed(manifestVersionCode: Long, dismissedVersionCode: Long?): Boolean =
        dismissedVersionCode != null && manifestVersionCode <= dismissedVersionCode

    /**
     * Fold the manifest + installed/host state + dismissal into a single [UpdateEvaluation]. This is
     * the one call the manager and UI reason about; it encodes the whole "should we offer this
     * update, and is it currently actionable?" policy in one testable place.
     */
    fun evaluate(
        installedVersionCode: Long,
        hostVersionCode: Long,
        manifest: PluginUpdateManifest,
        dismissedVersionCode: Long?,
    ): UpdateEvaluation = when {
        !isUpdateAvailable(installedVersionCode, manifest.versionCode) -> UpdateEvaluation.UpToDate
        !isHostCompatible(hostVersionCode, manifest.minHostVersion) ->
            UpdateEvaluation.Incompatible(manifest.minHostVersion ?: 0L)
        isDismissed(manifest.versionCode, dismissedVersionCode) -> UpdateEvaluation.Dismissed(manifest)
        else -> UpdateEvaluation.Available(manifest)
    }
}

/** Outcome of [PluginUpdateLogic.evaluate] for one plugin. */
sealed interface UpdateEvaluation {
    /** Installed build is the newest known — nothing to offer. */
    data object UpToDate : UpdateEvaluation

    /** A newer build exists and should be surfaced to the user. */
    data class Available(val manifest: PluginUpdateManifest) : UpdateEvaluation

    /** A newer build exists but the user dismissed exactly this version — kept quiet. */
    data class Dismissed(val manifest: PluginUpdateManifest) : UpdateEvaluation

    /** A newer build exists but requires a newer host ([minHostVersion]); withheld. */
    data class Incompatible(val minHostVersion: Long) : UpdateEvaluation
}
