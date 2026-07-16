package com.viperplayer.data.sync.push

import com.viperplayer.data.local.dao.OutboxDao
import com.viperplayer.data.local.entity.OutboxEntity
import com.viperplayer.plugin.model.PluginErrorCode
import com.viperplayer.plugin.model.PluginException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** The push-enabled gate for a plugin — abstracted so the drain is unit-testable without DataStore. */
fun interface PushSyncGate {
    suspend fun isEnabled(pluginId: String): Boolean
}

/**
 * Drains the library-push outbox: takes the pending entries for a plugin, runs them through the pure
 * [MutationCoalescer] (cancel/collapse/order), reconciles each survivor against the plugin's remote
 * state ([PushReconciler] — already-satisfied changes become a no-op), pushes the rest via
 * [PluginDataSource]'s write ops, and applies [PushPolicy] to each result (done → delete, unsupported
 * → skip, transient → retry, permanent → drop).
 *
 * Concurrency: a per-plugin mutex serializes drains so two triggers can't push the same entry twice;
 * a create-playlist's returned remote id is threaded to that playlist's add/remove within the same
 * drain via [remotePlaylistIds].
 *
 * This class owns *persistence + plugin calls*; the decisions (coalesce/reconcile/policy) live in the
 * pure package so they stay unit-testable without Android.
 */
