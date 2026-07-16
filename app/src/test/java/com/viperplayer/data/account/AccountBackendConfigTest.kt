package com.viperplayer.data.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [AccountBackendConfig.parse]: the `VIPER_BACKEND_GRPC` host:port parsing that gates
 * the account feature and picks TLS vs. plaintext (h2c).
 */
class AccountBackendConfigTest {

    @Test
    fun placeholder_isNotConfigured() {
        assertNull(AccountBackendConfig.parse(AccountBackendConfig.PLACEHOLDER))
    }

    @Test
    fun blankOrNull_isNotConfigured() {
        assertNull(AccountBackendConfig.parse(null))
        assertNull(AccountBackendConfig.parse(""))
        assertNull(AccountBackendConfig.parse("   "))
    }

    @Test
    fun hostAndPort_defaultsToTls() {
        val cfg = AccountBackendConfig.parse("api.viper.player:9090")!!
        assertEquals("api.viper.player", cfg.host)
        assertEquals(9090, cfg.port)
        assertTrue(cfg.useTls)
    }

    @Test
    fun plaintextPrefix_disablesTls() {
        val cfg = AccountBackendConfig.parse("plaintext://10.0.2.2:9090")!!
        assertEquals("10.0.2.2", cfg.host)
        assertEquals(9090, cfg.port)
        assertFalse(cfg.useTls)
    }

    @Test
    fun bareHost_usesDefaultPort() {
        val cfg = AccountBackendConfig.parse("api.viper.player")!!
        assertEquals("api.viper.player", cfg.host)
        assertEquals(9090, cfg.port)
        assertTrue(cfg.useTls)
    }

    @Test
    fun nonNumericPort_isNotConfigured() {
        assertNull(AccountBackendConfig.parse("host:notaport"))
    }

    @Test
    fun outOfRangePort_isNotConfigured() {
        assertNull(AccountBackendConfig.parse("host:0"))
        assertNull(AccountBackendConfig.parse("host:70000"))
    }

    @Test
    fun whitespaceIsTrimmed() {
        val cfg = AccountBackendConfig.parse("  host.example:8443  ")!!
        assertEquals("host.example", cfg.host)
        assertEquals(8443, cfg.port)
    }
}
