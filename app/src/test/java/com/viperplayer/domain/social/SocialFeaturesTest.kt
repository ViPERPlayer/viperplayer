package com.viperplayer.domain.social

import com.viperplayer.domain.config.BackendAvailability
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SocialFeatures.enabled] must be exactly the backend-configured gate.
 *
 * The gate is now injected as a [BackendAvailability], so both halves of the contract are testable
 * here. Previously this could only assert the "off" half — the gate read `BuildConfig` directly, and
 * the unit-test build always compiles with the placeholder URL, so the "on" case was unreachable and
 * the second test was a tautology against the same constant it was verifying.
 *
 * That `BackendConfig` maps a blank/placeholder URL to "not configured" is covered separately by
 * [com.viperplayer.data.social.BackendConfigTest].
 */
class SocialFeaturesTest {

    @Test
    fun enabled_isFalse_whenBackendIsNotConfigured() {
        assertFalse(SocialFeatures(BackendAvailability { false }).enabled)
    }

    @Test
    fun enabled_isTrue_whenBackendIsConfigured() {
        assertTrue(SocialFeatures(BackendAvailability { true }).enabled)
    }

    @Test
    fun enabled_isReadEveryTime_soLateConfigurationIsPickedUp() {
        var configured = false
        val features = SocialFeatures(BackendAvailability { configured })

        assertFalse(features.enabled)
        configured = true
        assertTrue("enabled must delegate on each read, not cache at construction", features.enabled)
    }
}
