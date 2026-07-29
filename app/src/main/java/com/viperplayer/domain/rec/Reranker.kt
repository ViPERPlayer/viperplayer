package com.viperplayer.domain.rec

import kotlin.math.ln
import kotlin.math.sqrt

/**
 * A candidate to be reranked: an [id] (any [Comparable] so ties are deterministic) paired with its
 * L2-normalized CLAP [embedding]. Generic over the id type so the pure reranker is decoupled from the
 * app's Room row `Long` (tests use plain ids).
 */
data class Candidate<out T>(val id: T, val embedding: FloatArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Candidate<*>) return false
        return id == other.id && embedding.contentEquals(other.embedding)
    }

    override fun hashCode(): Int = 31 * (id?.hashCode() ?: 0) + embedding.contentHashCode()
}

/**
 * A reranked candidate with its final [score] and the [rank] it landed at (0-based, best first), plus
 * the [relevance] component so callers/tests can inspect the raw cosine independently of the blend.
 */
data class Scored<out T>(
    val id: T,
    val score: Float,
    val relevance: Float,
    val rank: Int,
)

/**
 * An explicit, immutable snapshot of every knob the [Reranker] uses, so `rank` is a **pure, replayable
 * function of (taste, candidates, params)**. No weights are learned; [DEFAULT] documents sensible
 * values. Weights need not sum to 1 — they are relative contributions to the additive score.
 *
 * @param relevanceWeight   weight on cosine(candidate, taste) — the "how much does this match my taste"
 *                          term. The dominant term by default.
 * @param noveltyWeight     weight on novelty = `1 − cosine(candidate, taste)`, rewarding picks a little
 *                          off the obvious center so the feed isn't only the nearest neighbours.
 * @param explorationWeight weight on a Thompson-style uncertainty bonus (see below) that occasionally
 *                          surfaces less-certain candidates and fights filter-bubble collapse.
 * @param diversityWeight   MMR strength: how hard to penalize a candidate that is too close (max cosine)
 *                          to an already-selected result, applied greedily during selection.
 * @param antiRepetitionWeight  weight on the penalty for a recently-served id (down-weights repeats).
 * @param explorationPriorPlays a Bayesian prior "plays" added to each candidate's served count so a
 *                          brand-new candidate's uncertainty bonus is finite (avoids div-by-zero) and
 *                          the bonus decays as a candidate is served more (`sqrt(ln(total)/served)`).
 * @param seed              seed for the deterministic per-candidate exploration jitter, so the same
 *                          inputs always produce the same ranking (replayable) yet exploration still
 *                          varies run-to-run when the caller varies the seed.
 */
data class RerankParams(
    val relevanceWeight: Float = 1.0f,
    val noveltyWeight: Float = 0.15f,
    val explorationWeight: Float = 0.10f,
    val diversityWeight: Float = 0.25f,
    val antiRepetitionWeight: Float = 0.50f,
    val explorationPriorPlays: Float = 1.0f,
    val seed: Long = 0L,
) {
    companion object {
        /** Documented sensible defaults: relevance dominates, with gentle novelty/exploration/diversity. */
        val DEFAULT = RerankParams()
    }
}

/**
 * The pure, framework-free personalization reranker. It turns a raw candidate set (already restricted
 * to the taste's neighbourhood by the kNN caller, or the whole library) into a *mixed* feed by blending
 * five terms into an additive score, then selecting greedily with MMR diversity:
 *
 *  score(c) = w_rel·relevance(c) + w_nov·novelty(c) + w_exp·exploration(c)
 *             − w_div·maxSimToSelected(c) − w_rep·recencyPenalty(c)
 *
 *  - **relevance**  = cosine(c, taste) — the core "matches my taste" term (== dot for unit vectors).
 *  - **novelty**    = 1 − cosine(c, taste) — rewards picks slightly off the obvious center.
 *  - **exploration**= a Thompson/UCB-style `sqrt(ln(totalServed+e)/(served+prior))` uncertainty bonus,
 *                     jittered by a deterministic per-(id,seed) factor so it is bounded, reproducible
 *                     for a fixed seed, yet surfaces different long-tail picks as the seed changes.
 *  - **diversity**  = MMR: during greedy selection each remaining candidate is penalized by its max
 *                     cosine to the already-selected set, so near-duplicates don't stack up.
 *  - **anti-repetition** = a decaying penalty for ids served recently (see [servedRecency]).
 *
 * Everything is deterministic given the same [params] (incl. its seed) and inputs.
 */
object Reranker {

