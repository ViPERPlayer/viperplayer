package com.viperplayer.data.social

import com.viperplayer.domain.social.FriendActivityItem
import com.viperplayer.domain.social.FriendActivityRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * No-backend stub for [FriendActivityRepository]: emits an empty feed so the friend-activity section
 * renders nothing until a real backend-backed implementation lands.
 */
@Singleton
class StubFriendActivityRepository @Inject constructor() : FriendActivityRepository {
    override val activity: Flow<List<FriendActivityItem>> = flowOf(emptyList())
}
