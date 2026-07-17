package com.viperplayer.domain.librarysync

/**
 * The per-user library-sync surface against the ViPER backend (the `/library` routes). Every call is
 * authenticated transparently through the account session (refresh-and-retry once on a rejected
 * token); the UI never touches this directly — a ViewModel/orchestrator does.
 *
 * Concurrency model: mutations are coordinated by a per-user monotonic revision. A playlist upsert
 * carries the base revision the caller last saw; a stale base comes back as
 * [UpsertResult.conflict] = true (pull-merge-retry) rather than clobbering a concurrent edit.
 *
 * Scope note: this is the transport seam only. The local↔backend reconciliation/orchestration
 * (when to auto-push/pull, merge policy, an outbox) is a separate follow-up; these methods are ready
 * to be called by it.
 */
interface LibrarySyncRepository {

    /** Whether a backend URL is configured. When false every call returns [LibrarySyncResult.NotConfigured]. */
    val isConfigured: Boolean

    /** `GET /library` — the signed-in user's whole synced library snapshot (playlists + likes + revision). */
    suspend fun getLibrary(): LibrarySyncResult<LibrarySnapshot>

    /**
     * `PUT /library/playlists` — creates or replaces [playlist]. [baseRevision] is the revision last
     * seen for this playlist; a stale base comes back as [UpsertResult.conflict] = true.
     */
    suspend fun upsertPlaylist(playlist: SyncedPlaylist, baseRevision: Long): LibrarySyncResult<UpsertResult>

    /** `POST /library/playlists/delete` — deletes the playlist [playlistId]; returns the new library revision. */
    suspend fun deletePlaylist(playlistId: String): LibrarySyncResult<Long>

    /** `POST /library/likes` — sets/unsets a like on [track]; returns the new library revision. */
    suspend fun setLike(track: SyncedTrack, liked: Boolean): LibrarySyncResult<Long>
}
