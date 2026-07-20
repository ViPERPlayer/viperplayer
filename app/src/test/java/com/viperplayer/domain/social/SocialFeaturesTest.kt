package com.viperplayer.domain.social

import com.viperplayer.data.social.BackendConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * [SocialFeatures.enabled] must track the backend-configured gate. In the unit-test build `BuildConfig.
 * VIPER_BACKEND_URL` is the placeholder, so the gate is off; the "on for a real URL" half of the
 * contract is proven by [com.viperplayer.data.social.BackendConfigTest] over the same shared logic.
 */
class SocialFeaturesTest {

    @Test
    fun enabled_isFalse_forPlaceholderBuild() {
        // Unit tests compile with the placeholder VIPER_BACKEND_URL, so the social surface is gated off.
        assertFalse(SocialFeatures().enabled)
    }

    @Test
    fun enabled_matchesBackendConfig() {
        // The gate is exactly BackendConfig.isConfigured — no divergent "configured" notion.
        assertEquals(BackendConfig.isConfigured, SocialFeatures().enabled)
    }
}
