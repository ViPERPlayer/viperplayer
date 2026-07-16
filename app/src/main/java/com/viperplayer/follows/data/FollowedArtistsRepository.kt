package com.viperplayer.follows.data

import com.viperplayer.domain.model.MediaId
import com.viperplayer.follows.domain.FollowedArtist
import com.viperplayer.follows.domain.FollowedArtistSort
import kotlinx.coroutines.flow.Flow

/**
 * Repository over the followed-artists DAO. Exposes followed artists as a [Flow] and mutates them.
 * Follow is idempotent (no duplicates); [isFollowing] reflects live state.
 */
interface FollowedArtistsRepository {

    /** Live list of followed artists, ordered by the given [sort] (default: alphabetical by name). */
    fun followedArtists(sort: FollowedArtistSort = FollowedArtistSort.NAME): Flow<List<FollowedArtist>>

    /** Live "is this artist followed?" flag. */
    fun isFollowing(mediaId: MediaId): Flow<Boolean>

    /** Follow an artist. Idempotent: following an already-followed artist is a no-op. */
    suspend fun follow(artist: FollowedArtist)

    /** Unfollow the artist with the given [mediaId]. A no-op if it wasn't followed. */
    suspend fun unfollow(mediaId: MediaId)
}
