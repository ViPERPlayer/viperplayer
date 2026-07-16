package com.viperplayer.data.sync.push

import com.viperplayer.data.local.entity.OutboxEntity
import com.viperplayer.plugin.model.PluginErrorCode
import com.viperplayer.plugin.model.PluginException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavior tests for the drain manager over fakes (a fake outbox DAO + a fake plugin write gateway).
 * Exercises the whole drain: gating, unsupported-skip, coalesced-drop, reconciled no-op, retry vs
 * permanent classification, and the create-playlist -> remote-id threading for adds.
 */
class PushSyncManagerTest {

    private val outbox = FakeOutboxDao()
    private val gateway = FakeGateway()
    private var enabled = true
    private val manager = PushSyncManager(outbox, gateway, gate = { enabled })

    private fun enqueue(mutation: LibraryMutation) {
        outbox.rows.value = outbox.rows.value + OutboxMapper.toEntity(mutation).copy(id = outbox.nextId())
    }

    private fun pendingCount() = outbox.rows.value.count { it.status == OutboxEntity.STATUS_PENDING }

    @Test
    fun disabledPlugin_isNotDrained() = runTest {
        enabled = false
        enqueue(LibraryMutation.SetLiked("testsource", "t1", liked = true))
        assertEquals(0, manager.drainPlugin("testsource"))
        assertEquals(1, pendingCount()) // still queued
        assertTrue(gateway.calls.isEmpty())
    }

    @Test
    fun appliedMutation_isDeletedFromOutbox() = runTest {
        enqueue(LibraryMutation.SetLiked("testsource", "t1", liked = true))
        val applied = manager.drainPlugin("testsource")
        assertEquals(1, applied)
        assertEquals(0, pendingCount())
        assertEquals(listOf("setLiked:t1=true"), gateway.calls)
    }

    @Test
    fun unsupportedPlugin_keepsEntriesSkipped_withoutCalling() = runTest {
        gateway.supportsWrite = false
        enqueue(LibraryMutation.SetLiked("testsource", "t1", liked = true))
        assertEquals(0, manager.drainPlugin("testsource"))
        assertTrue(gateway.calls.isEmpty())
        assertEquals(0, pendingCount())
        assertEquals(1, outbox.rows.value.count { it.status == OutboxEntity.STATUS_SKIPPED })
    }

    @Test
    fun unavailablePlugin_keepsEntriesPending_notSkipped() = runTest {
        // A transient disconnect must NOT strand changes: they stay pending for a later retry.
        gateway.capability = WriteCapability.UNAVAILABLE
        enqueue(LibraryMutation.SetLiked("testsource", "t1", liked = true))
        assertEquals(0, manager.drainPlugin("testsource"))
        assertTrue(gateway.calls.isEmpty())
        assertEquals(1, pendingCount()) // still pending
        assertEquals(0, outbox.rows.value.count { it.status == OutboxEntity.STATUS_SKIPPED })
    }

    @Test
    fun previouslySkippedEntries_areRearmedWhenPluginBecomesSupported() = runTest {
        // Enqueue while unsupported -> parked as skipped.
        gateway.capability = WriteCapability.UNSUPPORTED
        enqueue(LibraryMutation.SetLiked("testsource", "t1", liked = true))
        manager.drainPlugin("testsource")
        assertEquals(1, outbox.rows.value.count { it.status == OutboxEntity.STATUS_SKIPPED })

        // Plugin updates to support writes; the skipped entry is re-armed and finally pushed.
        gateway.capability = WriteCapability.SUPPORTED
        gateway.remoteLiked = emptySet()
        val applied = manager.drainPlugin("testsource")
        assertEquals(1, applied)
        assertEquals(listOf("setLiked:t1=true"), gateway.calls)
        assertEquals(0, pendingCount())
    }

    @Test
    fun unlike_beyondTruncatedRemotePage_isStillPushed() = runTest {
        // The remote liked set is a partial snapshot (complete=false) that doesn't contain t1. An
        // unlike must NOT be skipped as "already absent" — the track may be liked beyond page 1.
        gateway.remoteLiked = setOf("other")
        gateway.remoteLikedComplete = false
        enqueue(LibraryMutation.SetLiked("testsource", "t1", liked = false))
        val applied = manager.drainPlugin("testsource")
        assertEquals(1, applied)
        assertEquals(listOf("setLiked:t1=false"), gateway.calls)
    }

