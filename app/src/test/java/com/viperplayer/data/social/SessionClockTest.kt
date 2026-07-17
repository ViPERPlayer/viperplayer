package com.viperplayer.data.social

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deterministic tests for [SessionClock]'s four-timestamp math and min-RTT selection.
 *
 * The clock is driven with an injected [nowNs] source (a scripted queue of client-monotonic readings —
 * t0 send then t3 receipt per ping) and an injected `responses` flow. The fake "server" replies inline
 * from within [send] by pushing the scripted [TimeRespDto] (echoing the just-sent t0) onto a `replay=1`
 * flow, so the clock's `first { it.t0 == t0 }` resolves immediately with no timeout race under virtual
 * time. Offset/rtt are then fully determined and asserted against the backend formula
 * (internal/wire clock.go):
 *
 *     offset = ((t1 − t0) + (t2 − t3)) / 2
 *     rtt    = (t3 − t0) − (t2 − t1)
 */
class SessionClockTest {

    /** A [nowNs] source that returns each scripted value once, in order. */
    private class ScriptedNow(values: List<Long>) : () -> Long {
        private val q = ArrayDeque(values)
        override fun invoke(): Long = q.removeFirst()
    }

    @Test
    fun singlePing_viaLoop_computesOffset_andExposesServerNow() = runTest {
        // Exercises the real start()/loop path. t0 = 1000 (send), t3 = 1200 (receipt); server t1 = 5100,
        // t2 = 5150. offset = ((5100-1000)+(5150-1200))/2 = (4100+3950)/2 = 4025.
        // serverNowUs at a later now=2000: (2000 + 4025)/1000 = 6.
        val responses = MutableSharedFlow<TimeRespDto>(replay = 1)
        val clock = SessionClock(
            nowNs = ScriptedNow(listOf(1000L, 1200L, 2000L)), // t0, t3, then serverNowUs read
            // Fake server: reply inline with the scripted server times, echoing the sent t0.
            send = { req -> responses.emit(TimeRespDto(t0 = req.t0, t1 = 5100, t2 = 5150)) },
            responses = responses,
            maxPings = 1,
        )
        assertNull("no offset before first sync", clock.serverNowUs())
        assertFalse(clock.synced.value)

        clock.start(this)
        advanceUntilIdle()

        assertTrue(clock.synced.value)
        // offset 4025 → serverNowUs = (2000 + 4025)/1000 = 6.
        assertEquals(6L, clock.serverNowUs())
    }

    @Test
    fun keepsMinRttSampleOffset_acrossPings() = runTest {
        // Two round-trips. Ping A: high RTT. Ping B: low RTT. The kept offset must be B's (lowest RTT),
        // even though A folded first.
        //
        // Ping A: t0=0,  t3=1000, t1=t2=10_000 → rtt=(1000-0)-(0)=1000, offset=((10000-0)+(10000-1000))/2=9500.
        // Ping B: t0=10, t3=110,  t1=t2=20_000 → rtt=(110-10)-(0)=100,  offset=((20000-10)+(20000-110))/2=19940.
        // serverNow read at now=10 → (10 + 19940)/1000 = 19 (B's offset, lower rtt).
        val responses = MutableSharedFlow<TimeRespDto>(replay = 1)
        // Each send replies with the server times keyed to the sent t0 (0 → A, 10 → B).
        val serverTimes = mapOf(0L to (10_000L to 10_000L), 10L to (20_000L to 20_000L))
        val clock = SessionClock(
            nowNs = ScriptedNow(listOf(0L, 1000L, 10L, 110L, 10L)), // A(t0,t3), B(t0,t3), serverNow read
            send = { req ->
                val (t1, t2) = serverTimes.getValue(req.t0)
                responses.emit(TimeRespDto(t0 = req.t0, t1 = t1, t2 = t2))
            },
            responses = responses,
            fastPings = 5,
            fastIntervalMs = 1, // tiny interval so the loop advances quickly under virtual time
            maxPings = 2,
        )

        clock.start(this)
        advanceUntilIdle()

        // Kept offset is B's (19940), lowest RTT (100 < 1000): serverNow = (10 + 19940)/1000 = 19.
        assertEquals(19L, clock.serverNowUs())
    }

    @Test
    fun pingOnce_foldsOneRoundTrip_deterministically() = runTest {
        // Drive a single round-trip directly (no loop) to isolate the fold math.
        // t0=100, t3=300, t1=2000, t2=2050 → offset=((2000-100)+(2050-300))/2 = (1900+1750)/2 = 1825.
        val responses = MutableSharedFlow<TimeRespDto>(replay = 1)
        val clock = SessionClock(
            nowNs = ScriptedNow(listOf(100L, 300L, 100L)), // t0, t3, serverNow read
            send = { req -> responses.emit(TimeRespDto(t0 = req.t0, t1 = 2000, t2 = 2050)) },
            responses = responses,
        )
        clock.pingOnce()

        // serverNow at now=100 → (100 + 1825)/1000 = 1.
        assertEquals(1L, clock.serverNowUs())
        assertTrue(clock.synced.value)
    }
}
