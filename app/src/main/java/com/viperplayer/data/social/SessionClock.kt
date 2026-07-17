package com.viperplayer.data.social

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * Runs the NTP-style four-timestamp clock-sync loop against a [JamConnection] to map the local
 * monotonic clock onto the server's, so the shared playback [com.viperplayer.domain.model.SessionPlayback]
 * (whose anchors are in server-monotonic microseconds) can be extrapolated locally.
 *
 * Each ping: send `time_req {t0 = nowNs()}`; on the matching `time_resp {t0, t1, t2}` stamp `t3 =
 * nowNs()` and compute, per internal/wire clock.go:
 *
 *     offsetNs = ((t1 − t0) + (t2 − t3)) / 2   // add to client-monotonic → server-monotonic
 *     rttNs    = (t3 − t0) − (t2 − t1)
 *
 * The lowest-RTT sample in a sliding window is the most accurate, so its offset is the one kept.
 * [serverNowUs] then returns `(nowNs() + offset) / 1000` (server monotonic µs == monotonic ns / 1000,
 * same start reference), or null until the first sync. Pings burst fast on connect then settle to a
 * steady interval.
 *
 * Pure/testable: [nowNs], [send] and [responses] are injected so a unit test can script the timestamps
 * and drive the math deterministically. The production wiring passes the [JamConnection]'s send/flow
 * and `System.nanoTime`.
 *
 * Not started until [start]; call [stop] (or cancel the owning scope) to end the loop.
 */
class SessionClock(
    private val nowNs: () -> Long = System::nanoTime,
    private val send: suspend (TimeReqDto) -> Unit,
    private val responses: Flow<TimeRespDto>,
    private val fastPings: Int = FAST_PINGS,
    private val fastIntervalMs: Long = FAST_INTERVAL_MS,
    private val steadyIntervalMs: Long = STEADY_INTERVAL_MS,
    private val windowSize: Int = WINDOW_SIZE,
    private val responseTimeoutMs: Long = RESPONSE_TIMEOUT_MS,
    // Test-only cap on the number of pings the loop issues before ending (null = run until stopped).
    // Keeps a virtual-time test's `advanceUntilIdle` from spinning on the otherwise-infinite loop.
    private val maxPings: Int? = null,
) {
    private val _synced = MutableStateFlow(false)

    /** True once at least one clock sample has been folded in — [serverNowUs] returns non-null after. */
    val synced: StateFlow<Boolean> = _synced.asStateFlow()

    /**
     * The offset (ns) to add to a client-monotonic reading to get server-monotonic, or null before the
     * first sync. `@Volatile` because [serverNowUs] may be read from a different thread than the loop.
     */
    @Volatile
    private var offsetNs: Long? = null

    /** Sliding window of recent (rtt, offset) samples; the min-RTT sample's offset is authoritative. */
    private val window = ArrayDeque<Sample>()

    private var loopJob: Job? = null

    /**
     * Server-monotonic microseconds now, or null until the first successful sync. Layer 2 combines this
     * with the playback anchor to extrapolate the current position.
     */
    fun serverNowUs(): Long? {
        val offset = offsetNs ?: return null
        return (nowNs() + offset) / 1000
    }

    /** Launches the ping loop on [scope]. Idempotent-ish: a prior loop is cancelled first. */
    fun start(scope: CoroutineScope) {
        loopJob?.cancel()
        loopJob = scope.launch { runLoop() }
    }

    /** Stops the ping loop. Safe to call when never started. */
    fun stop() {
        loopJob?.cancel()
        loopJob = null
    }

    private suspend fun runLoop() {
        var sent = 0
        while (currentCoroutineContext().isActive) {
            pingOnce()
            sent++
            if (maxPings != null && sent >= maxPings) break
            val interval = if (sent < fastPings) fastIntervalMs else steadyIntervalMs
            delay(interval)
        }
    }

    /**
     * Sends one ping and folds the matching response. Exposed (internal) so the unit test can drive a
     * single deterministic round-trip without the timing loop. Matches the response by echoed t0 (the
     * server echoes it), ignoring any stale/mismatched frames until the timeout.
     */
    internal suspend fun pingOnce() {
        val t0 = nowNs()
        try {
            send(TimeReqDto(t0 = t0))
        } catch (e: Exception) {
            Timber.w("Clock ping send failed: ${e.javaClass.simpleName}")
            return
        }
        // Match by the echoed t0 (the server echoes it), skipping any stale/mismatched frames until the
        // timeout. `first` completes the collect as soon as it matches.
        val resp = withTimeoutOrNull(responseTimeoutMs) {
            responses.first { it.t0 == t0 }
        } ?: return
        val t3 = nowNs()
        fold(resp, t3)
    }

    /** Folds a completed four-timestamp set into the window and updates the kept offset. */
    private fun fold(resp: TimeRespDto, t3: Long) {
        val offset = ((resp.t1 - resp.t0) + (resp.t2 - t3)) / 2
        val rtt = (t3 - resp.t0) - (resp.t2 - resp.t1)
        window.addLast(Sample(rttNs = rtt, offsetNs = offset))
        while (window.size > windowSize) window.removeFirst()
        // Lowest RTT in the window = least queuing/jitter = most trustworthy offset.
        val best = window.minByOrNull { it.rttNs } ?: return
        offsetNs = best.offsetNs
        _synced.value = true
    }

    private data class Sample(val rttNs: Long, val offsetNs: Long)

    companion object {
        const val FAST_PINGS = 5
        const val FAST_INTERVAL_MS = 500L
        const val STEADY_INTERVAL_MS = 5_000L
        const val WINDOW_SIZE = 8
        const val RESPONSE_TIMEOUT_MS = 5_000L
    }
}
