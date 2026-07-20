package com.viperplayer.follows.data

import com.viperplayer.data.sync.push.LibraryMutation
import com.viperplayer.data.sync.push.LibraryPushOutbox
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
    private val libraryPushOutbox: LibraryPushOutbox,
) : FollowedArtistsRepository {

    override fun followedArtists(sort: FollowedArtistSort): Flow<List<FollowedArtist>> =
        dao.observeAll().map { rows ->
            FollowedArtistOrdering.sorted(rows.map { it.toDomain() }, sort)
        }

    override fun isFollowing(mediaId: MediaId): Flow<Boolean> =
        dao.observeIsFollowing(mediaId.pluginId, mediaId.sourceId)

    override fun count(): Flow<Int> = dao.observeCount()

    override suspend fun follow(artist: FollowedArtist) {
        // INSERT OR IGNORE against the unique (pluginId, sourceId) index makes this idempotent.
        dao.insert(FollowedArtistEntity.fromDomain(artist))
        // Propagate the follow up to the originating plugin account (two-way sync push).
        libraryPushOutbox.enqueue(
            LibraryMutation.SetFollowed(artist.mediaId.pluginId, artist.mediaId.sourceId, followed = true)
        )
    }

    override suspend fun unfollow(mediaId: MediaId) {
        dao.delete(mediaId.pluginId, mediaId.sourceId)
        libraryPushOutbox.enqueue(
            LibraryMutation.SetFollowed(mediaId.pluginId, mediaId.sourceId, followed = false)
        )
    }
}