    @Test
    fun unlike_absentFromCompleteRemoteSet_isReconciledNoOp() = runTest {
        gateway.remoteLiked = setOf("other")
        gateway.remoteLikedComplete = true
        enqueue(LibraryMutation.SetLiked("testsource", "t1", liked = false))
        val applied = manager.drainPlugin("testsource")
        assertEquals(1, applied) // reconciled-applied, no plugin call
        assertTrue(gateway.calls.none { it.startsWith("setLiked") })
    }

    @Test
    fun likeThenUnlike_collapsesToUnlike_reconciledNoOpWhenRemoteAbsent() = runTest {
        // like+unlike collapses to a single unlike. The remote track is not liked (default empty set),
        // so the reconciler no-ops it as already-satisfied — no plugin call, both rows cleared.
        gateway.remoteLiked = emptySet()
        enqueue(LibraryMutation.SetLiked("testsource", "t1", liked = true))
        enqueue(LibraryMutation.SetLiked("testsource", "t1", liked = false))
        assertEquals(1, manager.drainPlugin("testsource")) // reconciled-applied
        assertTrue(gateway.calls.none { it.startsWith("setLiked") })
        assertEquals(0, pendingCount())
    }

    @Test
    fun likeThenUnlike_whenAlreadyLikedRemotely_pushesTheUnlike() = runTest {
        // The track IS liked remotely (from a prior PULL). The collapsed unlike is a REAL change and
        // must be pushed — the old "cancel to nothing" behavior would have silently dropped it.
        gateway.remoteLiked = setOf("t1")
        enqueue(LibraryMutation.SetLiked("testsource", "t1", liked = true))
        enqueue(LibraryMutation.SetLiked("testsource", "t1", liked = false))
        assertEquals(1, manager.drainPlugin("testsource"))
        assertEquals(listOf("setLiked:t1=false"), gateway.calls)
        assertEquals(0, pendingCount())
    }

    @Test
    fun alreadyLikedRemotely_isReconciledNoOp() = runTest {
        gateway.remoteLiked = setOf("t1")
        enqueue(LibraryMutation.SetLiked("testsource", "t1", liked = true))
        val applied = manager.drainPlugin("testsource")
        // Counts as applied (converged) but never calls the plugin's write.
        assertEquals(1, applied)
        assertTrue(gateway.calls.none { it.startsWith("setLiked") })
        assertEquals(0, pendingCount())
    }

    @Test
    fun retryableFailure_keepsEntry_andBumpsAttempts() = runTest {
        gateway.failWith = PluginException(PluginErrorCode.NETWORK, "offline")
        enqueue(LibraryMutation.SetLiked("testsource", "t1", liked = true))
        assertEquals(0, manager.drainPlugin("testsource"))
        assertEquals(1, pendingCount()) // kept for retry
        assertEquals(1, outbox.rows.value.first().attempts)
    }

    @Test
    fun permanentFailure_dropsEntry() = runTest {
        gateway.failWith = PluginException(PluginErrorCode.NOT_FOUND, "gone")
        enqueue(LibraryMutation.SetLiked("testsource", "t1", liked = true))
        assertEquals(0, manager.drainPlugin("testsource"))
        assertEquals(0, pendingCount()) // dropped, no infinite retry
    }

    @Test
    fun createThenAdd_threadsRemoteIdIntoTheAdd() = runTest {
        gateway.createdPlaylistId = "remote-42"
        enqueue(LibraryMutation.CreatePlaylist("testsource", "local-1", "My List"))
        enqueue(LibraryMutation.AddTrackToPlaylist("testsource", "local-1", "t1"))

        manager.drainPlugin("testsource")

        // The add must target the account-assigned remote id, not the local id.
        assertEquals(
            listOf("createPlaylist:My List", "addTrack:remote-42/t1"),
            gateway.calls,
        )
    }

    @Test
    fun emptyOutbox_isNoOp() = runTest {
        assertEquals(0, manager.drainPlugin("testsource"))
        assertTrue(gateway.calls.isEmpty())
    }

    // ---- Fakes ----

    private class FakeOutboxDao : com.viperplayer.data.local.dao.OutboxDao {
        val rows = MutableStateFlow<List<OutboxEntity>>(emptyList())
        private var seq = 0L
        fun nextId() = ++seq

