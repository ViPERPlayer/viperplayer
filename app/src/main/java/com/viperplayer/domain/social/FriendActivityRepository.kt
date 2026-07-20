package com.viperplayer.domain.social

import kotlinx.coroutines.flow.Flow

/**
 * Source of the friend-activity feed (LISTENING NOW plus shared-playlist / liked-song / followed-artist
 * items). Backend-gated: when the backend is unconfigured the stub implementation emits an empty feed.
 */
interface FriendActivityRepository {

    /** Live, recency-ordered feed of friend activity. */
    val activity: Flow<List<FriendActivityItem>>
}
