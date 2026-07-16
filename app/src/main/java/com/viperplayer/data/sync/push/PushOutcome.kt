package com.viperplayer.data.sync.push

/**
 * The outcome of attempting to push one mutation to a plugin. Pure classification — the drain manager
 * maps a plugin call's result/exception into one of these, and this table decides what happens to the
 * outbox entry. No Android here.
 */
enum class PushOutcome {
    /** The plugin applied the change (or it was already in the desired state — a reconciled no-op). */
    APPLIED,

    /**
     * The plugin (or this plugin build) doesn't support this write. The entry is not an error; it is
     * kept as SKIPPED so a future plugin update can apply it, and never counts as a failure/retry.
     */
    UNSUPPORTED,

    /** A transient failure (network, timeout, plugin busy). Keep the entry and retry it later. */
    RETRYABLE,

    /**
     * A permanent failure (invalid request, the remote object no longer exists, auth revoked). The
     * entry can never succeed, so it is dropped rather than retried forever.
     */
    PERMANENT,
}

/** What the drain should do with an outbox entry after a push attempt. */
enum class OutboxAction {
    /** Delete the entry — done (applied) or hopeless (permanent). */
    DELETE,

    /** Keep the entry and bump its attempt count for a later retry. */
    RETRY,

    /** Keep the entry, marked skipped (unsupported) — not retried on a normal drain. */
    SKIP,
}

/**
 * Pure policy mapping a [PushOutcome] (and the entry's prior attempt count) to the [OutboxAction].
 * Kept separate and Android-free so the retry/drop/keep decisions are unit-testable in isolation.
 */
object PushPolicy {

    /** Give up on an entry that has failed transiently this many times, to bound retry storms. */
    const val MAX_ATTEMPTS = 8

    fun decide(outcome: PushOutcome, attempts: Int): OutboxAction = when (outcome) {
        PushOutcome.APPLIED -> OutboxAction.DELETE
        PushOutcome.PERMANENT -> OutboxAction.DELETE
        PushOutcome.UNSUPPORTED -> OutboxAction.SKIP
        // A retryable failure that has exhausted its budget is treated as permanent (dropped) so the
        // outbox can't grow unbounded on a persistently failing target.
        PushOutcome.RETRYABLE -> if (attempts + 1 >= MAX_ATTEMPTS) OutboxAction.DELETE else OutboxAction.RETRY
    }
}
