package com.viperplayer.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A durable record of one pending local library mutation to be PUSHED up to a plugin account (the
 * outbox pattern). Rows are drained in [id] order by the push sync manager; the pure
 * [com.viperplayer.data.sync.push.MutationCoalescer] collapses/cancels redundant rows before any
 * plugin call, and [attempts] bounds retries on transient failures.
 *
 * The mutation payload is stored as JSON ([type] + [payloadJson]) rather than a wide column set, so
 * new mutation kinds don't require schema changes. [status] follows PENDING -> (drained) with
 * SKIPPED reserved for entries a plugin reported unsupported (kept for a future plugin update).
 */
@Entity(tableName = "library_push_outbox")
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Target plugin account. Indexed logically by drain queries; matches [type]'s plugin. */
    val pluginId: String,
    /** Discriminator for the mutation kind (see [OutboxType]). */
    val type: String,
    /** JSON-encoded [com.viperplayer.data.sync.push.LibraryMutation] payload. */
    val payloadJson: String,
    /** Enqueue time (ms). For diagnostics/ordering ties only; drain orders by [id]. */
    val createdAt: Long = System.currentTimeMillis(),
    /** Number of transient push attempts so far, for the retry budget. */
    val attempts: Int = 0,
    /** PENDING = to drain; SKIPPED = plugin unsupported, kept for a future plugin update. */
    val status: String = STATUS_PENDING,
) {
    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_SKIPPED = "skipped"
    }
}

/** Stable [OutboxEntity.type] discriminators. */
object OutboxType {
    const val SET_LIKED = "setLiked"
    const val SET_FOLLOWED = "setFollowed"
    const val CREATE_PLAYLIST = "createPlaylist"
    const val RENAME_PLAYLIST = "renamePlaylist"
    const val DELETE_PLAYLIST = "deletePlaylist"
    const val ADD_TRACK = "addTrack"
    const val REMOVE_TRACK = "removeTrack"
}
