package com.viperplayer.follows.domain

import com.viperplayer.domain.model.MediaId

/**
 * A followed (subscribed) artist. Self-contained in the follows feature; carries just enough to list
 * the artist and re-open its detail screen (via [mediaId]).
 *
 * @param mediaId the artist's cross-plugin id (pluginId + sourceId); uniquely identifies the follow.
 * @param name display name.
 * @param artworkUrl optional avatar/artwork.
 * @param followedAt epoch millis when the artist was followed — used for "recently followed" order.
 */
data class FollowedArtist(
    val mediaId: MediaId,
    val name: String,
    val artworkUrl: String?,
    val followedAt: Long,
)

/** How the Following list is ordered. */
enum class FollowedArtistSort {
    /** Alphabetical by name (case-insensitive), ties broken by most-recent. */
    NAME,

    /** Most recently followed first. */
    RECENTLY_FOLLOWED,
}

/**
 * Pure ordering/dedupe helpers for followed artists. No Android, no I/O — unit-testable in isolation.
 */
object FollowedArtistOrdering {

    /**
     * Collapse duplicates by [FollowedArtist.mediaId], keeping the entry with the newest
     * [FollowedArtist.followedAt] for each id. The relative input order is otherwise preserved.
     */
    fun dedupe(artists: List<FollowedArtist>): List<FollowedArtist> {
        val byId = LinkedHashMap<MediaId, FollowedArtist>()
        for (artist in artists) {
            val existing = byId[artist.mediaId]
            if (existing == null || artist.followedAt >= existing.followedAt) {
                byId[artist.mediaId] = artist
            }
        }
        return byId.values.toList()
    }

    /**
     * De-duplicate then sort. [FollowedArtistSort.NAME] is case-insensitive with most-recent as the
     * tie-breaker; [FollowedArtistSort.RECENTLY_FOLLOWED] is newest-first with name as the tie-breaker.
     */
    fun sorted(
        artists: List<FollowedArtist>,
        sort: FollowedArtistSort,
    ): List<FollowedArtist> {
        val unique = dedupe(artists)
        val byName: Comparator<FollowedArtist> =
            Comparator { a, b -> String.CASE_INSENSITIVE_ORDER.compare(a.name, b.name) }
        return when (sort) {
            FollowedArtistSort.NAME -> unique.sortedWith(
                byName.thenByDescending { it.followedAt }
            )

            FollowedArtistSort.RECENTLY_FOLLOWED -> unique.sortedWith(
                compareByDescending<FollowedArtist> { it.followedAt }.then(byName)
            )
        }
    }
}
