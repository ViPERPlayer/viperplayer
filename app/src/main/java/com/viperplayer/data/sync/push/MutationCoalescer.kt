package com.viperplayer.data.sync.push

/**
 * The result of coalescing a pending-mutation sequence:
 *  - [toPush]  — the ordered, deduped, collapsed set of mutations still worth pushing, each tagged
 *                with the outbox [seq] of the entry that represents it (so the drain can mark it done).
 *  - [toDrop]  — the [seq]s of entries that coalescing eliminated (a like+unlike that cancelled, an
 *                add+remove that cancelled, a rename superseded by a later rename, everything
 *                superseded by a delete). The drain deletes these without ever contacting the plugin.
 */
data class CoalesceResult(
    val toPush: List<PlannedMutation>,
    val toDrop: List<Long>,
)

/** A mutation the drain should push, carrying the outbox [seq] it should mark done/failed. */
data class PlannedMutation(val seq: Long, val mutation: LibraryMutation)

/**
 * The pure, deterministic core of two-way push sync. Given the pending outbox entries (in enqueue
 * order) it decides which mutations to actually push and in what order, folding away redundant work.
 *
 * Coalescing rules (per [LibraryMutation.Target], the identity of the remote object):
 *  - **Toggles collapse to their last state**: like, unlike, like → a single like — only the last
 *    intended state matters. A like then unlike collapses to a single unlike (NOT to "nothing"): the
 *    track may already be liked remotely from a prior PULL, so the unlike is a real change. Whether
 *    that last state is already satisfied remotely is decided later by [PushReconciler], not here —
 *    the coalescer never assumes an unknown starting state.
 *  - **Add/remove track collapse to the last op** for the same playlist+track, same reasoning.
 *  - **Renames collapse**: multiple renames of a playlist → a single rename to the last name.
 *  - **Delete supersedes**: a playlist delete drops every earlier pending mutation for that playlist
 *    *and* its member-track adds/removes — you don't rename/populate a playlist you're deleting.
 *  - **Create is kept and ordered first**: a create-playlist for a local playlist is emitted before
 *    any add-track that references it, so the remote playlist exists before we populate it. A create
 *    followed later by a delete of the same playlist nets to nothing (never created remotely).
 *
 * Ordering of the survivors: create-playlist first, then everything else in original enqueue order —
 * a stable order that keeps FIFO semantics while guaranteeing the create-before-add dependency.
 */
object MutationCoalescer {

    fun coalesce(entries: List<OutboxEntry>): CoalesceResult {
        if (entries.isEmpty()) return CoalesceResult(emptyList(), emptyList())

        val sorted = entries.sortedBy { it.seq }
        val allSeqs = sorted.map { it.seq }

        // Playlists that are being deleted — any earlier work on them (rename, add/remove of tracks)
        // is pointless. A create followed later by a delete also nets to nothing.
        //
        // NOT-YET-WIRED (future work): no enqueue site produces CreatePlaylist/RenamePlaylist/
        // DeletePlaylist today (remote-playlist create/rename/delete has no UI, and local playlists are
        // dropped by the outbox). This branch is fully modeled + tested for when those mutation sites
        // are added.
        //
        // ID-SPACE CAVEAT (read before wiring remote-playlist creation): DeletePlaylist keys on
        // `playlistId` (the plugin's REMOTE id) while CreatePlaylist keys on `localPlaylistId` (the
        // app's LOCAL id). These live in different id spaces, so the create+delete "nets to nothing"
        // cancellation below only fires when those two ids happen to be equal. Whoever wires
        // remote-playlist creation must ensure a create and its later delete carry ids in the SAME id
        // space (e.g. key both on the local id until the remote id is known, or correlate them) or the
        // pair will NOT cancel and an orphan create/delete round-trip will be pushed. Correcting this
        // "by construction" needs a model change (a delete that also carries the local id), so it is
        // deliberately left to the wiring change rather than done speculatively here.
        val deletedPlaylistKeys = sorted
            .mapNotNull { (it.mutation as? LibraryMutation.DeletePlaylist)?.let { d -> playlistKey(d.pluginId, d.playlistId) } }
            .toSet()
        val createdThenDeleted = deletedPlaylistKeys.filter { key ->
            sorted.any { e -> (e.mutation as? LibraryMutation.CreatePlaylist)?.let { playlistKey(it.pluginId, it.localPlaylistId) } == key }
        }.toSet()

        // Collapse per target: keep only the LAST entry per target (last-writer-wins for toggles and
        // renames). This is always the user's final intended state; it is never "cancelled to nothing"
        // because we don't know the remote starting state here — the reconciler no-ops it at push time
        // if the remote already matches.
        val lastPerTarget = LinkedHashMap<LibraryMutation.Target, OutboxEntry>()
        for (entry in sorted) {
            lastPerTarget[entry.mutation.target] = entry
        }

        val kept = mutableListOf<OutboxEntry>()
        for ((_, entry) in lastPerTarget) {
            val mutation = entry.mutation

            // Drop anything targeting a playlist that is being deleted (unless it IS the delete).
            val ownerKey = playlistOwnerKey(mutation)
            if (ownerKey != null && ownerKey in deletedPlaylistKeys && mutation !is LibraryMutation.DeletePlaylist) {
                continue
            }
            // A create+delete pair nets to nothing: drop the delete too (nothing exists remotely).
            if (mutation is LibraryMutation.DeletePlaylist && playlistKey(mutation.pluginId, mutation.playlistId) in createdThenDeleted) {
                continue
            }

            kept += entry
        }

        // Survivors keep their outbox seq; the rest are dropped.
        val keptSeqs = kept.map { it.seq }.toSet()
        val toDrop = allSeqs.filter { it !in keptSeqs }

        // Order: create-playlist first (so the playlist exists before adds reference it), then the
        // rest in original enqueue order.
        val ordered = kept.sortedWith(
            compareByDescending<OutboxEntry> { it.mutation is LibraryMutation.CreatePlaylist }
                .thenBy { it.seq }
        )
        val toPush = ordered.map { PlannedMutation(it.seq, it.mutation) }

        return CoalesceResult(toPush = toPush, toDrop = toDrop)
    }

    /** The playlist a mutation is scoped to (for delete-supersedes), or null if not playlist-scoped. */
    private fun playlistOwnerKey(mutation: LibraryMutation): String? = when (mutation) {
        is LibraryMutation.CreatePlaylist -> playlistKey(mutation.pluginId, mutation.localPlaylistId)
        is LibraryMutation.RenamePlaylist -> playlistKey(mutation.pluginId, mutation.playlistId)
        is LibraryMutation.DeletePlaylist -> playlistKey(mutation.pluginId, mutation.playlistId)
        is LibraryMutation.AddTrackToPlaylist -> playlistKey(mutation.pluginId, mutation.playlistId)
        is LibraryMutation.RemoveTrackFromPlaylist -> playlistKey(mutation.pluginId, mutation.playlistId)
        else -> null
    }

    private fun playlistKey(pluginId: String, playlistId: String) = "$pluginId|$playlistId"
}
