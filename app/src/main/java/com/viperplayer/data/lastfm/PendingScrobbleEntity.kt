package com.viperplayer.data.lastfm

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.viperplayer.domain.lastfm.PendingScrobble
import com.viperplayer.domain.lastfm.ScrobbleTrack

/**
 * One un-submitted scrobble in the offline queue ("viperplayer_lastfm" database). A scrobble lands
 * here when its `track.scrobble` submission fails (offline / server error) and is drained on the next
 * successful call or connectivity change. Fully denormalized — it depends on nothing else.
 *
 * A unique index on (artist, track, timestampSeconds) mirrors Last.fm's own scrobble identity so
 * `INSERT OR IGNORE` de-dupes: a retry of the same play can never enqueue a duplicate row.
 *
 * @param timestampSeconds UNIX time (seconds) the track started playing — the scrobble's timestamp.
 */
@Entity(
    tableName = "pending_scrobbles",
    indices = [
        Index(value = ["artist", "track", "timestampSeconds"], unique = true),
        Index(value = ["timestampSeconds"]),
    ],
)
data class PendingScrobbleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val artist: String,
    val track: String,
    val album: String?,
    val albumArtist: String?,
    val trackNumber: Int?,
    val durationSeconds: Long?,
    val mbid: String?,
    val timestampSeconds: Long,
) {
    fun toDomain(): PendingScrobble = PendingScrobble(
        track = ScrobbleTrack(
            artist = artist,
            track = track,
            album = album,
            durationSeconds = durationSeconds,
            albumArtist = albumArtist,
            trackNumber = trackNumber,
            mbid = mbid,
        ),
        timestampSeconds = timestampSeconds,
    )

    companion object {
        fun fromDomain(pending: PendingScrobble): PendingScrobbleEntity {
            val t = pending.track
            return PendingScrobbleEntity(
                artist = t.artist,
                track = t.track,
                album = t.album,
                albumArtist = t.albumArtist,
                trackNumber = t.trackNumber,
                durationSeconds = t.durationSeconds,
                mbid = t.mbid,
                timestampSeconds = pending.timestampSeconds,
            )
        }
    }
}
