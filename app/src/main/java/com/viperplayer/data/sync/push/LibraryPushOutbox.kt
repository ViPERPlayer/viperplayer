package com.viperplayer.data.sync.push

import com.viperplayer.data.local.dao.OutboxDao
import com.viperplayer.data.preferences.PushSyncPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The write-side entry point mutation sites call to record a local library change for later push to a
 * plugin account. It only persists the intent (into the Room outbox) and triggers a drain; it never
 * blocks the local mutation on a network/plugin call.
 *
 * An interface so the (Hilt-wired, Room-backed) implementation can be swapped for a no-op in unit
 * tests of the mutation-site repositories, which shouldn't need a database to record a like.
 */
interface LibraryPushOutbox {
    /** Live count of queued-but-unpushed changes, for a "N changes to sync" indicator. */
    val pendingCount: Flow<Int>

    /** Record [mutation] for push if its target plugin has push sync enabled (else a no-op). */
    suspend fun enqueue(mutation: LibraryMutation)

    companion object {
        /** A no-op outbox for tests / contexts where push sync is irrelevant. */
        val NOOP: LibraryPushOutbox = object : LibraryPushOutbox {
            override val pendingCount: Flow<Int> = flowOf(0)
            override suspend fun enqueue(mutation: LibraryMutation) = Unit
        }
    }
}

/**
 * Room-backed outbox. Enqueue is gated per plugin by [PushSyncPreferences]: if the user hasn't opted
 * the plugin in, or the mutation targets the embedded `local` library (no remote account), nothing is
 * queued. Deciding *whether* to push and coalescing redundant work is left to [PushSyncManager] on
 * drain — this keeps the enqueue path cheap and the local UI responsive.
 */
@Singleton
class RoomLibraryPushOutbox @Inject constructor(
    private val outboxDao: OutboxDao,
    private val pushSyncPreferences: PushSyncPreferences,
    private val pushSyncManager: PushSyncManager,
) : LibraryPushOutbox {

    override val pendingCount: Flow<Int> = outboxDao.observePendingCount()

    override suspend fun enqueue(mutation: LibraryMutation) {
        val pluginId = mutation.pluginId
        if (pluginId == LOCAL_PLUGIN_ID) return
        if (!pushSyncPreferences.isEnabledSync(pluginId)) return
        try {
            outboxDao.insert(OutboxMapper.toEntity(mutation))
            pushSyncManager.requestDrain(pluginId)
        } catch (e: Exception) {
            Timber.w(e, "Failed to enqueue library push mutation: $mutation")
        }
    }

    private companion object {
        const val LOCAL_PLUGIN_ID = "local"
    }
}
