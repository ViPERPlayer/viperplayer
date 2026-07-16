package com.viperplayer.data.sync.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure coalescing/ordering core of two-way push sync. No Android, no Room: a
 * sequence of [OutboxEntry]s in, a coalesced/ordered plan out. Covers cancellation (like+unlike,
 * add+remove), collapsing (last-writer-wins), delete-supersedes, create-before-add ordering, dedupe,
 * and the empty case.
 */
class MutationCoalescerTest {

    private var seq = 0L
    private fun entry(m: LibraryMutation) = OutboxEntry(seq = seq++, mutation = m)

    private fun coalesce(vararg mutations: LibraryMutation): CoalesceResult {
        seq = 0
        return MutationCoalescer.coalesce(mutations.map { entry(it) })
    }

    private fun pushed(result: CoalesceResult) = result.toPush.map { it.mutation }

    @Test
    fun empty_isNoOp() {
        val result = MutationCoalescer.coalesce(emptyList())
        assertTrue(result.toPush.isEmpty())
        assertTrue(result.toDrop.isEmpty())
    }

    @Test
    fun likeThenUnlike_sameTrack_collapsesToUnlike() {
        // NOT cancelled to nothing: the track may already be liked remotely (from a prior PULL), so the
        // unlike is a real change. It collapses to a single unlike; the reconciler no-ops it later only
        // if the remote is already unliked.
        val result = coalesce(
            LibraryMutation.SetLiked("testsource", "t1", liked = true),
            LibraryMutation.SetLiked("testsource", "t1", liked = false),
        )
        assertEquals(listOf(LibraryMutation.SetLiked("testsource", "t1", liked = false)), pushed(result))
        // The superseded like (seq 0) is dropped; the surviving unlike is seq 1.
        assertEquals(listOf(0L), result.toDrop)
    }

    @Test
    fun likeUnlikeLike_collapsesToSingleLike() {
        val result = coalesce(
            LibraryMutation.SetLiked("testsource", "t1", liked = true),
            LibraryMutation.SetLiked("testsource", "t1", liked = false),
            LibraryMutation.SetLiked("testsource", "t1", liked = true),
        )
        assertEquals(listOf(LibraryMutation.SetLiked("testsource", "t1", liked = true)), pushed(result))
    }

    @Test
    fun unlikeOnly_isPushed() {
        // No prior like in this batch (the track was liked in an earlier session): the unlike stands.
        val result = coalesce(LibraryMutation.SetLiked("testsource", "t1", liked = false))
        assertEquals(listOf(LibraryMutation.SetLiked("testsource", "t1", liked = false)), pushed(result))
    }

    @Test
    fun followThenUnfollow_collapsesToUnfollow() {
        val result = coalesce(
            LibraryMutation.SetFollowed("testsource", "a1", followed = true),
            LibraryMutation.SetFollowed("testsource", "a1", followed = false),
        )
        assertEquals(listOf(LibraryMutation.SetFollowed("testsource", "a1", followed = false)), pushed(result))
    }

    @Test
    fun sameTrack_differentPlugins_doNotCoalesce() {
        val result = coalesce(
            LibraryMutation.SetLiked("testsource", "t1", liked = true),
            LibraryMutation.SetLiked("fifthsource", "t1", liked = false),
        )
        // Different accounts — both survive.
        assertEquals(2, pushed(result).size)
    }

    @Test
    fun addThenRemove_sameTrack_collapsesToRemove() {
        // The track may already be a member remotely, so the remove is a real change; collapse to it
        // (the reconciler no-ops later if the remote already lacks the track).
        val result = coalesce(
            LibraryMutation.AddTrackToPlaylist("testsource", "p1", "t1"),
            LibraryMutation.RemoveTrackFromPlaylist("testsource", "p1", "t1"),
        )
        assertEquals(listOf(LibraryMutation.RemoveTrackFromPlaylist("testsource", "p1", "t1")), pushed(result))
    }

    @Test
    fun removeThenAdd_sameTrack_netsToAdd() {
        val result = coalesce(
            LibraryMutation.RemoveTrackFromPlaylist("testsource", "p1", "t1"),
            LibraryMutation.AddTrackToPlaylist("testsource", "p1", "t1"),
        )
        assertEquals(listOf(LibraryMutation.AddTrackToPlaylist("testsource", "p1", "t1")), pushed(result))
    }

    @Test
    fun addSameTrackTwice_isDeduped() {
        val result = coalesce(
            LibraryMutation.AddTrackToPlaylist("testsource", "p1", "t1"),
            LibraryMutation.AddTrackToPlaylist("testsource", "p1", "t1"),
        )
        assertEquals(listOf(LibraryMutation.AddTrackToPlaylist("testsource", "p1", "t1")), pushed(result))
        assertEquals(1, result.toDrop.size)
    }

