package com.viperplayer.data.sync.push

/**
 * One recorded, still-pending local mutation as the pure core sees it — a [mutation] plus the
 * monotonically increasing [seq] it was enqueued at. [seq] gives a total order (create-before-add,
 * FIFO within a target) without depending on wall-clock timestamps, which can tie or go backwards.
 *
 * This is deliberately Android/Room-free: [com.viperplayer.data.sync.push.OutboxDao] maps its Room
 * rows to these before handing them to [MutationCoalescer], and the drain manager pushes the
 * survivors. The mapping is what lets the whole coalescing/ordering logic be JVM-unit-tested.
 */
data class OutboxEntry(
    /** Enqueue order — unique and increasing. Row id (autoincrement) works as this. */
    val seq: Long,
    val mutation: LibraryMutation,
)