    /**
     * Reranks [candidates] against [taste], returning up to [limit] [Scored] results, best first.
     *
     * @param taste          the L2-normalized taste vector, or null when cold — with no taste the score
     *                       collapses to exploration − diversity − anti-repetition, i.e. a diverse,
     *                       repetition-avoiding shuffle rather than a personalized ranking.
     * @param candidates     the pool to rerank; embeddings of a different dimension than [taste] are
     *                       scored with zero relevance/novelty (they still get exploration/diversity).
     * @param servedRecency  id → recency weight in `(0,1]` of how recently/heavily it was served (1 =
     *                       just served, decaying toward 0); absent ids are treated as 0 (never served).
     * @param servedCounts   id → total times served (drives the exploration uncertainty term).
     * @param params         the immutable scoring snapshot (weights + seed). Defaults to [RerankParams.DEFAULT].
     */
    fun <T : Comparable<T>> rank(
        taste: FloatArray?,
        candidates: List<Candidate<T>>,
        servedRecency: Map<T, Float> = emptyMap(),
        servedCounts: Map<T, Int> = emptyMap(),
        limit: Int,
        params: RerankParams = RerankParams.DEFAULT,
    ): List<Scored<T>> {
        if (limit <= 0 || candidates.isEmpty()) return emptyList()
        val dim = taste?.size
        val totalServed = servedCounts.values.sumOf { it.toLong() }.toDouble()

        // Precompute the per-candidate, selection-independent portion of the score once.
        data class Pre<T>(val cand: Candidate<T>, val relevance: Float, val base: Float)

        val pre = ArrayList<Pre<T>>(candidates.size)
        for (c in candidates) {
            val relevance = if (dim != null && c.embedding.size == dim) dot(taste, c.embedding) else 0f
            val novelty = if (dim != null && c.embedding.size == dim) 1f - relevance else 0f
            val exploration = explorationBonus(
                id = c.id,
                served = servedCounts[c.id] ?: 0,
                totalServed = totalServed,
                prior = params.explorationPriorPlays,
                seed = params.seed,
            )
            val recencyPenalty = servedRecency[c.id] ?: 0f
            val base = params.relevanceWeight * relevance +
                params.noveltyWeight * novelty +
                params.explorationWeight * exploration -
                params.antiRepetitionWeight * recencyPenalty
            pre += Pre(c, relevance, base)
        }

        // Greedy MMR selection: repeatedly take the best (base − diversity·maxSimToSelected). This is
        // O(n·k) which is fine for a reranked shortlist. Ties break deterministically by id.
        val remaining = ArrayList(pre)
        val selected = ArrayList<Scored<T>>(minOf(limit, remaining.size))
        val selectedEmbeddings = ArrayList<FloatArray>()

        val target = minOf(limit, remaining.size)
        while (selected.size < target && remaining.isNotEmpty()) {
            var bestIdx = -1
            var bestScore = Float.NEGATIVE_INFINITY
            var bestId: T? = null
            for (i in remaining.indices) {
                val p = remaining[i]
                val maxSim = maxCosine(p.cand.embedding, selectedEmbeddings, dim)
                val score = p.base - params.diversityWeight * maxSim
                val currentBestId = bestId
                val better = score > bestScore ||
                    (score == bestScore && (currentBestId == null || p.cand.id < currentBestId))
                if (better) {
                    bestScore = score
                    bestIdx = i
                    bestId = p.cand.id
                }
            }
            val chosen = remaining.removeAt(bestIdx)
            selected += Scored(
                id = chosen.cand.id,
                score = bestScore,
                relevance = chosen.relevance,
                rank = selected.size,
            )
            selectedEmbeddings += chosen.cand.embedding
        }
        return selected
    }

    /**
     * A Thompson/UCB-style exploration bonus in `[0, ~1]`: high for candidates served few times (high
     * uncertainty → worth trying), decaying as `sqrt(ln(totalServed)/served)` as a candidate accrues
     * plays. A deterministic per-(id, seed) jitter in `[0.5, 1]` breaks the symmetry between equally
     * unseen candidates so exploration doesn't always surface the same one, while staying reproducible
     * for a fixed seed. Bounded and never negative.
     */
    private fun <T> explorationBonus(
        id: T,
        served: Int,
        totalServed: Double,
        prior: Float,
        seed: Long,
    ): Float {
        val n = served + prior.toDouble().coerceAtLeast(1e-3)
        // +e so the log is >= 1 even before anything has been served (bonus stays finite & positive).
        val confidence = sqrt(ln(totalServed + Math.E) / n)
        // Deterministic jitter in [0.5, 1] from a hash of (id, seed) — reproducible, seed-varying.
        val jitter = 0.5f + 0.5f * hashUnit(id, seed)
        // Squash into a bounded, gentle bonus.
        return (confidence.toFloat() * jitter).coerceIn(0f, 1f)
    }

    /** The max cosine of [v] against any of [others] (0 when empty / dimension-mismatched). */
    private fun maxCosine(v: FloatArray, others: List<FloatArray>, dim: Int?): Float {
        if (others.isEmpty() || dim == null || v.size != dim) return 0f
        var best = 0f
        for (o in others) {
            if (o.size != dim) continue
            val s = dot(v, o)
            if (s > best) best = s
        }
        return best
    }

    /** A deterministic value in `[0,1)` from a hash of ([id], [seed]) — the exploration jitter source. */
    private fun <T> hashUnit(id: T, seed: Long): Float {
        var h = seed * -0x61c8864680b583ebL // Fibonacci-hash mixer
        h = h xor (id.hashCode().toLong() * -0x7ee3623a03d3c1c1L)
        h = h xor (h ushr 32)
        // Map the low 24 bits into [0,1).
        val bits = (h ushr 8).toInt() and 0xFFFFFF
        return bits / 16_777_216f
    }

    /** Dot product of two equal-length vectors (== cosine for unit-norm inputs). */
    private fun dot(a: FloatArray, b: FloatArray): Float {
        var acc = 0.0
        val n = minOf(a.size, b.size)
        for (i in 0 until n) acc += a[i].toDouble() * b[i]
        return acc.toFloat()
    }
}
