package com.viperplayer.domain.autoplaylist

import com.viperplayer.domain.model.ArtistRef
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.stats.PlayRecord
import java.util.Random

/**
 * Pure, Android-free generation of the dynamic auto-playlists' song lists.
 *
 * Every function here is deterministic and side-effect free: it takes the raw inputs (library songs
 * and/or listening [PlayRecord]s, plus `now` / `limit` / a shuffle `seed`) and returns the ordered
 * `List<Song>` for one [AutoPlaylistType]. There are no DB, clock, or RNG dependencies — the
 * repository supplies the real data and `System.currentTimeMillis()`, and the ViewModel/screen only
 * render the result. This is what makes the generators trivially unit-testable.
 *
 * ### How a play-history song that is no longer in the library is handled
 * The play-based generators ([mostPlayed], [recentlyPlayed], [forgotten]) key off the play-history,
 * which is fully denormalized (title/artist/album/artwork are copied onto each [PlayRecord]). A song
 * present in history but missing from the current library is therefore **included** using its cached
 * metadata (see [recordToSong]) rather than skipped, so the playlist doesn't silently lose tracks
 * when a plugin song is un-saved. Where the live library still has the song, its (richer, current)
 * [Song] is preferred so likes/artwork/playability stay accurate.
 */
object AutoPlaylistGenerator {

    /** Default maximum number of songs an auto-playlist yields. */
    const val DEFAULT_LIMIT = 100

    /**
     * "Recently added" — library songs newest-first.
     *
     * [songsNewestFirst] must already be ordered newest-added first (the repository supplies this via
     * an insertion-order query). This function de-duplicates by [Song.id] (keeping the first, i.e.
     * newest, occurrence) and takes the first [limit]. Keeping the ordering as an input — rather than
     * re-deriving a date here — keeps the function pure while the "date desc" ordering lives in SQL.
     */
    fun recentlyAdded(songsNewestFirst: List<Song>, limit: Int = DEFAULT_LIMIT): List<Song> =
        songsNewestFirst.distinctBy { it.id }.take(limit.coerceAtLeast(0))

    /**
     * "Favorites / Liked" — the user's liked songs, in the order supplied by the liked-songs source.
     * De-duplicated by id and limited. (A thin wrapper so the surface is uniform with the others.)
     */
    fun favorites(likedSongs: List<Song>, limit: Int = DEFAULT_LIMIT): List<Song> =
        likedSongs.distinctBy { it.id }.take(limit.coerceAtLeast(0))

    /**
     * "Shuffle All" — every library song in a shuffled order. Deterministic for a given [seed] so the
     * result is testable; the caller passes a fresh seed (e.g. the current time) for real shuffles.
     */
    fun shuffleAll(librarySongs: List<Song>, seed: Long, limit: Int = DEFAULT_LIMIT): List<Song> =
        librarySongs.distinctBy { it.id }
            .shuffled(Random(seed))
            .take(limit.coerceAtLeast(0))

    /**
     * "Most Played" — songs ranked by play count (descending) within the given records.
     *
     * Ties break deterministically: higher play count, then more-recent last play, then [MediaId]
     * string ascending — identical inputs always yield the same order. Each ranked media id resolves
     * to a [Song] via [library] (preferred) or the play-history's cached metadata.
     *
     * @param records the play-history to rank over (already range-filtered by the caller if desired).
     * @param library the current library songs, indexed by id for resolution.
     * @param keyOf how a [Song]'s [MediaId] maps to the string the play-history stores. Defaults to the
     *   pure [mediaKey]; the (Android) repository passes `MediaId::encode` so the join matches the
     *   Uri-encoded ids actually persisted. Overriding it keeps this function Android-free/testable.
     * @param mediaIdOf how a history record's id string is turned back into a [MediaId] when the song is
     *   no longer in the library. Defaults to the pure [parseMediaId]; the (Android) repository passes a
     *   `MediaId.decode`-based parser so the reconstructed id is Uri-DECODED (round-tripping the
     *   encoded string the history stores) instead of keeping percent-escapes. Kept as a parameter so
     *   this function stays Android-free/testable.
     */
    fun mostPlayed(
        records: List<PlayRecord>,
        library: List<Song>,
        limit: Int = DEFAULT_LIMIT,
        keyOf: (MediaId) -> String = ::mediaKey,
        mediaIdOf: (String) -> MediaId = ::parseMediaId,
    ): List<Song> {
        if (records.isEmpty()) return emptyList()
        val byId = indexLibrary(library, keyOf)
        return records.groupBy { it.mediaId }
            .map { (mediaId, group) ->
                RankedPlay(
                    mediaId = mediaId,
                    playCount = group.size,
                    lastPlayedMs = group.maxOf { it.timestampMs },
                    representative = group.first(),
                )
            }
            .sortedWith(
                compareByDescending<RankedPlay> { it.playCount }
                    .thenByDescending { it.lastPlayedMs }
                    .thenBy { it.mediaId },
            )
            .take(limit.coerceAtLeast(0))
            .map { resolve(it.mediaId, it.representative, byId, mediaIdOf) }
    }

