package com.viperplayer.data.sync.push

/**
 * A single local library change that should be propagated *up* to a plugin's signed-in account so the
 * account library stays in sync with what the user did locally (the PUSH direction of two-way sync;
 * the PULL direction is [com.viperplayer.data.sync.LibrarySyncManager]).
 *
 * These are **pure value objects** — no Android, no persistence, no plugin calls. The outbox
 * ([OutboxEntry]) records them and [MutationCoalescer] reasons about a sequence of them
 * (coalescing/ordering/dedupe) before the drain manager pushes the survivors. Keeping this
 * Android-free is what makes the coalescing/ordering the JVM-testable core of the feature.
 *
 * Every mutation carries the [pluginId] whose account it targets, so the drain can route each one to
 * the right plugin. Playlist/track ids are the plugin's own source ids (never the app's `MediaId`
 * string), because the remote account speaks in its own ids.
 */
sealed interface LibraryMutation {

    /** The plugin account this mutation must be pushed to. */
    val pluginId: String

    /**
     * A stable key identifying *what* this mutation targets. Two mutations with the same [target] act
     * on the same remote object, so a later one supersedes/cancels an earlier one when coalescing.
     * Includes the plugin so identical source ids across plugins never collide.
     */
    val target: Target

    /** Like or unlike a track. Unlike after like (same track) cancels out. */
    data class SetLiked(
        override val pluginId: String,
        val trackId: String,
        val liked: Boolean,
    ) : LibraryMutation {
        override val target get() = Target(pluginId, KIND_LIKE, trackId)
    }

    /** Follow or unfollow an artist. Unfollow after follow (same artist) cancels out. */
    data class SetFollowed(
        override val pluginId: String,
        val artistId: String,
        val followed: Boolean,
    ) : LibraryMutation {
        override val target get() = Target(pluginId, KIND_FOLLOW, artistId)
    }

    /**
     * Create a playlist on the account. [localPlaylistId] is the app's local playlist id; the drain
     * uses it to correlate the remote id the plugin returns so later add/remove mutations for the same
     * local playlist can be routed to the created remote playlist.
     */
    data class CreatePlaylist(
        override val pluginId: String,
        val localPlaylistId: String,
        val name: String,
    ) : LibraryMutation {
        // A distinct kind from rename/delete: a create and a later rename of the same playlist must
        // NOT collapse into one another (both are needed — create it, then rename it), whereas repeated
        // renames of one playlist DO collapse. Delete-supersedes still works because it keys on the
        // playlist id via playlistOwnerKey, independent of this target kind.
        override val target get() = Target(pluginId, KIND_PLAYLIST_CREATE, localPlaylistId)
    }

    /** Rename a playlist. Repeated renames of the same playlist collapse to the last name. */
    data class RenamePlaylist(
        override val pluginId: String,
        val playlistId: String,
        val name: String,
    ) : LibraryMutation {
        override val target get() = Target(pluginId, KIND_PLAYLIST, playlistId)
    }

    /** Delete a playlist. Supersedes every other pending mutation for the same playlist. */
    data class DeletePlaylist(
        override val pluginId: String,
        val playlistId: String,
    ) : LibraryMutation {
        override val target get() = Target(pluginId, KIND_PLAYLIST, playlistId)
    }

    /** Add a track to a playlist. Add then remove (same playlist+track) cancels out. */
    data class AddTrackToPlaylist(
        override val pluginId: String,
        val playlistId: String,
        val trackId: String,
    ) : LibraryMutation {
        override val target get() = Target(pluginId, KIND_PLAYLIST_TRACK, "$playlistId|$trackId")
    }

    /** Remove a track from a playlist. Remove then add (same playlist+track) cancels out. */
    data class RemoveTrackFromPlaylist(
        override val pluginId: String,
        val playlistId: String,
        val trackId: String,
    ) : LibraryMutation {
        override val target get() = Target(pluginId, KIND_PLAYLIST_TRACK, "$playlistId|$trackId")
    }

    /**
     * The identity of the remote object a mutation acts on: (plugin, kind, key). Used purely for
     * coalescing — two mutations sharing a [Target] address the same thing.
     */
    data class Target(val pluginId: String, val kind: String, val key: String)

    companion object {
        const val KIND_LIKE = "like"
        const val KIND_FOLLOW = "follow"
        const val KIND_PLAYLIST = "playlist"
        const val KIND_PLAYLIST_CREATE = "playlistCreate"
        const val KIND_PLAYLIST_TRACK = "playlistTrack"
    }
}
