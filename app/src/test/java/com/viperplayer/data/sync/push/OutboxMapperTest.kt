package com.viperplayer.data.sync.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for the JSON (de)serialization bridging [LibraryMutation] and its durable outbox row.
 * Every mutation kind must survive a round-trip, and a corrupt/unknown row must decode to null
 * (so it can be dropped without wedging the queue) rather than throw.
 */
class OutboxMapperTest {

    private fun roundTrip(mutation: LibraryMutation) {
        val entity = OutboxMapper.toEntity(mutation)
        assertEquals(mutation, OutboxMapper.toMutation(entity))
    }

    @Test fun setLiked_roundTrips() = roundTrip(LibraryMutation.SetLiked("testsource", "t1", liked = true))
    @Test fun setUnliked_roundTrips() = roundTrip(LibraryMutation.SetLiked("testsource", "t1", liked = false))
    @Test fun setFollowed_roundTrips() = roundTrip(LibraryMutation.SetFollowed("testsource", "a1", followed = true))
    @Test fun createPlaylist_roundTrips() = roundTrip(LibraryMutation.CreatePlaylist("testsource", "p1", "My List"))
    @Test fun renamePlaylist_roundTrips() = roundTrip(LibraryMutation.RenamePlaylist("testsource", "p1", "Renamed"))
    @Test fun deletePlaylist_roundTrips() = roundTrip(LibraryMutation.DeletePlaylist("testsource", "p1"))
    @Test fun addTrack_roundTrips() = roundTrip(LibraryMutation.AddTrackToPlaylist("testsource", "p1", "t1"))
    @Test fun removeTrack_roundTrips() = roundTrip(LibraryMutation.RemoveTrackFromPlaylist("testsource", "p1", "t1"))

    @Test
    fun pluginId_isPreservedOnEntity() {
        val entity = OutboxMapper.toEntity(LibraryMutation.SetLiked("fifthsource", "t9", liked = true))
        assertEquals("fifthsource", entity.pluginId)
    }

    @Test
    fun corruptPayload_decodesToNull() {
        val bad = OutboxMapper.toEntity(LibraryMutation.SetLiked("testsource", "t1", liked = true))
            .copy(payloadJson = "{ not json")
        assertNull(OutboxMapper.toMutation(bad))
    }

    @Test
    fun unknownType_decodesToNull() {
        val unknown = OutboxMapper.toEntity(LibraryMutation.SetLiked("testsource", "t1", liked = true))
            .copy(type = "totallyUnknownType")
        assertNull(OutboxMapper.toMutation(unknown))
    }

    @Test
    fun toEntry_usesRowIdAsSeq() {
        val entity = OutboxMapper.toEntity(LibraryMutation.SetLiked("testsource", "t1", liked = true)).copy(id = 42)
        val entry = OutboxMapper.toEntry(entity)
        assertEquals(42L, entry?.seq)
        assertEquals(LibraryMutation.SetLiked("testsource", "t1", liked = true), entry?.mutation)
    }
}