    @Test
    fun multipleRenames_collapseToLast() {
        val result = coalesce(
            LibraryMutation.RenamePlaylist("testsource", "p1", "A"),
            LibraryMutation.RenamePlaylist("testsource", "p1", "B"),
            LibraryMutation.RenamePlaylist("testsource", "p1", "C"),
        )
        assertEquals(listOf(LibraryMutation.RenamePlaylist("testsource", "p1", "C")), pushed(result))
    }

    @Test
    fun createBeforeAddToIt_isOrderedCreateFirst() {
        // Enqueued add-first (adds can be recorded before the create is flushed); the plan must still
        // put the create ahead so the remote playlist exists before it is populated.
        val result = coalesce(
            LibraryMutation.AddTrackToPlaylist("testsource", "p1", "t1"),
            LibraryMutation.CreatePlaylist("testsource", "p1", "My List"),
            LibraryMutation.AddTrackToPlaylist("testsource", "p1", "t2"),
        )
        val plan = pushed(result)
        assertEquals(LibraryMutation.CreatePlaylist("testsource", "p1", "My List"), plan.first())
        assertTrue(plan.contains(LibraryMutation.AddTrackToPlaylist("testsource", "p1", "t1")))
        assertTrue(plan.contains(LibraryMutation.AddTrackToPlaylist("testsource", "p1", "t2")))
    }

    @Test
    fun deletePlaylist_supersedesRenameAndAdds() {
        val result = coalesce(
            LibraryMutation.RenamePlaylist("testsource", "p1", "B"),
            LibraryMutation.AddTrackToPlaylist("testsource", "p1", "t1"),
            LibraryMutation.AddTrackToPlaylist("testsource", "p1", "t2"),
            LibraryMutation.DeletePlaylist("testsource", "p1"),
        )
        // Only the delete remains; you don't rename/populate a playlist you're deleting.
        assertEquals(listOf(LibraryMutation.DeletePlaylist("testsource", "p1")), pushed(result))
    }

    @Test
    fun createThenRename_bothSurvive_createFirst() {
        // A create and a later rename of the same playlist must NOT collapse into one another: the
        // remote playlist has to be created and then renamed. (Regression guard: they share a playlist
        // id, so a naive same-target collapse would drop the create.)
        val result = coalesce(
            LibraryMutation.CreatePlaylist("testsource", "p1", "Working title"),
            LibraryMutation.RenamePlaylist("testsource", "p1", "Final title"),
        )
        assertEquals(
            listOf(
                LibraryMutation.CreatePlaylist("testsource", "p1", "Working title"),
                LibraryMutation.RenamePlaylist("testsource", "p1", "Final title"),
            ),
            pushed(result),
        )
    }

    @Test
    fun createThenDelete_samePlaylist_netsToNothing() {
        val result = coalesce(
            LibraryMutation.CreatePlaylist("testsource", "p1", "Temp"),
            LibraryMutation.AddTrackToPlaylist("testsource", "p1", "t1"),
            LibraryMutation.DeletePlaylist("testsource", "p1"),
        )
        // Nothing exists remotely — the whole lifecycle collapses to a no-op.
        assertTrue(pushed(result).isEmpty())
    }

    @Test
    fun deleteOfOnePlaylist_doesNotAffectAnother() {
        val result = coalesce(
            LibraryMutation.AddTrackToPlaylist("testsource", "p2", "t1"),
            LibraryMutation.DeletePlaylist("testsource", "p1"),
        )
        assertTrue(pushed(result).contains(LibraryMutation.AddTrackToPlaylist("testsource", "p2", "t1")))
        assertTrue(pushed(result).contains(LibraryMutation.DeletePlaylist("testsource", "p1")))
    }

    @Test
    fun independentMutations_keepEnqueueOrder() {
        val result = coalesce(
            LibraryMutation.SetLiked("testsource", "t1", liked = true),
            LibraryMutation.SetFollowed("testsource", "a1", followed = true),
            LibraryMutation.SetLiked("testsource", "t2", liked = true),
        )
        assertEquals(
            listOf(
                LibraryMutation.SetLiked("testsource", "t1", liked = true),
                LibraryMutation.SetFollowed("testsource", "a1", followed = true),
                LibraryMutation.SetLiked("testsource", "t2", liked = true),
            ),
            pushed(result),
        )
    }

    @Test
    fun survivors_carryTheirOutboxSeq() {
        // The plan must reference the seq of the *surviving* (last) entry per target so the drain
        // deletes the right row.
        val result = coalesce(
            LibraryMutation.SetLiked("testsource", "t1", liked = false), // seq 0
            LibraryMutation.SetLiked("testsource", "t1", liked = true),  // seq 1 (survivor)
        )
        assertEquals(1, result.toPush.size)
        assertEquals(1L, result.toPush.first().seq)
        assertEquals(listOf(0L), result.toDrop)
    }
}
