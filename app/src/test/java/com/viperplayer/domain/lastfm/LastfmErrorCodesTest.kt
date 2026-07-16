package com.viperplayer.domain.lastfm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [LastfmErrorCodes.isRetryableLastfmError]: the transient (retry, keep queue) vs
 * permanent (drop queue) split that stops a rate-limit / maintenance window from permanently losing a
 * user's queued scrobbles.
 */
class LastfmErrorCodesTest {

    @Test
    fun transientCodes_areRetryable() {
        // 8 operation failed, 11 service offline, 16 temporarily unavailable, 29 rate limit.
        assertTrue(LastfmErrorCodes.isRetryableLastfmError(LastfmErrorCodes.OPERATION_FAILED))
        assertTrue(LastfmErrorCodes.isRetryableLastfmError(LastfmErrorCodes.SERVICE_OFFLINE))
        assertTrue(LastfmErrorCodes.isRetryableLastfmError(LastfmErrorCodes.TEMPORARILY_UNAVAILABLE))
        assertTrue(LastfmErrorCodes.isRetryableLastfmError(LastfmErrorCodes.RATE_LIMIT_EXCEEDED))
        // Same by raw value, to guard against a constant being retargeted.
        assertTrue(LastfmErrorCodes.isRetryableLastfmError(8))
        assertTrue(LastfmErrorCodes.isRetryableLastfmError(11))
        assertTrue(LastfmErrorCodes.isRetryableLastfmError(16))
        assertTrue(LastfmErrorCodes.isRetryableLastfmError(29))
    }

    @Test
    fun permanentCodes_areNotRetryable() {
        // 4 auth failed, 6 invalid params, 9 invalid session key, 10 invalid API key,
        // 13 invalid signature, 26 suspended key, 7 invalid resource.
        assertFalse(LastfmErrorCodes.isRetryableLastfmError(LastfmErrorCodes.AUTHENTICATION_FAILED))
        assertFalse(LastfmErrorCodes.isRetryableLastfmError(LastfmErrorCodes.INVALID_PARAMETERS))
        assertFalse(LastfmErrorCodes.isRetryableLastfmError(LastfmErrorCodes.INVALID_SESSION_KEY))
        assertFalse(LastfmErrorCodes.isRetryableLastfmError(LastfmErrorCodes.INVALID_API_KEY))
        assertFalse(LastfmErrorCodes.isRetryableLastfmError(LastfmErrorCodes.INVALID_SIGNATURE))
        assertFalse(LastfmErrorCodes.isRetryableLastfmError(LastfmErrorCodes.SUSPENDED_API_KEY))
        assertFalse(LastfmErrorCodes.isRetryableLastfmError(LastfmErrorCodes.INVALID_RESOURCE))
    }

    @Test
    fun unknownAndInternalCodes_areNotRetryable() {
        // Unknown codes and the internal -1 "malformed response" default to permanent (drop, not loop).
        assertFalse(LastfmErrorCodes.isRetryableLastfmError(-1))
        assertFalse(LastfmErrorCodes.isRetryableLastfmError(0))
        assertFalse(LastfmErrorCodes.isRetryableLastfmError(999))
    }
}
