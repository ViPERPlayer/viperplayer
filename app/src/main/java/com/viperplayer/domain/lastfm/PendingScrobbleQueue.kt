package com.viperplayer.domain.lastfm

/**
 * Pure, Android-free logic for the offline scrobble queue: adding with dedupe, ordering for a drain,
 * and slicing into Last.fm-sized batches. The actual persistence (a Room table) delegates its
 * decisions here so the ordering/dedupe rules are unit-testable without a database.
 *
 * Ordering rule: scrobbles are drained OLDEST FIRST (ascending start timestamp). Last.fm accepts past
 * timestamps, and submitting chronologically keeps a user's history consistent if a partial batch
 * succeeds.
 */
object PendingScrobbleQueue {

    /**
     * Adds [scrobble] to [existing], de-duplicating on [PendingScrobble.dedupeKey] (same
     * artist+title+start-timestamp). Returns the list unchanged when it is already present, so a retry
     * or a double-fire can never enqueue the same scrobble twice.
     */
    fun add(existing: List<PendingScrobble>, scrobble: PendingScrobble): List<PendingScrobble> {
        if (existing.any { it.dedupeKey == scrobble.dedupeKey }) return existing
        return existing + scrobble
    }

    /** [existing] sorted oldest-first by start timestamp (the drain order). Stable for equal stamps. */
    fun drainOrder(existing: List<PendingScrobble>): List<PendingScrobble> =
        existing.sortedBy { it.timestampSeconds }

    /**
     * Splits [existing] (in drain order) into batches of at most [LastfmRequests.MAX_BATCH], ready to
     * be sent one `track.scrobble` call each. Empty input ⇒ empty list.
     */
    fun batches(existing: List<PendingScrobble>): List<List<PendingScrobble>> =
        drainOrder(existing).chunked(LastfmRequests.MAX_BATCH)

    /**
     * Removes the [submitted] scrobbles (matched by dedupe key) from [existing], preserving the order
     * of the remaining items. Used after a batch is accepted so only the un-submitted tail is retried.
     */
    fun remove(
        existing: List<PendingScrobble>,
        submitted: Collection<PendingScrobble>,
    ): List<PendingScrobble> {
        val submittedKeys = submitted.mapTo(HashSet()) { it.dedupeKey }
        return existing.filterNot { it.dedupeKey in submittedKeys }
    }
}
