package com.viperplayer.data.sync.push

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the pure retry/drop/keep policy: how a push outcome (and the entry's prior attempt
 * count) maps to what happens to the outbox entry.
 */
class PushPolicyTest {

    @Test
    fun applied_isDeleted() {
        assertEquals(OutboxAction.DELETE, PushPolicy.decide(PushOutcome.APPLIED, attempts = 0))
    }

    @Test
    fun permanent_isDropped() {
        assertEquals(OutboxAction.DELETE, PushPolicy.decide(PushOutcome.PERMANENT, attempts = 0))
    }

    @Test
    fun unsupported_isSkippedNotDropped() {
        // Kept (skipped) so a future plugin update can apply it — never a hard failure.
        assertEquals(OutboxAction.SKIP, PushPolicy.decide(PushOutcome.UNSUPPORTED, attempts = 0))
    }

    @Test
    fun retryable_belowBudget_isRetried() {
        assertEquals(OutboxAction.RETRY, PushPolicy.decide(PushOutcome.RETRYABLE, attempts = 0))
        assertEquals(OutboxAction.RETRY, PushPolicy.decide(PushOutcome.RETRYABLE, attempts = PushPolicy.MAX_ATTEMPTS - 2))
    }

    @Test
    fun retryable_atBudget_isDropped() {
        // One more attempt would reach MAX_ATTEMPTS, so give up rather than retry forever.
        assertEquals(OutboxAction.DELETE, PushPolicy.decide(PushOutcome.RETRYABLE, attempts = PushPolicy.MAX_ATTEMPTS - 1))
        assertEquals(OutboxAction.DELETE, PushPolicy.decide(PushOutcome.RETRYABLE, attempts = PushPolicy.MAX_ATTEMPTS))
    }
}
