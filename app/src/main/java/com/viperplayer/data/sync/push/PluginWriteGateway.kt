package com.viperplayer.data.sync.push

/**
 * The narrow slice of plugin capability the push drain needs: the account-library WRITE ops plus a
 * capability probe and the minimal reads used to build a [RemoteState] for reconciliation. An
 * interface (implemented by `PluginDataSource`) so [PushSyncManager] can be unit-tested against a
 * fake without a live plugin connection or Android.
 *
 * Every write returns a `Result`; a failure carrying a `PluginException` lets the manager classify
 * the outcome (unsupported / permanent / retryable). Reads return null on failure, leaving that
 * remote dimension unknown (the reconciler then pushes anyway).
 */
interface PluginWriteGateway {

    /**
     * The plugin's account-library write capability *right now*. Crucially distinguishes a connected
     * plugin that genuinely lacks the capability ([WriteCapability.UNSUPPORTED] → skip the entries)
     * from one that just isn't reachable yet ([WriteCapability.UNAVAILABLE] → keep pending and retry),
     * so a transient disconnect never permanently strands queued changes.
     */
    suspend fun libraryWriteCapability(pluginId: String): WriteCapability

    suspend fun setLiked(pluginId: String, trackId: String, liked: Boolean): Result<Unit>
    suspend fun setFollowed(pluginId: String, artistId: String, followed: Boolean): Result<Unit>
    suspend fun createPlaylist(pluginId: String, name: String): Result<String>
    suspend fun renamePlaylist(pluginId: String, playlistId: String, name: String): Result<Unit>
    suspend fun deletePlaylist(pluginId: String, playlistId: String): Result<Unit>
    suspend fun addTrackToPlaylist(pluginId: String, playlistId: String, trackId: String): Result<Unit>
    suspend fun removeTrackFromPlaylist(pluginId: String, playlistId: String, trackId: String): Result<Unit>

    /** Remote source ids of the account's currently liked/saved tracks (first page), or null. */
    suspend fun likedTrackIds(pluginId: String, limit: Int): RemoteSet?

    /** Remote source ids of the account's currently followed artists (first page), or null. */
    suspend fun followedArtistIds(pluginId: String, limit: Int): RemoteSet?
}

/**
 * A page of remote source ids plus whether it is the COMPLETE set. [complete] is false when the page
 * was full (there may be more) — the reconciler then won't skip a removal off it. See [PushReconciler].
 */
data class RemoteSet(val ids: Set<String>, val complete: Boolean)

/** The account-library write capability of a plugin at a moment in time. */
enum class WriteCapability {
    /** Connected and advertises library write — proceed to push. */
    SUPPORTED,

    /** Connected but does not advertise library write — skip its entries (a future update may add it). */
    UNSUPPORTED,

    /** Not currently reachable (disconnected/connecting/disabled) — keep entries pending and retry. */
    UNAVAILABLE,
}
