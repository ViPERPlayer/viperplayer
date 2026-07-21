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
        // Followed artists are always plugin artists (follows sync to a plugin account).
        mediaId = MediaId.Plugin(pluginId, sourceId),
        name = name,
        artworkUrl = artworkUrl,
        followedAt = followedAt,
    )

    companion object {
        fun fromDomain(artist: FollowedArtist): FollowedArtistEntity {
            val mediaId = artist.mediaId
            val pluginId = if (mediaId is MediaId.Plugin) mediaId.pluginId else ""
            return FollowedArtistEntity(
                pluginId = pluginId,
                sourceId = mediaId.sourceId,
                name = artist.name,
                artworkUrl = artist.artworkUrl,
                followedAt = artist.followedAt,
            )
        }
    }
}