        override suspend fun insert(entry: OutboxEntity): Long {
            val id = nextId()
            rows.value = rows.value + entry.copy(id = id)
            return id
        }

        override suspend fun pendingFor(pluginId: String): List<OutboxEntity> =
            rows.value.filter { it.pluginId == pluginId && it.status == OutboxEntity.STATUS_PENDING }.sortedBy { it.id }

        override suspend fun allPending(): List<OutboxEntity> =
            rows.value.filter { it.status == OutboxEntity.STATUS_PENDING }.sortedBy { it.id }

        override suspend fun pluginsWithPending(): List<String> =
            rows.value.filter { it.status == OutboxEntity.STATUS_PENDING }.map { it.pluginId }.distinct()

        override fun observePendingCount(): Flow<Int> =
            rows.map { list -> list.count { it.status == OutboxEntity.STATUS_PENDING } }

        override suspend fun deleteById(id: Long) { rows.value = rows.value.filterNot { it.id == id } }
        override suspend fun deleteByIds(ids: List<Long>) { rows.value = rows.value.filterNot { it.id in ids } }
        override suspend fun updateAttempts(id: Long, attempts: Int) {
            rows.value = rows.value.map { if (it.id == id) it.copy(attempts = attempts) else it }
        }
        override suspend fun updateStatus(id: Long, status: String) {
            rows.value = rows.value.map { if (it.id == id) it.copy(status = status) else it }
        }
        override suspend fun rearmSkipped(pluginId: String) {
            rows.value = rows.value.map {
                if (it.pluginId == pluginId && it.status == OutboxEntity.STATUS_SKIPPED)
                    it.copy(status = OutboxEntity.STATUS_PENDING, attempts = 0) else it
            }
        }
        override suspend fun deleteForPlugin(pluginId: String) {
            rows.value = rows.value.filterNot { it.pluginId == pluginId }
        }
    }

    private class FakeGateway : PluginWriteGateway {
        var capability = WriteCapability.SUPPORTED
        var supportsWrite: Boolean
            get() = capability == WriteCapability.SUPPORTED
            set(value) { capability = if (value) WriteCapability.SUPPORTED else WriteCapability.UNSUPPORTED }
        var failWith: Throwable? = null
        var remoteLiked: Set<String>? = emptySet()
        var remoteLikedComplete = true
        var remoteFollowed: Set<String>? = emptySet()
        var createdPlaylistId = "remote-created"
        val calls = mutableListOf<String>()

        private fun <T> maybeFail(value: T): Result<T> =
            failWith?.let { Result.failure(it) } ?: Result.success(value)

        override suspend fun libraryWriteCapability(pluginId: String) = capability
        override suspend fun setLiked(pluginId: String, trackId: String, liked: Boolean): Result<Unit> {
            calls += "setLiked:$trackId=$liked"; return maybeFail(Unit)
        }
        override suspend fun setFollowed(pluginId: String, artistId: String, followed: Boolean): Result<Unit> {
            calls += "setFollowed:$artistId=$followed"; return maybeFail(Unit)
        }
        override suspend fun createPlaylist(pluginId: String, name: String): Result<String> {
            calls += "createPlaylist:$name"; return maybeFail(createdPlaylistId)
        }
        override suspend fun renamePlaylist(pluginId: String, playlistId: String, name: String): Result<Unit> {
            calls += "rename:$playlistId=$name"; return maybeFail(Unit)
        }
        override suspend fun deletePlaylist(pluginId: String, playlistId: String): Result<Unit> {
            calls += "delete:$playlistId"; return maybeFail(Unit)
        }
        override suspend fun addTrackToPlaylist(pluginId: String, playlistId: String, trackId: String): Result<Unit> {
            calls += "addTrack:$playlistId/$trackId"; return maybeFail(Unit)
        }
        override suspend fun removeTrackFromPlaylist(pluginId: String, playlistId: String, trackId: String): Result<Unit> {
            calls += "removeTrack:$playlistId/$trackId"; return maybeFail(Unit)
        }
        override suspend fun likedTrackIds(pluginId: String, limit: Int) =
            remoteLiked?.let { RemoteSet(it, remoteLikedComplete) }
        override suspend fun followedArtistIds(pluginId: String, limit: Int) =
            remoteFollowed?.let { RemoteSet(it, complete = true) }
    }
}
