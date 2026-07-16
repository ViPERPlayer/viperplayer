package com.viperplayer.domain.lastfm

import com.viperplayer.domain.lastfm.ScrobbleQueueDrainer.ScrobbleBatchOutcome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Drain tests for [ScrobbleQueueDrainer] against a fake API + fake in-memory DAO, exercising the SAME
 * outcome classification the repository uses ([classify], mirroring
 * `LastfmRepositoryImpl.classifyScrobbleOutcome`).
 *
 * The MAJOR fix: a TRANSIENT Last.fm ApiError (code 11/16/29/8) must KEEP the queued rows for retry —
 * NOT delete them — while a PERMANENT one drops them, and an invalid-session-key (code 9) clears the
 * session. This proves queued scrobbles survive a rate-limit / maintenance window.
 */
class ScrobbleQueueDrainerTest {

    /** A minimal stand-in for a persisted scrobble row. */
    private data class Row(val id: Int)

    /** A fake in-memory queue DAO: holds rows and records deletions. */
    private class FakeDao(initial: List<Row>) {
        val rows = initial.toMutableList()
        fun getAll(): List<Row> = rows.toList()
        fun delete(batch: List<Row>) { rows.removeAll(batch.toSet()) }
    }

    /**
     * Mirror of the production result -> outcome mapping (kept in sync with
     * LastfmRepositoryImpl.classifyScrobbleOutcome). Modelled here without the Android LastfmResult so
     * the drain policy is testable JVM-side.
     */
    private sealed interface ApiResult {
        object Success : ApiResult
        data class ApiError(val code: Int) : ApiResult
        object NetworkError : ApiResult
    }

    private fun classify(result: ApiResult): ScrobbleBatchOutcome = when (result) {
        ApiResult.Success -> ScrobbleBatchOutcome.ACCEPTED
        is ApiResult.ApiError -> when {
            result.code == LastfmErrorCodes.INVALID_SESSION_KEY -> ScrobbleBatchOutcome.SESSION_INVALID
            LastfmErrorCodes.isRetryableLastfmError(result.code) -> ScrobbleBatchOutcome.RETRY_LATER
            else -> ScrobbleBatchOutcome.DROP
        }
        ApiResult.NetworkError -> ScrobbleBatchOutcome.RETRY_LATER
    }

    private suspend fun drainWith(dao: FakeDao, sessionCleared: BooleanArray, apiResult: (List<Row>) -> ApiResult) {
        ScrobbleQueueDrainer.drain(
            rows = dao.getAll(),
            batchSize = 2,
            submit = { batch -> classify(apiResult(batch)) },
            delete = { batch -> dao.delete(batch) },
            clearSession = { sessionCleared[0] = true },
        )
    }

    @Test
    fun transientApiError_keepsAllQueuedRows() = runTest {
        val dao = FakeDao(listOf(Row(1), Row(2), Row(3)))
        val cleared = booleanArrayOf(false)
        // Service offline (11) — a transient condition.
        drainWith(dao, cleared) { ApiResult.ApiError(LastfmErrorCodes.SERVICE_OFFLINE) }
        // MAJOR fix: nothing deleted, so the user's queued scrobbles survive the outage.
        assertEquals(listOf(Row(1), Row(2), Row(3)), dao.rows)
        assertFalse(cleared[0])
    }

    @Test
    fun rateLimit_keepsQueuedRows() = runTest {
        val dao = FakeDao(listOf(Row(1), Row(2)))
        val cleared = booleanArrayOf(false)
        drainWith(dao, cleared) { ApiResult.ApiError(LastfmErrorCodes.RATE_LIMIT_EXCEEDED) }
        assertEquals(listOf(Row(1), Row(2)), dao.rows)
    }

    @Test
    fun permanentApiError_dropsTheBatch() = runTest {
        val dao = FakeDao(listOf(Row(1), Row(2)))
        val cleared = booleanArrayOf(false)
        // Invalid params (6) — permanent; retrying can never succeed, so drop.
        drainWith(dao, cleared) { ApiResult.ApiError(LastfmErrorCodes.INVALID_PARAMETERS) }
        assertTrue(dao.rows.isEmpty())
        assertFalse(cleared[0])
    }

    @Test
    fun invalidSessionKey_clearsSessionAndKeepsRows() = runTest {
        val dao = FakeDao(listOf(Row(1), Row(2)))
        val cleared = booleanArrayOf(false)
        // Code 9: dead session — clear the stored session and stop (rows kept, not resubmitted).
        drainWith(dao, cleared) { ApiResult.ApiError(LastfmErrorCodes.INVALID_SESSION_KEY) }
        assertTrue("session should be cleared on code 9", cleared[0])
        assertEquals(listOf(Row(1), Row(2)), dao.rows)
    }

    @Test
    fun success_deletesEveryBatch() = runTest {
        val dao = FakeDao(listOf(Row(1), Row(2), Row(3)))
        val cleared = booleanArrayOf(false)
        drainWith(dao, cleared) { ApiResult.Success }
        assertTrue(dao.rows.isEmpty())
    }

    @Test
    fun transientOnSecondBatch_keepsRemainingBatchesButFirstAlreadyDeleted() = runTest {
        // batchSize=2: rows [1,2],[3,4]. First batch succeeds (deleted), second hits a rate limit (kept).
        val dao = FakeDao(listOf(Row(1), Row(2), Row(3), Row(4)))
        val cleared = booleanArrayOf(false)
        drainWith(dao, cleared) { batch ->
            if (batch.contains(Row(1))) ApiResult.Success
            else ApiResult.ApiError(LastfmErrorCodes.RATE_LIMIT_EXCEEDED)
        }
        // First batch drained, the transient second batch stays queued for the next attempt.
        assertEquals(listOf(Row(3), Row(4)), dao.rows)
    }

    @Test
    fun networkError_keepsQueuedRows() = runTest {
        val dao = FakeDao(listOf(Row(1), Row(2)))
        val cleared = booleanArrayOf(false)
        drainWith(dao, cleared) { ApiResult.NetworkError }
        assertEquals(listOf(Row(1), Row(2)), dao.rows)
    }
}
