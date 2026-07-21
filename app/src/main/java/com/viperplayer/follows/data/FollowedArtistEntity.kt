package com.viperplayer.follows.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.viperplayer.domain.model.MediaId
import com.viperplayer.follows.domain.FollowedArtist

/**
 * Room row for a followed artist. Self-contained in the follows feature's own database so the shared
 * [com.viperplayer.data.local.ViperPlayerDatabase] is never touched.
 *
 * The follow is uniquely identified by (`pluginId`, `sourceId`) — the components of a [MediaId]. The
 * unique index makes `INSERT OR IGNORE` idempotent so following the same artist twice is a no-op.
 */
@Entity(
    tableName = "followed_artists",
    indices = [Index(value = ["pluginId", "sourceId"], unique = true)],
)
data class FollowedArtistEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val pluginId: String,
    val sourceId: String,
    val name: String,
    val artworkUrl: String?,
    val followedAt: Long,
) {
    fun toDomain(): FollowedArtist = FollowedArtist(
        // Reconstruct the typed id from the stored routing id: the embedded local plugin maps back to
        // MediaId.Local, every other plugin to MediaId.Plugin (never Plugin("local", …)).
        mediaId = MediaId.from(pluginId, sourceId),
        name = name,
        artworkUrl = artworkUrl,
        followedAt = followedAt,
    )

    companion object {
        fun fromDomain(artist: FollowedArtist): FollowedArtistEntity {
            // Store the routing id so it matches the isFollowing/unfollow reads, which also key on
            // routingPluginId (local → "local"). Artists are never Radio, so this never throws.
            return FollowedArtistEntity(
                pluginId = artist.mediaId.routingPluginId,
                sourceId = artist.mediaId.sourceId,
                name = artist.name,
                artworkUrl = artist.artworkUrl,
                followedAt = artist.followedAt,
            )
        }
    }
}
