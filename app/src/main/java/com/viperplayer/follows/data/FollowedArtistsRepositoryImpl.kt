package com.viperplayer.follows.data

import com.viperplayer.domain.model.MediaId
import com.viperplayer.follows.domain.FollowedArtist
import com.viperplayer.follows.domain.FollowedArtistOrdering
import com.viperplayer.follows.domain.FollowedArtistSort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FollowedArtistsRepositoryImpl @Inject constructor(
    private val dao: FollowedArtistDao,
) : FollowedArtistsRepository {

    override fun followedArtists(sort: FollowedArtistSort): Flow<List<FollowedArtist>> =
        dao.observeAll().map { rows ->
            FollowedArtistOrdering.sorted(rows.map { it.toDomain() }, sort)
        }

    override fun isFollowing(mediaId: MediaId): Flow<Boolean> =
        dao.observeIsFollowing(mediaId.pluginId, mediaId.sourceId)

    override suspend fun follow(artist: FollowedArtist) {
        // INSERT OR IGNORE against the unique (pluginId, sourceId) index makes this idempotent.
        dao.insert(FollowedArtistEntity.fromDomain(artist))
    }

    override suspend fun unfollow(mediaId: MediaId) {
        dao.delete(mediaId.pluginId, mediaId.sourceId)
    }
}
