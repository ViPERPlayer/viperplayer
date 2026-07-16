package com.viperplayer.data.plugin.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure decision core of the plugin update system: version comparison,
 * minHostVersion gating, the check cadence, dismissed-version suppression, and the combined
 * [PluginUpdateLogic.evaluate].
 */
class PluginUpdateLogicTest {

    private fun manifest(
        versionCode: Long = 2,
        minHostVersion: Long? = null,
    ) = PluginUpdateManifest(
        versionCode = versionCode,
        versionName = "1.$versionCode",
        downloadUrl = "https://example.com/p.apk",
        minHostVersion = minHostVersion,
    )

    // ---- isUpdateAvailable: newer / equal / older ----

    @Test
    fun update_available_whenManifestVersionIsNewer() {
        assertTrue(PluginUpdateLogic.isUpdateAvailable(installedVersionCode = 1, manifestVersionCode = 2))
    }

    @Test
    fun update_notAvailable_whenVersionsEqual() {
        assertFalse(PluginUpdateLogic.isUpdateAvailable(installedVersionCode = 5, manifestVersionCode = 5))
    }

    @Test
    fun update_notAvailable_whenManifestVersionIsOlder() {
        assertFalse(PluginUpdateLogic.isUpdateAvailable(installedVersionCode = 5, manifestVersionCode = 4))
    }

    // ---- minHostVersion gating ----

    @Test
    fun hostCompatible_whenNoMinHostVersion() {
        assertTrue(PluginUpdateLogic.isHostCompatible(hostVersionCode = 1, minHostVersion = null))
    }

    @Test
    fun hostCompatible_whenHostMeetsFloor() {
        assertTrue(PluginUpdateLogic.isHostCompatible(hostVersionCode = 5, minHostVersion = 5))
        assertTrue(PluginUpdateLogic.isHostCompatible(hostVersionCode = 6, minHostVersion = 5))
    }

    @Test
    fun hostIncompatible_whenHostBelowFloor() {
        assertFalse(PluginUpdateLogic.isHostCompatible(hostVersionCode = 4, minHostVersion = 5))
    }

    // ---- cadence: first-run / due / not-due / backwards-clock ----

    @Test
    fun shouldCheck_onFirstRun_whenNeverChecked() {
        assertTrue(PluginUpdateLogic.shouldCheck(lastCheckMs = 0, nowMs = 1_000, intervalMs = 10_000))
    }

    @Test
    fun shouldCheck_notDue_beforeInterval() {
        val last = 1_000_000L
        assertFalse(
            PluginUpdateLogic.shouldCheck(lastCheckMs = last, nowMs = last + 5_000, intervalMs = 10_000)
        )
    }

    @Test
    fun shouldCheck_due_atExactlyInterval() {
        val last = 1_000_000L
        assertTrue(
            PluginUpdateLogic.shouldCheck(lastCheckMs = last, nowMs = last + 10_000, intervalMs = 10_000)
        )
    }

    @Test
    fun shouldCheck_due_afterInterval() {
        val last = 1_000_000L
        assertTrue(
            PluginUpdateLogic.shouldCheck(lastCheckMs = last, nowMs = last + 999_999, intervalMs = 10_000)
        )
    }

    @Test
    fun shouldCheck_whenClockMovedBackwards() {
        assertTrue(
            PluginUpdateLogic.shouldCheck(lastCheckMs = 1_000_000, nowMs = 5_000, intervalMs = 10_000)
        )
    }

    // ---- dismissed-version suppression ----

    @Test
    fun dismissed_whenSameVersionDismissed() {
        assertTrue(PluginUpdateLogic.isDismissed(manifestVersionCode = 3, dismissedVersionCode = 3))
    }

    @Test
    fun notDismissed_whenNothingDismissed() {
        assertFalse(PluginUpdateLogic.isDismissed(manifestVersionCode = 3, dismissedVersionCode = null))
    }

    @Test
    fun notDismissed_whenNewerVersionThanDismissed() {
        // User dismissed v2; v3 must re-surface.
        assertFalse(PluginUpdateLogic.isDismissed(manifestVersionCode = 3, dismissedVersionCode = 2))
    }

    @Test
    fun dismissed_whenOlderThanDismissed() {
        // A version at or below the dismissed one stays suppressed.
        assertTrue(PluginUpdateLogic.isDismissed(manifestVersionCode = 1, dismissedVersionCode = 2))
    }

    @Test
    fun veryLargeVersionCode_stillCompares() {
        assertTrue(PluginUpdateLogic.isUpdateAvailable(Long.MAX_VALUE - 1, Long.MAX_VALUE))
        assertFalse(PluginUpdateLogic.isDismissed(Long.MAX_VALUE, dismissedVersionCode = Long.MAX_VALUE - 1))
    }

    // ---- evaluate: folds it all together ----

    @Test
    fun evaluate_upToDate_whenInstalledIsNewest() {
        val result = PluginUpdateLogic.evaluate(
            installedVersionCode = 2,
            hostVersionCode = 10,
            manifest = manifest(versionCode = 2),
            dismissedVersionCode = null,
        )
        assertEquals(UpdateEvaluation.UpToDate, result)
    }

    @Test
    fun evaluate_available_whenNewerAndCompatibleAndNotDismissed() {
        val m = manifest(versionCode = 3)
        val result = PluginUpdateLogic.evaluate(
            installedVersionCode = 2,
            hostVersionCode = 10,
            manifest = m,
            dismissedVersionCode = null,
        )
        assertEquals(UpdateEvaluation.Available(m), result)
    }

    @Test
    fun evaluate_incompatible_whenHostTooOld() {
        val result = PluginUpdateLogic.evaluate(
            installedVersionCode = 2,
            hostVersionCode = 4,
            manifest = manifest(versionCode = 3, minHostVersion = 5),
            dismissedVersionCode = null,
        )
        assertEquals(UpdateEvaluation.Incompatible(5), result)
    }

    @Test
    fun evaluate_dismissed_whenUserDismissedThisVersion() {
        val m = manifest(versionCode = 3)
        val result = PluginUpdateLogic.evaluate(
            installedVersionCode = 2,
            hostVersionCode = 10,
            manifest = m,
            dismissedVersionCode = 3,
        )
        assertEquals(UpdateEvaluation.Dismissed(m), result)
    }

    @Test
    fun evaluate_available_whenDismissedOlderVersion() {
        val m = manifest(versionCode = 4)
        val result = PluginUpdateLogic.evaluate(
            installedVersionCode = 2,
            hostVersionCode = 10,
            manifest = m,
            dismissedVersionCode = 3, // dismissed v3, but v4 is newer
        )
        assertEquals(UpdateEvaluation.Available(m), result)
    }

    @Test
    fun evaluate_incompatibleTakesPrecedenceOverDismissal() {
        // An incompatible host is withheld regardless of dismissal state.
        val result = PluginUpdateLogic.evaluate(
            installedVersionCode = 2,
            hostVersionCode = 1,
            manifest = manifest(versionCode = 3, minHostVersion = 5),
            dismissedVersionCode = 3,
        )
        assertEquals(UpdateEvaluation.Incompatible(5), result)
    }
}
