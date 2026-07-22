package com.viperplayer.data.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the HTTP status → domain-error mapping ([mapAccountHttpError]). These lock down the
 * contract the repository relies on: 401 → refresh/sign-out, 409 → email-taken, 400 → validation,
 * 5xx/408/429 → transient network (non-destructive), everything else → a rejection.
 *
 * The fallback strings are now caller-supplied (see [AccountErrorFallbacks]), so the tests pass the
 * English defaults explicitly and the mapper itself stays pure.
 */
class HttpErrorMappingTest {

    private val fallbacks = AccountErrorFallbacks(
        emailTaken = "That email is already registered",
        badRequest = "Check your details and try again",
        notFound = "Not found",
        requestFailedFormat = "Request failed (%1\$d)",
    )

    @Test
    fun unauthorized_mapsToUnauthenticated() {
        assertEquals(AccountApiResult.Unauthenticated, mapAccountHttpError(401, "token expired", fallbacks))
    }

    @Test
    fun conflict_mapsToRejected_withServerMessage() {
        assertEquals(
            AccountApiResult.Rejected("that email is taken"),
            mapAccountHttpError(409, "that email is taken", fallbacks),
        )
    }

    @Test
    fun conflict_blankMessage_usesFallback() {
        val result = mapAccountHttpError(409, "   ", fallbacks)
        assertTrue(result is AccountApiResult.Rejected)
        assertEquals("That email is already registered", (result as AccountApiResult.Rejected).message)
    }

    @Test
    fun badRequest_mapsToRejected() {
        assertEquals(
            AccountApiResult.Rejected("password too short"),
            mapAccountHttpError(400, "password too short", fallbacks),
        )
    }

    @Test
    fun notFound_mapsToRejected() {
        val result = mapAccountHttpError(404, null, fallbacks)
        assertTrue(result is AccountApiResult.Rejected)
        assertEquals("Not found", (result as AccountApiResult.Rejected).message)
    }

    @Test
    fun serverErrors_mapToNetworkError() {
        // Transient conditions must NOT clear the session (they surface as a retryable network error).
        assertEquals(AccountApiResult.NetworkError, mapAccountHttpError(500, "boom", fallbacks))
        assertEquals(AccountApiResult.NetworkError, mapAccountHttpError(502, null, fallbacks))
        assertEquals(AccountApiResult.NetworkError, mapAccountHttpError(503, "unavailable", fallbacks))
        assertEquals(AccountApiResult.NetworkError, mapAccountHttpError(504, null, fallbacks))
    }

    @Test
    fun timeoutAndRateLimit_mapToNetworkError() {
        assertEquals(AccountApiResult.NetworkError, mapAccountHttpError(408, null, fallbacks))
        assertEquals(AccountApiResult.NetworkError, mapAccountHttpError(429, "slow down", fallbacks))
    }

    @Test
    fun unknownStatus_mapsToRejected_withStatusInFallback() {
        val result = mapAccountHttpError(418, null, fallbacks)
        assertTrue(result is AccountApiResult.Rejected)
        assertEquals("Request failed (418)", (result as AccountApiResult.Rejected).message)
    }
}