@Singleton
class PushSyncManager @Inject constructor(
    private val outboxDao: OutboxDao,
    private val gateway: PluginWriteGateway,
    private val gate: PushSyncGate,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val drainLocks = mutableMapOf<String, Mutex>()
    private val locksGuard = Mutex()

    /** Fire-and-forget drain request for one plugin (called after an enqueue). */
    fun requestDrain(pluginId: String) {
        scope.launch { drainPlugin(pluginId) }
    }

    /** Drain every plugin that currently has pending entries (e.g. on connect, or after a PULL sync). */
    suspend fun drainAll() {
        val plugins = runCatching { outboxDao.pluginsWithPending() }.getOrDefault(emptyList())
        for (pluginId in plugins) drainPlugin(pluginId)
    }

    /**
     * Discard every queued (pending or skipped) change for [pluginId] — called when the user turns off
     * push sync for it, so a later re-enable doesn't suddenly flush a backlog of stale intents.
     */
    suspend fun clearPending(pluginId: String) {
        runCatching { outboxDao.deleteForPlugin(pluginId) }
            .onFailure { Timber.w(it, "Failed to clear push outbox for $pluginId") }
    }

    /**
     * Drain a single plugin's outbox. No-op when push is disabled for it, the plugin isn't connected,
     * or it has no pending entries. Serialized per plugin. Returns the number of entries that were
     * actually pushed (applied) — useful for tests and diagnostics.
     */
    suspend fun drainPlugin(pluginId: String): Int = mutexFor(pluginId).withLock {
        if (!gate.isEnabled(pluginId)) return@withLock 0

        when (gateway.libraryWriteCapability(pluginId)) {
            // Not reachable yet: leave everything pending and retry on the next connect/enqueue. Never
            // marks entries skipped, so a transient disconnect can't permanently strand queued changes.
            WriteCapability.UNAVAILABLE -> return@withLock 0
            // Connected but genuinely can't write: park pending entries as skipped so a future plugin
            // update can apply them, and don't burn retries hitting UNSUPPORTED on each one.
            WriteCapability.UNSUPPORTED -> {
                val pending = runCatching { outboxDao.pendingFor(pluginId) }.getOrDefault(emptyList())
                pending.forEach { runCatching { outboxDao.updateStatus(it.id, OutboxEntity.STATUS_SKIPPED) } }
                if (pending.isNotEmpty()) {
                    Timber.d("Push sync: $pluginId lacks library write; ${pending.size} entries skipped")
                }
                return@withLock 0
            }
            WriteCapability.SUPPORTED -> Unit // fall through and drain
        }

        // The plugin can write now — re-arm anything previously parked as skipped (e.g. it was
        // write-incapable before and has since updated) so those changes finally get pushed.
        runCatching { outboxDao.rearmSkipped(pluginId) }

        val pending = runCatching { outboxDao.pendingFor(pluginId) }.getOrElse {
            Timber.w(it, "Failed to read outbox for $pluginId"); return@withLock 0
        }
        if (pending.isEmpty()) return@withLock 0

        val entries = pending.mapNotNull { row -> OutboxMapper.toEntry(row)?.let { row.id to it } }
        // Rows that failed to decode are corrupt — drop them so they can't wedge the queue.
        val corrupt = pending.map { it.id } - entries.map { it.first }.toSet()
        if (corrupt.isNotEmpty()) runCatching { outboxDao.deleteByIds(corrupt) }

        val plan = MutationCoalescer.coalesce(entries.map { it.second })
        // Entries coalescing eliminated (cancelled/superseded) never touch the plugin.
        if (plan.toDrop.isNotEmpty()) runCatching { outboxDao.deleteByIds(plan.toDrop) }

        val attemptsBySeq = pending.associate { it.id to it.attempts }
        val remoteState = fetchRemoteState(pluginId)
        val remotePlaylistIds = mutableMapOf<String, String>()

        var applied = 0
        for (planned in plan.toPush) {
            val outcome = push(pluginId, planned.mutation, remoteState, remotePlaylistIds)
            when (PushPolicy.decide(outcome, attemptsBySeq[planned.seq] ?: 0)) {
                OutboxAction.DELETE -> {
                    runCatching { outboxDao.deleteById(planned.seq) }
                    if (outcome == PushOutcome.APPLIED) applied++
                }
                OutboxAction.RETRY ->
                    runCatching { outboxDao.updateAttempts(planned.seq, (attemptsBySeq[planned.seq] ?: 0) + 1) }
                OutboxAction.SKIP ->
                    runCatching { outboxDao.updateStatus(planned.seq, OutboxEntity.STATUS_SKIPPED) }
            }
        }
        applied
    }

    /**
     * Push one mutation, returning its outcome. Reconciliation short-circuits an already-satisfied
     * change to [PushOutcome.APPLIED] without a plugin call. Create-playlist records the account id so
     * this drain's later playlist mutations can target it.
     */
    private suspend fun push(
        pluginId: String,
        mutation: LibraryMutation,
        remoteState: RemoteState,
        remotePlaylistIds: MutableMap<String, String>,
    ): PushOutcome {
        if (!PushReconciler.isNeeded(mutation, remoteState)) return PushOutcome.APPLIED

        val result: Result<*> = when (mutation) {
            is LibraryMutation.SetLiked ->
                gateway.setLiked(pluginId, mutation.trackId, mutation.liked)
            is LibraryMutation.SetFollowed ->
                gateway.setFollowed(pluginId, mutation.artistId, mutation.followed)
            is LibraryMutation.CreatePlaylist ->
                gateway.createPlaylist(pluginId, mutation.name)
                    .onSuccess { remoteId -> remotePlaylistIds[mutation.localPlaylistId] = remoteId }
            is LibraryMutation.RenamePlaylist ->
                gateway.renamePlaylist(pluginId, resolvePlaylistId(mutation.playlistId, remotePlaylistIds), mutation.name)
            is LibraryMutation.DeletePlaylist ->
                gateway.deletePlaylist(pluginId, resolvePlaylistId(mutation.playlistId, remotePlaylistIds))
            is LibraryMutation.AddTrackToPlaylist ->
                gateway.addTrackToPlaylist(pluginId, resolvePlaylistId(mutation.playlistId, remotePlaylistIds), mutation.trackId)
            is LibraryMutation.RemoveTrackFromPlaylist ->
                gateway.removeTrackFromPlaylist(pluginId, resolvePlaylistId(mutation.playlistId, remotePlaylistIds), mutation.trackId)
        }
        return result.fold(onSuccess = { PushOutcome.APPLIED }, onFailure = { classify(it) })
    }

    /** Prefer this drain's just-created remote id for a local playlist; else use the given id as-is. */
    private fun resolvePlaylistId(playlistId: String, remotePlaylistIds: Map<String, String>): String =
        remotePlaylistIds[playlistId] ?: playlistId

    /**
     * Map a push failure to an outcome. The capability gate already parked genuinely write-incapable
     * plugins, so an UNSUPPORTED here means a mid-drain disconnect (source() throwing) — treat it as
     * retryable, not a permanent drop. Only truly hopeless codes (bad request, gone, revoked auth,
     * incompatible host) are permanent; everything else (network, rate-limit, internal, unavailable)
     * retries.
     */
    private fun classify(error: Throwable): PushOutcome {
        val code = (error as? PluginException)?.code ?: return PushOutcome.RETRYABLE
        return when (code) {
            PluginErrorCode.INVALID_REQUEST,
            PluginErrorCode.NOT_FOUND,
            PluginErrorCode.AUTH_REQUIRED,
            PluginErrorCode.AUTH_FAILED,
            PluginErrorCode.INCOMPATIBLE_HOST -> PushOutcome.PERMANENT
            else -> PushOutcome.RETRYABLE
        }
    }

    /**
     * Best-effort snapshot of the parts of the remote library a push might redundantly repeat, so
     * already-satisfied likes/follows become no-ops. Cheap and tolerant: any read that fails leaves
     * that dimension unknown (null), which the reconciler treats as "push anyway".
     */
    private suspend fun fetchRemoteState(pluginId: String): RemoteState = withContext(Dispatchers.IO) {
        val liked = runCatching { gateway.likedTrackIds(pluginId, REMOTE_STATE_LIMIT) }.getOrNull()
        val followed = runCatching { gateway.followedArtistIds(pluginId, REMOTE_STATE_LIMIT) }.getOrNull()
        RemoteState(
            likedTrackIds = liked?.ids,
            likedComplete = liked?.complete == true,
            followedArtistIds = followed?.ids,
            followedComplete = followed?.complete == true,
            playlistTrackIds = null,
        )
    }

    private suspend fun mutexFor(pluginId: String): Mutex = locksGuard.withLock {
        drainLocks.getOrPut(pluginId) { Mutex() }
    }

    private companion object {
        /** Cap on remote items fetched for reconciliation — a first page is enough to catch dupes. */
        const val REMOTE_STATE_LIMIT = 200
    }
}
