package com.viperplayer.data.social

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [BackendConfig]'s shared "is the backend configured?" logic — the exact notion the
 * account/session transports and [com.viperplayer.domain.social.SocialFeatures] all reuse. A blank or
 * un-substituted placeholder URL is unconfigured; a real URL is configured (trailing slash trimmed).
 */
class BackendConfigTest {

    @Test
    fun placeholderUrl_isNotConfigured() {
        assertFalse(BackendConfig.isConfigured(BackendConfig.PLACEHOLDER))
        assertNull(BackendConfig.baseUrlOf(BackendConfig.PLACEHOLDER))
    }

    @Test
    fun blankUrl_isNotConfigured() {
        assertFalse(BackendConfig.isConfigured(""))
        assertFalse(BackendConfig.isConfigured("   "))
        assertNull(BackendConfig.baseUrlOf(""))
    }

    @Test
    fun realUrl_isConfigured_andTrimsTrailingSlash() {
        assertTrue(BackendConfig.isConfigured("https://backend.test"))
        assertEquals("https://backend.test", BackendConfig.baseUrlOf("https://backend.test/"))
    }
}