    /**
     * "Recently Played" — the distinct songs most recently played, newest first.
     *
     * Collapses the (chronological) history to one entry per media id at its most-recent play, orders
     * by that play descending (id ascending as a deterministic tie-break), and takes [limit].
     */
    fun recentlyPlayed(
        records: List<PlayRecord>,
        library: List<Song>,
        limit: Int = DEFAULT_LIMIT,
        keyOf: (MediaId) -> String = ::mediaKey,
        mediaIdOf: (String) -> MediaId = ::parseMediaId,
    ): List<Song> {
        if (records.isEmpty()) return emptyList()
        val byId = indexLibrary(library, keyOf)
        return records.groupBy { it.mediaId }
            .map { (mediaId, group) ->
                val latest = group.maxByOrNull { it.timestampMs }!!
                RankedPlay(
                    mediaId = mediaId,
                    playCount = group.size,
                    lastPlayedMs = latest.timestampMs,
                    representative = latest,
                )
            }
            .sortedWith(
                compareByDescending<RankedPlay> { it.lastPlayedMs }
                    .thenBy { it.mediaId },
            )
            .take(limit.coerceAtLeast(0))
            .map { resolve(it.mediaId, it.representative, byId, mediaIdOf) }
    }

    /**
     * "Forgotten" — songs played before but not recently: their most-recent play is older than
     * [notRecentBeforeMs] (i.e. `now - staleness window`). Ordered by most-recent play descending so
     * the "least forgotten" (closest to the cutoff) come first, then id ascending. Empty when nothing
     * qualifies.
     *
     * @param records the full play-history.
     * @param library current library songs for resolution.
     * @param notRecentBeforeMs a song qualifies only if its last play is strictly before this instant
     *   (typically `now - 30 days`). A song played at/after this is "recent" and excluded.
     */
    fun forgotten(
        records: List<PlayRecord>,
        library: List<Song>,
        notRecentBeforeMs: Long,
        limit: Int = DEFAULT_LIMIT,
        keyOf: (MediaId) -> String = ::mediaKey,
        mediaIdOf: (String) -> MediaId = ::parseMediaId,
    ): List<Song> {
        if (records.isEmpty()) return emptyList()
        val byId = indexLibrary(library, keyOf)
        return records.groupBy { it.mediaId }
            .map { (mediaId, group) ->
                val latest = group.maxByOrNull { it.timestampMs }!!
                RankedPlay(
                    mediaId = mediaId,
                    playCount = group.size,
                    lastPlayedMs = latest.timestampMs,
                    representative = latest,
                )
            }
            .filter { it.lastPlayedMs < notRecentBeforeMs }
            .sortedWith(
                compareByDescending<RankedPlay> { it.lastPlayedMs }
                    .thenBy { it.mediaId },
            )
            .take(limit.coerceAtLeast(0))
            .map { resolve(it.mediaId, it.representative, byId, mediaIdOf) }
    }

    /** A media id's aggregated play stats, used to rank the play-based playlists. */
    private data class RankedPlay(
        val mediaId: String,
        val playCount: Int,
        val lastPlayedMs: Long,
        val representative: PlayRecord,
    )

