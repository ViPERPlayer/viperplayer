package com.viperplayer.data.social

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The no-backend stub repositories must emit empty/zero and no-op their mutations, so the (backend-
 * gated) social UI renders nothing until a real implementation lands.
 */
class StubSocialRepositoriesTest {

    @Test
    fun friends_emitsEmpty() = runTest {
        val repo = StubFriendsRepository()
        assertTrue(repo.friends.first().isEmpty())
        assertTrue(repo.liveJams.first().isEmpty())
    }

    @Test
    fun activity_emitsEmpty() = runTest {
        assertTrue(StubFriendActivityRepository().activity.first().isEmpty())
    }

    @Test
    fun sharedPlaylists_emitsEmptyInbox_zeroUnread_andNoOps() = runTest {
        val repo = StubSharedPlaylistsRepository()
        assertTrue(repo.inbox.first().isEmpty())
        assertEquals(0, repo.unreadCount.first())
        // Mutations are no-ops (must not throw).
        repo.save("any")
        repo.dismiss("any")
        assertTrue(repo.inbox.first().isEmpty())
    }
}
