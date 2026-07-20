package com.viperplayer.domain.social

import kotlinx.coroutines.flow.Flow

/**
 * Source of the "shared with you" inbox of playlist invites, plus the unread badge count and the two
 * inbox actions. Backend-gated: when the backend is unconfigured the stub implementation emits an empty
 * inbox / zero unread and the mutations are no-ops.
 */
interface SharedPlaylistsRepository {

    /** Live inbox of shared-playlist invites. */
    val inbox: Flow<List<SharedPlaylistInvite>>

    /** Live count of unread invites (drives the inbox badge). */
    val unreadCount: Flow<Int>

    /** Accept/save the invite with the given [id] into the user's library. */
    suspend fun save(id: String)

    /** Dismiss (decline/remove) the invite with the given [id] from the inbox. */
    suspend fun dismiss(id: String)
}
