package com.viperplayer.data.sync.push

/**
 * A snapshot of the parts of a plugin account's remote library that a push might redundantly repeat.
 * The drain fills this in (from the plugin, best-effort) before deciding whether a mutation is
 * actually needed. Anything unknown is left null/absent, which the reconciler treats as "don't know →
 * push anyway" rather than risk skipping a needed change.
 *
 * [likedComplete] / [followedComplete] say whether the corresponding set is the *entire* remote set or
 * just a first page. This matters for the "remove" direction: an id being ABSENT from a truncated
 * snapshot does not prove it isn't liked/followed remotely, so an unlike/unfollow must NOT be skipped
 * off a partial set. A partial set can still safely skip a redundant ADD (present ⇒ already there).
 */
data class RemoteState(
    val likedTrackIds: Set<String>? = null,
    val likedComplete: Boolean = false,
    val followedArtistIds: Set<String>? = null,
    val followedComplete: Boolean = false,
    /** Remote (playlistId -> trackIds) membership, or null if unknown. */
    val playlistTrackIds: Map<String, Set<String>>? = null,
    val playlistTracksComplete: Boolean = false,
)

/**
 * Pure conflict handling: given the [RemoteState] and a [LibraryMutation], decide whether pushing it
 * would actually change anything. When the remote is already in the desired state the push is a no-op
 * — the drain records it as [PushOutcome.APPLIED] without calling the plugin. This is what keeps the
 * two libraries convergent even if a change was already reflected remotely (e.g. by an earlier PULL).
 *
 * Conservative by design:
 *  - Unknown state (null) → push.
 *  - A "set" mutation to PRESENT (like/follow/add whose target is already in the remote set) → skip,
 *    even off a partial set, because presence is authoritative.
 *  - A "clear" mutation (unlike/unfollow/remove) may only be skipped when the set is known COMPLETE;
 *    off a partial snapshot an absent id is inconclusive, so we push to be safe.
 */
object PushReconciler {

    /** True if [mutation] would change the remote from [state]; false if it's already satisfied. */
    fun isNeeded(mutation: LibraryMutation, state: RemoteState): Boolean = when (mutation) {
        is LibraryMutation.SetLiked -> {
            val liked = state.likedTrackIds ?: return true
            membershipNeeded(mutation.trackId in liked, mutation.liked, state.likedComplete)
        }
        is LibraryMutation.SetFollowed -> {
            val followed = state.followedArtistIds ?: return true
            membershipNeeded(mutation.artistId in followed, mutation.followed, state.followedComplete)
        }
        is LibraryMutation.AddTrackToPlaylist -> {
            val members = state.playlistTrackIds?.get(mutation.playlistId) ?: return true
            membershipNeeded(mutation.trackId in members, wantPresent = true, complete = state.playlistTracksComplete)
        }
        is LibraryMutation.RemoveTrackFromPlaylist -> {
            val members = state.playlistTrackIds?.get(mutation.playlistId) ?: return true
            membershipNeeded(mutation.trackId in members, wantPresent = false, complete = state.playlistTracksComplete)
        }
        // Create/rename/delete have no cheap idempotency check here; always attempt (the plugin's own
        // write is expected to be idempotent or to no-op if already in the target state).
        is LibraryMutation.CreatePlaylist,
        is LibraryMutation.RenamePlaylist,
        is LibraryMutation.DeletePlaylist -> true
    }

    /**
     * Whether a membership change is needed given the current [isPresent] state, the [wantPresent]
     * target, and whether the observed set is [complete].
     *  - want present & already present → not needed (skip; presence is authoritative even if partial).
     *  - want present & absent → needed.
     *  - want absent & present → needed (remove it).
     *  - want absent & absent → only skippable if the set is COMPLETE; off a partial set, push to be safe.
     */
    private fun membershipNeeded(isPresent: Boolean, wantPresent: Boolean, complete: Boolean): Boolean =
        if (wantPresent) !isPresent
        else isPresent || !complete
}
