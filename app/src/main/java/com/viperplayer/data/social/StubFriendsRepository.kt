package com.viperplayer.data.social

import com.viperplayer.domain.social.Friend
import com.viperplayer.domain.social.FriendsRepository
import com.viperplayer.domain.social.LiveJam
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * No-backend stub for [FriendsRepository]: emits empty friends and live-Jam lists so the friends rail
 * and Jam cards render nothing until a real backend-backed implementation lands.
 */
@Singleton
class StubFriendsRepository @Inject constructor() : FriendsRepository {
    override val friends: Flow<List<Friend>> = flowOf(emptyList())
    override val liveJams: Flow<List<LiveJam>> = flowOf(emptyList())
}
