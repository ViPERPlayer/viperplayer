package com.viperplayer.data.rec

import com.viperplayer.data.local.entity.SongEntity

/**
 * Pure (Android-free) helpers deciding WHICH songs the [LibraryIndexWorker] embeds and in WHAT order.
 *
 * Split out from the worker so the two policy decisions the product cares about — "does this song have
 * local audio bytes we can decode?" and "which songs do we embed first?" — are unit-testable on the JVM
 * without a device, a codec or a model.
 */
object SongEmbedCandidates {

    /**
     * Priority tier for embedding order (lower value = embedded first). We front-load the songs the
     * user is most likely to want recommendations from and that are cheapest/most-reliable to decode:
     *  0. DOWNLOADED — bytes are already on disk (fast, offline-safe, and clearly "owned" music).
     *  1. LIKED      — explicit taste signal.
     *  2. RECENT     — recently played (has a `lastPlayed` timestamp).
     *  3. OTHER      — everything else (saved-but-untouched, etc.).
     */
    enum class Priority { DOWNLOADED, LIKED, RECENT, OTHER }

    /** The priority tier for [song]. Downloaded beats liked beats recently-played beats the rest. */
    fun priorityOf(song: SongEntity): Priority = when {
        song.isDownloaded -> Priority.DOWNLOADED
        song.isLiked -> Priority.LIKED
        song.lastPlayed != null -> Priority.RECENT
        else -> Priority.OTHER
    }

    /**
     * Whether [song] MIGHT have local audio bytes we can decode. A song qualifies when it is a
     * downloaded track with a non-blank [SongEntity.downloadPath], OR a local-library track (its
     * `idType == "local"`, whose `sourceId` is a resolvable `content://`/file source). Pure,
     * streaming-only plugin songs (no download, not local) have NO local bytes and are SKIPPED here —
     * they get server-side text embeddings in a later wave.
     *
     * This is a cheap pre-filter on the ROW; the actual file/URI is opened (and may still fail) by the
     * [SongAudioSourceResolver] at embed time.
     */
    fun hasLocalBytes(song: SongEntity): Boolean {
        val hasDownload = song.isDownloaded && !song.downloadPath.isNullOrBlank()
        val isLocalLibrary = song.idType == LOCAL_ID_TYPE
        return hasDownload || isLocalLibrary
    }

    /**
     * Orders [candidates] for embedding: by [priorityOf] tier, then most-recently-played first inside a
     * tier (a `null` lastPlayed sorts last), then a stable `id ASC` tiebreak so an interrupted run
     * resumes deterministically. Returns a new list; does not mutate the input.
     */
    fun prioritize(candidates: List<SongEntity>): List<SongEntity> =
        candidates.sortedWith(
            compareBy<SongEntity> { priorityOf(it).ordinal }
                .thenByDescending { it.lastPlayed ?: Long.MIN_VALUE }
                .thenBy { it.id }
        )

    /** Filters to only the songs with local bytes, then [prioritize]s them. */
    fun embeddableInPriorityOrder(candidates: List<SongEntity>): List<SongEntity> =
        prioritize(candidates.filter { hasLocalBytes(it) })

    /**
     * Whether [song] is a STREAMING-only candidate: a plugin-served track (`idType != "local"`) that is
     * NOT downloaded. These have no local bytes ([hasLocalBytes] is false), so the local
     * [LibraryIndexWorker] skips them; instead they are embedded from their live stream (Path B's
     * [StreamingIndexWorker]) or opportunistically captured during playback (Path A). This is the exact
     * complement of [hasLocalBytes] over the two known idTypes, so the local and streaming workers
     * partition the missing-embedding set with NO overlap and NO double-embed.
     *
     * A downloaded plugin track has local bytes and is the LOCAL worker's job, not this one — hence the
     * `!isDownloaded` guard.
     */
    fun isStreamingCandidate(song: SongEntity): Boolean =
        song.idType != LOCAL_ID_TYPE && !song.isDownloaded

    /** Filters to only the STREAMING-only songs, then [prioritize]s them (liked/recent first). */
    fun streamingInPriorityOrder(candidates: List<SongEntity>): List<SongEntity> =
        prioritize(candidates.filter { isStreamingCandidate(it) })

    /** The [SongEntity.idType] discriminator for on-device local-library tracks. */
    const val LOCAL_ID_TYPE = "local"
}
