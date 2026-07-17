package com.viperplayer.data.librarysync

import com.viperplayer.data.account.AccountApiResult
import com.viperplayer.domain.librarysync.LibrarySyncResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the transport → domain mapping ([AccountApiResult.mapToDomain]) and the DTO ↔ domain
 * conversions ([LibraryMappers]). These lock down that every transport outcome has a domain
 * counterpart and that the wire field names survive the round trip unchanged.
 */
class LibrarySyncMappingTest {

    @Test
    fun success_isTransformed() {
        val result = AccountApiResult.Success(RevisionResponseDto(revision = 11)).mapToDomain { it.revision }
        assertEquals(LibrarySyncResult.Success(11L), result)
    }

    @Test
    fun rejected_carriesMessage() {
        val result = AccountApiResult.Rejected("bad payload").mapToDomain<Unit, Unit> { }
        assertEquals(LibrarySyncResult.Rejected("bad payload"), result)
    }

    @Test
    fun unauthenticated_networkError_notConfigured_mapAcross() {
        assertEquals(
            LibrarySyncResult.Unauthenticated,
            AccountApiResult.Unauthenticated.mapToDomain<Unit, Unit> { },
        )
        assertEquals(
            LibrarySyncResult.NetworkError,
            AccountApiResult.NetworkError.mapToDomain<Unit, Unit> { },
        )
        assertEquals(
            LibrarySyncResult.NotConfigured,
            AccountApiResult.NotConfigured.mapToDomain<Unit, Unit> { },
        )
    }

    @Test
    fun trackRef_roundTrips_allFields() {
        val dto = TrackRefDto(
            pluginId = "testsource",
            sourceId = "t1",
            title = "Title",
            artist = "Artist",
            album = "Album",
            artworkUrl = "http://art",
            durationMs = 1234,
        )
        val back = dto.toDomain().toDto()
        assertEquals(dto, back)
    }

    @Test
    fun playlist_roundTrips_withTracks() {
        val dto = PlaylistDto(
            id = "p1",
            name = "Mix",
            tracks = listOf(TrackRefDto("testsource", "t1"), TrackRefDto("local", "s2")),
            revision = 5,
            updatedAtMs = 99,
        )
        val back = dto.toDomain().toDto()
        assertEquals(dto, back)
    }

    @Test
    fun snapshot_mapsAllCollectionsAndRevision() {
        val dto = LibrarySnapshotDto(
            playlists = listOf(PlaylistDto(id = "p1", name = "n")),
            likedTracks = listOf(TrackRefDto("local", "s9")),
            revision = 7,
        )
        val domain = dto.toDomain()
        assertEquals(7L, domain.revision)
        assertEquals("p1", domain.playlists.single().id)
        assertEquals("s9", domain.likedTracks.single().sourceId)
    }

    @Test
    fun malformedResult_stillMaps_whenTransformIgnoresValue() {
        // A rejection never invokes transform — guards against a mapper that unconditionally touches value.
        val result = AccountApiResult.Rejected("x").mapToDomain<String, Int> { it.length }
        assertTrue(result is LibrarySyncResult.Rejected)
    }
}
