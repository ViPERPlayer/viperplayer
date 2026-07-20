package com.viperplayer.data.social

import com.viperplayer.domain.social.SharedPlaylistInvite
import com.viperplayer.domain.social.SharedPlaylistsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * No-backend stub for [SharedPlaylistsRepository]: emits an empty inbox and zero unread, and the
 * save/dismiss actions are no-ops, until a real backend-backed implementation lands.
 */
@Singleton
class StubSharedPlaylistsRepository @Inject constructor() : SharedPlaylistsRepository {
    override val inbox: Flow<List<SharedPlaylistInvite>> = flowOf(emptyList())
    override val unreadCount: Flow<Int> = flowOf(0)

    override suspend fun save(id: String) = Unit
    override suspend fun dismiss(id: String) = Unit
}
