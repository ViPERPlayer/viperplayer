package com.viperplayer.domain.model

/**
 * A human-readable *reason* attached to a recommended song ("Sounds like X"), kept UI-string-free so it
 * stays a pure domain type — the presentation layer localizes each [kind] with its [arg] (see the
 * `rec_reason_*` string resources). Computed in the recommendation repositories, where the candidate
 * embeddings / seed / taste are available, and threaded through [RecResult] to the row renderer.
 *
 * Only the LOCAL paths (which have on-device embeddings) can compute a specific anchor-based reason;
 * the server Discover path (no embeddings) uses the generic [MATCHES_TASTE].
 */
data class RecReason(
    val kind: Kind,
    /** The anchor/seed title or artist name for the templated reasons; null for [Kind.MATCHES_TASTE]. */
    val arg: String? = null,
) {
    enum class Kind {
        /** "Sounds like {arg}" — nearest owned liked/most-played anchor song by cosine (local For You). */
        SOUNDS_LIKE,

        /** "Because you like {arg}" — anchor by artist (local, when the anchor's artist is the signal). */
        BECAUSE_YOU_LIKE,

        /** "More like {arg}" — the seed track ("More like this"). */
        MORE_LIKE,

        /** "Matches your taste" — the generic, no-specific-anchor reason (server Discover). */
        MATCHES_TASTE,
    }
}
