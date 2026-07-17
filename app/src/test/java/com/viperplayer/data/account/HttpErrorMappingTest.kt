package com.viperplayer.data.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the HTTP status → domain-error mapping ([mapAccountHttpError]). These lock down the
 * contract the repository relies on: 401 → refresh/sign-out, 409 → email-taken, 400 → validation,
 * 5xx/408/429 → transient network (non-destructive), everything else → a rejection.
 */
class HttpErrorMappingTest {

    @Test
    fun unauthorized_mapsToUnauthenticated() {
        assertEquals(AccountApiResult.Unauthenticated, mapAccountHttpError(401, "token expired"))
    }

    @Test
    fun conflict_mapsToRejected_withServerMessage() {
        assertEquals(
            AccountApiResult.Rejected("that email is taken"),
            mapAccountHttpError(409, "that email is taken"),
        )
    }

    @Test
    fun conflict_blankMessage_usesFallback() {
        val result = mapAccountHttpError(409, "   ")
        assertTrue(result is AccountApiResult.Rejected)
        assertEquals("That email is already registered", (result as AccountApiResult.Rejected).message)
    }

    @Test
    fun badRequest_mapsToRejected() {
        assertEquals(AccountApiResult.Rejected("password too short"), mapAccountHttpError(400, "password too short"))
    }

    @Test
    fun notFound_mapsToRejected() {
        val result = mapAccountHttpError(404, null)
        assertTrue(result is AccountApiResult.Rejected)
    }

    @Test
    fun serverErrors_mapToNetworkError() {
        // Transient conditions must NOT clear the session (they surface as a retryable network error).
        assertEquals(AccountApiResult.NetworkError, mapAccountHttpError(500, "boom"))
        assertEquals(AccountApiResult.NetworkError, mapAccountHttpError(502, null))
        assertEquals(AccountApiResult.NetworkError, mapAccountHttpError(503, "unavailable"))
        assertEquals(AccountApiResult.NetworkError, mapAccountHttpError(504, null))
    }

    @Test
    fun timeoutAndRateLimit_mapToNetworkError() {
        assertEquals(AccountApiResult.NetworkError, mapAccountHttpError(408, null))
        assertEquals(AccountApiResult.NetworkError, mapAccountHttpError(429, "slow down"))
    }

    @Test
    fun unknownStatus_mapsToRejected_withStatusInFallback() {
        val result = mapAccountHttpError(418, null)
        assertTrue(result is AccountApiResult.Rejected)
        assertTrue((result as AccountApiResult.Rejected).message.contains("418"))
    }
}
