package com.viperplayer.domain.social

import com.viperplayer.domain.model.MediaId

/**
 * Domain models for the (backend-gated) social surface: friends rail, live-Jam cards, the
 * shared-with-you inbox, and the friend-activity feed. Plain immutable value types with no Android or
 * transport dependencies — the repositories in [com.viperplayer.domain.social] expose them and the UI
 * renders them. When the backend is unconfigured every repository emits empty, so the social sections
 * are simply absent.
 */

/** A single friend for the friends rail, optionally with what they're playing right now. */
data class Friend(
    val id: String,
    val displayName: String,
    val avatarUrl: String?,
    val nowPlaying: FriendTrack?,
)

/** The track a friend is currently playing (the now-playing badge on their avatar). */
data class FriendTrack(
    val title: String,
    val artist: String,
    val artworkUrl: String?,
)

/**
 * A live Jam session surfaced as a joinable card: who's hosting, how many friends are listening (with
 * a few initials for stacked avatars), the current track, and the share [code] used to join (may be
 * null when only an in-app deep link is offered).
 */
data class LiveJam(
    val sessionId: String,
    val name: String,
    val hostName: String,
    val listenerCount: Int,
    val listenerInitials: List<String>,
    val currentTrack: String,
    val code: String?,
)

/**
 * A shared-playlist invite in the "shared with you" inbox: who shared it, an optional note, when, how
 * many songs, and whether the recipient has seen it yet.
 *
 * [sharerId] is a stable per-sharer key used only to pick a deterministic avatar tint (so the same
 * sharer always gets the same tone). It defaults to "" for stub producers, which may pass [sharerName]
 * as the id until a real backend supplies a stable friend id. [playlistMediaId] is the portable id of
 * the shared playlist, used to open its detail screen from the inbox Preview action (null until a
 * backend supplies it — Preview is then a no-op).
 */
data class SharedPlaylistInvite(
    val id: String,
    val playlistName: String,
    val playlistArtworkUrl: String?,
    val sharerName: String,
    val sharerAvatarUrl: String?,
    val sharerId: String = "",
    val message: String?,
    val timestampMs: Long,
    val songCount: Int,
    val unread: Boolean,
    val playlistMediaId: MediaId? = null,
)

/**
 * One entry in the friend-activity feed. Variants mirror the mockup's activity types; each carries a
 * [timestampMs] so the feed can be ordered/grouped by recency.
 */
sealed interface FriendActivityItem {
    val friend: Friend
    val timestampMs: Long

    /**
     * A friend is listening right now; [isHosting] flags an active Jam they're hosting. [sessionCode]
     * is the joinable Jam share code when they're hosting — used to prefill the Join-a-Jam flow. Null
     * when unknown (the Join affordance is then hidden/disabled).
     */
    data class ListeningNow(
        override val friend: Friend,
        val track: FriendTrack,
        val isHosting: Boolean,
        override val timestampMs: Long,
        val sessionCode: String? = null,
    ) : FriendActivityItem

    /** A friend shared a playlist. */
    data class SharedPlaylist(
        override val friend: Friend,
        val playlistName: String,
        override val timestampMs: Long,
    ) : FriendActivityItem

    /** A friend liked a song. */
    data class LikedSong(
        override val friend: Friend,
        val songTitle: String,
        override val timestampMs: Long,
    ) : FriendActivityItem

    /**
     * A friend followed an artist. [artistArtworkUrl] is the artist's image (shown as the trailing
     * thumbnail) and [artistMediaId] its portable id for a future tap-through to artist detail; both
     * default to null until a backend supplies them.
     */
    data class FollowedArtist(
        override val friend: Friend,
        val artistName: String,
        override val timestampMs: Long,
        val artistArtworkUrl: String? = null,
        val artistMediaId: MediaId? = null,
    ) : FriendActivityItem
}
