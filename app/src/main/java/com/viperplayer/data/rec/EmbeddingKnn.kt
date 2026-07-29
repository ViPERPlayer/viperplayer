package com.viperplayer.data.rec

import kotlin.math.sqrt

/**
 * A candidate id paired with its similarity [score] (higher = more similar). Generic over the id type
 * ([T]) so the pure kNN core is decoupled from any concrete id — the recommender keys candidates by the
 * Room row `Long` while unit tests use plain ids.
 *
 * [id] participates in the deterministic tie-break of [EmbeddingKnn.cosineTopK] (see there); [score] is
 * the cosine similarity (== dot product for the app's L2-normalized CLAP embeddings).
 */
data class ScoredId<out T>(val id: T, val score: Float)

/**
 * Pure, framework-free brute-force k-nearest-neighbour over CLAP audio embeddings.
 *
 * The app's embeddings are 512-d and L2-normalized (see [ClapAudioEncoder]/[l2Normalize]), so cosine
 * similarity reduces to the plain dot product — no per-candidate normalization is needed. A linear scan
 * is comfortably fast for the low-thousands-of-songs local libraries this targets (P2 can swap in an ANN
 * index behind the same surface if a library ever gets large enough to matter).
 *
 * Deliberately Android-free and stateless so it unit-tests directly and can be reused unchanged.
 */
object EmbeddingKnn {

    /**
     * The top [k] candidates most similar to [query] by cosine similarity, best first.
     *
     * @param query      the seed/centroid vector; cosine == dot product because vectors are unit-norm.
     * @param candidates `(id, vector)` pairs to score. Vectors of a length other than [query]'s are
     *                   skipped defensively (a corrupt/mismatched row can't poison the ranking).
     * @param k          how many results to return (a non-positive [k] yields an empty list).
     * @param excludeIds ids to omit from the results (e.g. the seed itself and its trivial duplicates).
     *
     * Ties (equal score) are broken **deterministically** by the natural order of [id] via
     * [Comparable], so the same inputs always produce the same ranking — important for stable UI and
     * reproducible tests. [T] must be [Comparable] for that reason.
     */
    fun <T : Comparable<T>> cosineTopK(
        query: FloatArray,
        candidates: List<Pair<T, FloatArray>>,
        k: Int,
        excludeIds: Set<T> = emptySet(),
    ): List<ScoredId<T>> {
        if (k <= 0 || candidates.isEmpty()) return emptyList()
        val dim = query.size

        val scored = ArrayList<ScoredId<T>>(candidates.size)
        for ((id, vector) in candidates) {
            if (id in excludeIds) continue
            if (vector.size != dim) continue
            scored += ScoredId(id, dot(query, vector))
        }

        // Highest score first; deterministic id tie-break for equal scores (stable ordering).
        scored.sortWith(
            compareByDescending<ScoredId<T>> { it.score }.thenBy { it.id }
        )
        return if (scored.size > k) scored.subList(0, k).toList() else scored
    }

    /**
     * The re-normalized mean (centroid) of [vectors], or `null` when [vectors] is empty (no centroid to
     * form). Averaging unit vectors yields a vector shorter than 1, so the result is L2-normalized back
     * to unit length — it can then be compared to the library with [cosineTopK] as a "taste" query.
     *
     * Vectors whose length differs from the first are skipped defensively; if that leaves nothing, or
     * the centroid is degenerate (all-zero, e.g. antipodal inputs), the result is `null`.
     */
    fun meanVector(vectors: List<FloatArray>): FloatArray? {
        if (vectors.isEmpty()) return null
        val dim = vectors.first().size
        if (dim == 0) return null

        val sum = DoubleArray(dim)
        var count = 0
        for (v in vectors) {
            if (v.size != dim) continue
            for (i in 0 until dim) sum[i] += v[i]
            count++
        }
        if (count == 0) return null

        val mean = FloatArray(dim) { (sum[it] / count).toFloat() }
        // Guard against a degenerate (all-zero) centroid before normalizing.
        var norm = 0.0
        for (x in mean) norm += x.toDouble() * x
        if (norm <= 0.0) return null
        val inv = (1.0 / sqrt(norm)).toFloat()
        return FloatArray(dim) { mean[it] * inv }
    }

    /** Dot product of two equal-length vectors (== cosine similarity for unit-norm inputs). */
    private fun dot(a: FloatArray, b: FloatArray): Float {
        var acc = 0.0
        for (i in a.indices) acc += a[i].toDouble() * b[i]
        return acc.toFloat()
    }
}
