package com.viperplayer.domain.social

import kotlinx.coroutines.flow.Flow

/**
 * Source of the friends rail and joinable live-Jam cards. Backend-gated: when the backend is
 * unconfigured the stub implementation emits empty lists, so the friends rail and Jam cards are absent.
 */
interface FriendsRepository {

    /** Live list of the current user's friends, with their now-playing state where available. */
    val friends: Flow<List<Friend>>

    /** Live list of currently-joinable Jam sessions among the user's friends. */
    val liveJams: Flow<List<LiveJam>>
}
