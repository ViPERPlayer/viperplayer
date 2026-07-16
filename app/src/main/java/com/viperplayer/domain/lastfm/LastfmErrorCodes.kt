package com.viperplayer.domain.lastfm

/**
 * Pure, Android-free classification of Last.fm API error codes (the `{"error":N}` envelope Last.fm
 * returns, sometimes even with HTTP 200). Kept side-effect free so the retry/drop decision for the
 * offline scrobble queue is unit-testable without any network or database.
 *
 * Codes are from https://www.last.fm/api/errorcodes. The distinction that matters for the queue:
 *  - TRANSIENT (retryable): the same request may succeed on a later attempt, so the queued scrobbles
 *    must be KEPT and retried — never deleted. Deleting on these permanently loses a user's queued
 *    scrobbles during a rate-limit / maintenance window.
 *  - PERMANENT (non-retryable): retrying with the same request can never succeed (bad params, bad
 *    signature, revoked/suspended key), so the batch is dropped.
 */
object LastfmErrorCodes {

    /** Invalid API key / service does not accept it. Permanent. */
    const val INVALID_API_KEY = 10

    /** Authentication failed. Permanent (won't self-heal on retry). */
    const val AUTHENTICATION_FAILED = 4

    /** Invalid parameters — the request itself is malformed. Permanent. */
    const val INVALID_PARAMETERS = 6

    /** Invalid resource specified. Permanent. */
    const val INVALID_RESOURCE = 7

    /** Operation failed — a generic server-side failure Last.fm asks clients to try again on. Transient. */
    const val OPERATION_FAILED = 8

    /** Invalid session key — the user must re-authenticate. Permanent; also triggers a local logout. */
    const val INVALID_SESSION_KEY = 9

    /** Service offline — the service is temporarily unavailable. Transient. */
    const val SERVICE_OFFLINE = 11

    /** Invalid method signature supplied. Permanent. */
    const val INVALID_SIGNATURE = 13

    /** Temporary processing error / "try again later". Transient. */
    const val TEMPORARILY_UNAVAILABLE = 16

    /** Suspended API key — access has been revoked. Permanent. */
    const val SUSPENDED_API_KEY = 26

    /** Rate limit exceeded — back off and retry later. Transient. */
    const val RATE_LIMIT_EXCEEDED = 29

    /**
     * The transient conditions: the request may succeed on a later attempt, so queued scrobbles must be
     * KEPT and retried rather than dropped. Any code NOT in this set is treated as permanent.
     */
    private val RETRYABLE = setOf(
        OPERATION_FAILED,      // 8
        SERVICE_OFFLINE,       // 11
        TEMPORARILY_UNAVAILABLE, // 16
        RATE_LIMIT_EXCEEDED,   // 29
    )

    /**
     * Whether a Last.fm error [code] is a TRANSIENT condition whose request should be retried later
     * (queue KEPT), rather than a PERMANENT rejection (queue dropped).
     *
     * Returns true for codes 8, 11, 16, 29; false for everything else (e.g. 4 auth failed, 6 invalid
     * params, 9 invalid session key, 10 invalid API key, 13 invalid signature, 26 suspended key, and
     * the internal -1 "malformed response").
     */
    fun isRetryableLastfmError(code: Int): Boolean = code in RETRYABLE
}
