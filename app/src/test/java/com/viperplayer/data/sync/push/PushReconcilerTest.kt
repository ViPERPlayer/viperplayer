package com.viperplayer.data.sync.push

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure conflict-handling core: given a remote state and a mutation, decide whether
 * the push is needed or already-satisfied (a no-op). Unknown remote state must always push.
 */
class PushReconcilerTest {

    @Test
    fun likeAlreadyLikedRemotely_isNoOp() {
        // Presence is authoritative even if the snapshot is partial.
        val state = RemoteState(likedTrackIds = setOf("t1"), likedComplete = false)
        assertFalse(PushReconciler.isNeeded(LibraryMutation.SetLiked("p", "t1", liked = true), state))
    }

    @Test
    fun likeNotYetLikedRemotely_isNeeded() {
        val state = RemoteState(likedTrackIds = setOf("t2"), likedComplete = true)
        assertTrue(PushReconciler.isNeeded(LibraryMutation.SetLiked("p", "t1", liked = true), state))
    }

    @Test
    fun unlikeOfAlreadyAbsentTrack_fromCompleteSet_isNoOp() {
        val state = RemoteState(likedTrackIds = emptySet(), likedComplete = true)
        assertFalse(PushReconciler.isNeeded(LibraryMutation.SetLiked("p", "t1", liked = false), state))
    }

    @Test
    fun unlikeOfAbsentTrack_fromPARTIALSet_isNeeded() {
        // Absent from a truncated page proves nothing — the unlike must still be pushed.
        val state = RemoteState(likedTrackIds = emptySet(), likedComplete = false)
        assertTrue(PushReconciler.isNeeded(LibraryMutation.SetLiked("p", "t1", liked = false), state))
    }

    @Test
    fun unlikeOfPresentTrack_isNeeded() {
        val state = RemoteState(likedTrackIds = setOf("t1"), likedComplete = true)
        assertTrue(PushReconciler.isNeeded(LibraryMutation.SetLiked("p", "t1", liked = false), state))
    }

    @Test
    fun unknownLikedSet_alwaysPushes() {
        val state = RemoteState(likedTrackIds = null)
        assertTrue(PushReconciler.isNeeded(LibraryMutation.SetLiked("p", "t1", liked = true), state))
        assertTrue(PushReconciler.isNeeded(LibraryMutation.SetLiked("p", "t1", liked = false), state))
    }

    @Test
    fun followAlreadyFollowed_isNoOp() {
        val state = RemoteState(followedArtistIds = setOf("a1"), followedComplete = false)
        assertFalse(PushReconciler.isNeeded(LibraryMutation.SetFollowed("p", "a1", followed = true), state))
    }

    @Test
    fun unfollowOfNotFollowed_fromCompleteSet_isNoOp() {
        val state = RemoteState(followedArtistIds = emptySet(), followedComplete = true)
        assertFalse(PushReconciler.isNeeded(LibraryMutation.SetFollowed("p", "a1", followed = false), state))
    }

    @Test
    fun addTrackAlreadyMember_isNoOp() {
        val state = RemoteState(playlistTrackIds = mapOf("p1" to setOf("t1")), playlistTracksComplete = false)
        assertFalse(PushReconciler.isNeeded(LibraryMutation.AddTrackToPlaylist("p", "p1", "t1"), state))
    }

    @Test
    fun addTrackNotMember_isNeeded() {
        val state = RemoteState(playlistTrackIds = mapOf("p1" to setOf("t2")), playlistTracksComplete = true)
        assertTrue(PushReconciler.isNeeded(LibraryMutation.AddTrackToPlaylist("p", "p1", "t1"), state))
    }

    @Test
    fun removeTrackNotMember_fromCompleteSet_isNoOp() {
        val state = RemoteState(playlistTrackIds = mapOf("p1" to emptySet()), playlistTracksComplete = true)
        assertFalse(PushReconciler.isNeeded(LibraryMutation.RemoveTrackFromPlaylist("p", "p1", "t1"), state))
    }

    @Test
    fun createRenameDelete_alwaysAttempted() {
        val state = RemoteState()
        assertTrue(PushReconciler.isNeeded(LibraryMutation.CreatePlaylist("p", "p1", "n"), state))
        assertTrue(PushReconciler.isNeeded(LibraryMutation.RenamePlaylist("p", "p1", "n"), state))
        assertTrue(PushReconciler.isNeeded(LibraryMutation.DeletePlaylist("p", "p1"), state))
    }
}