    /**
     * Resolves a ranked media id to a [Song]: prefers the live library entry (current likes/artwork/
     * playability), falling back to a [Song] rebuilt from the play-history's cached metadata when the
     * song is no longer in the library. The library is indexed by the same raw id string the
     * play-history stores ([Song.id] via [mediaKey]).
     */
    private fun resolve(
        mediaIdString: String,
        record: PlayRecord,
        byId: Map<String, Song>,
        mediaIdOf: (String) -> MediaId,
    ): Song = byId[mediaIdString] ?: recordToSong(record, mediaIdOf)

    /**
     * The join key between a [Song] and a [PlayRecord]: a stable, type-tagged string for the
     * [Song.id], computed purely (no Android `Uri`) so the generator stays Android-free and
     * JVM-testable. Must round-trip with [parseMediaId]. The on-device repository overrides this with
     * `MediaId.encode` so it matches exactly what the play-history stores in [PlayRecord.mediaId].
     */
    fun mediaKey(mediaId: MediaId): String = when (mediaId) {
        is MediaId.Plugin -> "t=plugin&p=${mediaId.pluginId}&s=${mediaId.sourceId}"
        is MediaId.Local -> "t=local&s=${mediaId.sourceId}"
        is MediaId.Radio -> "t=radio&seed=${mediaKey(mediaId.seed)}"
    }

    /** Indexes [library] by [keyOf] for O(1) resolution of a ranked history id back to a song. */
    private fun indexLibrary(library: List<Song>, keyOf: (MediaId) -> String): Map<String, Song> =
        library.associateBy { keyOf(it.id) }

    /**
     * Builds a minimal [Song] from a [PlayRecord]'s cached metadata, for a history song that is no
     * longer in the library. Not internet-required and marked playable so it can still be launched;
     * the player resolves the real stream via the plugin at play time.
     *
     * @param mediaIdOf turns the record's id string back into a [MediaId]. Defaults to the pure
     *   [parseMediaId] (no `Uri`) so this is JVM-testable; the (Android) repository passes a
     *   `MediaId.decode`-based parser so the reconstructed id is Uri-DECODED and round-trips exactly
     *   with the encoded string the history stores (otherwise the plugin is later handed a raw
     *   percent-encoded sourceId and resolves the wrong track / fails to stream — notably for `local`,
     *   whose sourceId is a `content://…` URI). Either parser must never throw on a malformed id.
     */
    fun recordToSong(
        record: PlayRecord,
        mediaIdOf: (String) -> MediaId = ::parseMediaId,
    ): Song = Song(
        id = mediaIdOf(record.mediaId),
        title = record.title,
        artists = record.artist.takeIf { it.isNotBlank() }
            ?.let { listOf(ArtistRef(name = it)) }
            .orEmpty(),
        durationMs = record.durationMs.takeIf { it > 0L },
        artworkUrl = record.artworkUrl,
    )

    /**
     * Pure parse of a [mediaKey] string into a [MediaId] (no Android `Uri`). Values are used as-is (NOT
     * Uri-decoded); a missing/blank `sourceId` falls back to the raw string so a [MediaId] can always
     * be constructed (its `sourceId` must be non-blank). Exposed as the pure default for
     * [recordToSong]/the play-based generators and as a safe fallback for the repository's decoding
     * parser (`MediaId.decode`) when an id is genuinely malformed; never throws.
     */
    fun parseMediaId(raw: String): MediaId {
        val params = raw.split("&").mapNotNull { part ->
            val idx = part.indexOf('=')
            if (idx <= 0) null else part.substring(0, idx) to part.substring(idx + 1)
        }.toMap()
        // sourceId must be non-blank (MediaId's init requires it); fall back to the raw string, then to
        // a placeholder so a malformed/blank id can never throw here.
        val sourceId = params["s"]?.takeIf { it.isNotBlank() }
            ?: raw.takeIf { it.isNotBlank() }
            ?: "unknown"
        return when (params["t"]) {
            "local" -> MediaId.Local(sourceId)
            "radio" -> params["seed"]?.takeIf { it.isNotBlank() }?.let { MediaId.Radio(parseMediaId(it)) }
                ?: MediaId.Local(sourceId)
            else -> MediaId.Plugin(params["p"].orEmpty().ifBlank { "unknown" }, sourceId)
        }
    }
}
