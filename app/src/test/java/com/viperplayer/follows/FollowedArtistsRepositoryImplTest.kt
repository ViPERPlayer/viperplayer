package com.viperplayer.follows

import com.viperplayer.data.sync.push.LibraryMutation
import com.viperplayer.data.sync.push.LibraryPushOutbox
import com.viperplayer.domain.model.MediaId
import com.viperplayer.follows.data.FollowedArtistDao
import com.viperplayer.follows.data.FollowedArtistEntity
import com.viperplayer.follows.data.FollowedArtistsRepositoryImpl
import com.viperplayer.follows.domain.FollowedArtist
import com.viperplayer.follows.domain.FollowedArtistSort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Repository tests over a fake in-memory [FollowedArtistDao] that mimics the unique
 * (pluginId, sourceId) index and INSERT-OR-IGNORE semantics of Room. Covers follow adds, unfollow
 * removes, follow idempotency (no duplicates), isFollowing state, and the repository's sort order.
 */
class FollowedArtistsRepositoryImplTest {

    /** Minimal in-memory DAO honoring the unique (pluginId, sourceId) index and INSERT OR IGNORE. */
    private class FakeFollowedArtistDao : FollowedArtistDao {
        private var nextId = 1L
        private val rows = MutableStateFlow<List<FollowedArtistEntity>>(emptyList())

        override fun observeAll(): Flow<List<FollowedArtistEntity>> =
            rows.map { list -> list.sortedWith(compareByDescending<FollowedArtistEntity> { it.followedAt }.thenByDescending { it.id }) }

        override fun observeIsFollowing(pluginId: String, sourceId: String): Flow<Boolean> =
            rows.map { list -> list.any { it.pluginId == pluginId && it.sourceId == sourceId } }

        override fun observeCount(): Flow<Int> = rows.map { it.size }

        override suspend fun insert(artist: FollowedArtistEntity): Long {
            val exists = rows.value.any { it.pluginId == artist.pluginId && it.sourceId == artist.sourceId }
            if (exists) return -1L // INSERT OR IGNORE: conflict -> ignored
            val id = nextId++
            rows.value = rows.value + artist.copy(id = id)
            return id
        }

        override suspend fun delete(pluginId: String, sourceId: String) {
            rows.value = rows.value.filterNot { it.pluginId == pluginId && it.sourceId == sourceId }
        }
    }

    private fun followed(sourceId: String, name: String, followedAt: Long) = FollowedArtist(
        mediaId = MediaId("plugin", sourceId),
        name = name,
        artworkUrl = null,
        followedAt = followedAt,
    )

    @Test
    fun follow_addsArtist() = runTest {
        val repo = FollowedArtistsRepositoryImpl(FakeFollowedArtistDao(), LibraryPushOutbox.NOOP)

        repo.follow(followed("1", "Artist One", 100))

        val list = repo.followedArtists().first()
        assertEquals(1, list.size)
        assertEquals("Artist One", list.first().name)
    }

    @Test
    fun follow_isIdempotent_noDuplicates() = runTest {
        val repo = FollowedArtistsRepositoryImpl(FakeFollowedArtistDao(), LibraryPushOutbox.NOOP)

        val artist = followed("1", "Artist One", 100)
        repo.follow(artist)
        repo.follow(artist)
        repo.follow(artist.copy(name = "Renamed", followedAt = 999))

        val list = repo.followedArtists().first()
        assertEquals(1, list.size)
        // The original row is kept (INSERT OR IGNORE), not overwritten.
        assertEquals("Artist One", list.first().name)
    }

    @Test
    fun unfollow_removesArtist() = runTest {
        val repo = FollowedArtistsRepositoryImpl(FakeFollowedArtistDao(), LibraryPushOutbox.NOOP)
        val id = MediaId("plugin", "1")
        repo.follow(followed("1", "Artist One", 100))

        repo.unfollow(id)

        assertTrue(repo.followedArtists().first().isEmpty())
    }

    @Test
    fun unfollow_missingArtist_isNoOp() = runTest {
        val repo = FollowedArtistsRepositoryImpl(FakeFollowedArtistDao(), LibraryPushOutbox.NOOP)
        repo.follow(followed("1", "Artist One", 100))

        repo.unfollow(MediaId("plugin", "does-not-exist"))

        assertEquals(1, repo.followedArtists().first().size)
    }

    @Test
    fun isFollowing_reflectsState() = runTest {
        val repo = FollowedArtistsRepositoryImpl(FakeFollowedArtistDao(), LibraryPushOutbox.NOOP)
        val id = MediaId("plugin", "1")

        assertFalse(repo.isFollowing(id).first())

        repo.follow(followed("1", "Artist One", 100))
        assertTrue(repo.isFollowing(id).first())

        repo.unfollow(id)
        assertFalse(repo.isFollowing(id).first())
    }

    @Test
    fun followedArtists_sortedByName() = runTest {
        val repo = FollowedArtistsRepositoryImpl(FakeFollowedArtistDao(), LibraryPushOutbox.NOOP)
        repo.follow(followed("1", "Zed", 100))
        repo.follow(followed("2", "amy", 200))
        repo.follow(followed("3", "Bob", 300))

        val names = repo.followedArtists(FollowedArtistSort.NAME).first().map { it.name }

        assertEquals(listOf("amy", "Bob", "Zed"), names)
    }

    @Test
    fun followedArtists_sortedByRecentlyFollowed() = runTest {
        val repo = FollowedArtistsRepositoryImpl(FakeFollowedArtistDao(), LibraryPushOutbox.NOOP)
        repo.follow(followed("1", "Old", 100))
        repo.follow(followed("2", "Newest", 300))
        repo.follow(followed("3", "Middle", 200))

        val names = repo.followedArtists(FollowedArtistSort.RECENTLY_FOLLOWED).first().map { it.name }

        assertEquals(listOf("Newest", "Middle", "Old"), names)
    }

    @Test
    fun follow_and_unfollow_enqueueOppositePushMutations() = runTest {
        // The two-way-sync wiring must record a SetFollowed(true) on follow and SetFollowed(false) on
        // unfollow so the change can be propagated to the plugin account.
        val outbox = CapturingOutbox()
        val repo = FollowedArtistsRepositoryImpl(FakeFollowedArtistDao(), outbox)
        val id = MediaId("plugin", "1")

        repo.follow(followed("1", "Artist One", 100))
        repo.unfollow(id)

        assertEquals(
            listOf(
                LibraryMutation.SetFollowed("plugin", "1", followed = true),
                LibraryMutation.SetFollowed("plugin", "1", followed = false),
            ),
            outbox.captured,
        )
    }

    /** Records enqueued mutations so the wiring from the repository to the outbox can be asserted. */
    private class CapturingOutbox : LibraryPushOutbox {
        val captured = mutableListOf<LibraryMutation>()
        override val pendingCount = flowOf(0)
        override suspend fun enqueue(mutation: LibraryMutation) {
            captured += mutation
        }
    }
}
