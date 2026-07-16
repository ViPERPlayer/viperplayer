package com.viperplayer.domain.lastfm

/**
 * The pure, Android-free control flow for draining the offline scrobble queue. Splits the queued rows
 * into Last.fm-sized batches, submits each oldest-first, and decides — per the [ScrobbleBatchOutcome]
 * the submit returns — whether to delete the batch, keep it for a later retry, or clear the dead
 * session. Holds no I/O of its own: reading rows, submitting, deleting, and clearing the session are
 * all passed in as suspend callbacks, so the whole policy is unit-testable with fakes and no database.
 *
 * @param R the opaque row type (a persisted entity); the drainer never inspects it beyond batching.
 */
object ScrobbleQueueDrainer {

    /**
     * The outcome of submitting one batch, mapped from the network result by the caller. This is what
     * the drain policy branches on — the pure classification ([LastfmErrorCodes.isRetryableLastfmError],
     * the invalid-session-key check) is applied by the caller when building this value.
     */
    enum class ScrobbleBatchOutcome {
        /** Accepted — delete this batch and continue with the next. */
        ACCEPTED,

        /** Permanently rejected (bad params/signature/suspended key) — drop this batch, continue. */
        DROP,

        /** Transient failure (rate limit / maintenance / network) — KEEP this batch and stop the drain. */
        RETRY_LATER,

        /** The session key is dead — clear the stored session (local logout) and stop the drain. */
        SESSION_INVALID,

        /** Nothing was sent (e.g. not configured) — stop the drain, keep the batch. */
        NOT_SENT,
    }

    /**
     * Drains [rows] (already oldest-first) in batches of at most [batchSize].
     *
     * @param submit sends a batch and returns its [ScrobbleBatchOutcome].
     * @param delete removes an accepted/dropped batch from the queue.
     * @param clearSession clears the stored session on [ScrobbleBatchOutcome.SESSION_INVALID].
     */
    suspend fun <R> drain(
        rows: List<R>,
        batchSize: Int = LastfmRequests.MAX_BATCH,
        submit: suspend (batch: List<R>) -> ScrobbleBatchOutcome,
        delete: suspend (batch: List<R>) -> Unit,
        clearSession: suspend () -> Unit,
    ) {
        if (rows.isEmpty()) return
        for (batch in rows.chunked(batchSize)) {
            when (submit(batch)) {
                ScrobbleBatchOutcome.ACCEPTED -> delete(batch)
                ScrobbleBatchOutcome.DROP -> delete(batch)
                ScrobbleBatchOutcome.RETRY_LATER -> return
                ScrobbleBatchOutcome.SESSION_INVALID -> {
                    clearSession()
                    return
                }
                ScrobbleBatchOutcome.NOT_SENT -> return
            }
        }
    }
}
