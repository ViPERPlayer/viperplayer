package com.viperplayer.data.account

import com.viperplayer.domain.account.AccountApiResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for the authenticated-call policy ([runAuthenticated]) — the authed path behind GetMe
 * (and later library calls):
 *
 *  - a call that succeeds first time is NOT retried;
 *  - an UNAUTHENTICATED response triggers exactly one force-refresh + retry with the new token;
 *  - if refresh fails, the result is UNAUTHENTICATED and the call is not retried;
 *  - a null token short-circuits to UNAUTHENTICATED without calling the request.
 */
class AuthRetryTest {

    @Test
    fun success_firstTry_doesNotRefreshOrRetry() = runTest {
        var refreshCalls = 0
        val tokensUsed = mutableListOf<String>()

        val result = runAuthenticated<String>(
            provideToken = { "token-1" },
            forceRefresh = { refreshCalls++; "should-not-be-used" },
            call = { token ->
                tokensUsed += token
                AccountApiResult.Success("ok")
            },
        )

        assertEquals(AccountApiResult.Success("ok"), result)
        assertEquals(listOf("token-1"), tokensUsed)
        assertEquals(0, refreshCalls)
    }

    @Test
    fun unauthenticated_refreshesAndRetriesOnceWithNewToken() = runTest {
        var refreshCalls = 0
        val tokensUsed = mutableListOf<String>()

        val result = runAuthenticated<String>(
            provideToken = { "stale-token" },
            forceRefresh = { refreshCalls++; "fresh-token" },
            call = { token ->
                tokensUsed += token
                if (token == "stale-token") AccountApiResult.Unauthenticated
                else AccountApiResult.Success("ok-after-refresh")
            },
        )

        assertEquals(AccountApiResult.Success("ok-after-refresh"), result)
        assertEquals(listOf("stale-token", "fresh-token"), tokensUsed)
        assertEquals(1, refreshCalls)
    }

    @Test
    fun unauthenticated_refreshFails_returnsUnauthenticated_noRetry() = runTest {
        val tokensUsed = mutableListOf<String>()

        val result = runAuthenticated<String>(
            provideToken = { "stale-token" },
            forceRefresh = { null }, // refresh token rejected / offline
            call = { token ->
                tokensUsed += token
                AccountApiResult.Unauthenticated
            },
        )

        assertEquals(AccountApiResult.Unauthenticated, result)
        assertEquals(listOf("stale-token"), tokensUsed) // called once, not retried
    }

    @Test
    fun unauthenticated_retryStillUnauthenticated_returnsUnauthenticated() = runTest {
        var callCount = 0

        val result = runAuthenticated<String>(
            provideToken = { "stale-token" },
            forceRefresh = { "fresh-but-also-bad" },
            call = { callCount++; AccountApiResult.Unauthenticated },
        )

        assertEquals(AccountApiResult.Unauthenticated, result)
        assertEquals(2, callCount) // original + one retry, no more
    }

    @Test
    fun noToken_shortCircuits_withoutCallingRpc() = runTest {
        var callCount = 0

        val result = runAuthenticated<String>(
            provideToken = { null },
            forceRefresh = { fail_shouldNotRefresh() },
            call = { callCount++; AccountApiResult.Success("nope") },
        )

        assertEquals(AccountApiResult.Unauthenticated, result)
        assertEquals(0, callCount)
    }

    @Test
    fun networkError_firstTry_isReturned_notTreatedAsAuthFailure() = runTest {
        var refreshCalls = 0

        val result = runAuthenticated<String>(
            provideToken = { "token" },
            forceRefresh = { refreshCalls++; "x" },
            call = { AccountApiResult.NetworkError },
        )

        assertEquals(AccountApiResult.NetworkError, result)
        assertEquals(0, refreshCalls) // network error must NOT trigger a refresh
    }

    private fun fail_shouldNotRefresh(): String? {
        throw AssertionError("forceRefresh should not be called when there is no token")
    }
}
