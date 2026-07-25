package com.viperplayer.data.source

import com.viperplayer.domain.model.MediaId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for [shouldBlockForOffline], the offline-mode gate applied at the plugin fetch
 * boundary. A remote-plugin browse/search/detail read is blocked only when offline mode is on; the
 * embedded local library is never blocked, and calls flagged [allowOffline] (stream resolution and
 * push/sync writes) always pass through.
 */
class OfflineGateTest {

    @Test
    fun blocksRemoteRead_whenOfflineOn() {
        assertTrue(shouldBlockForOffline("testsource", offlineMode = true, allowOffline = false))
    }

    @Test
    fun allowsRemoteRead_whenOfflineOff() {
        assertFalse(shouldBlockForOffline("testsource", offlineMode = false, allowOffline = false))
    }

    @Test
    fun neverBlocksLocalLibrary_evenWhenOfflineOn() {
        assertFalse(
            shouldBlockForOffline(MediaId.LOCAL_PLUGIN_ID, offlineMode = true, allowOffline = false)
        )
    }

    @Test
    fun allowOffline_bypassesGate_forRemotePlugin() {
        // Stream resolution / push-sync writes pass allowOffline = true and must not be gated.
        assertFalse(shouldBlockForOffline("testsource", offlineMode = true, allowOffline = true))
    }
}
