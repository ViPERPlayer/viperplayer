package com.viperplayer.domain.rec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * Pure-JVM tests for [Reranker]: relevance ordering, MMR actually diversifying a near-duplicate set, a
 * bounded + deterministic (seed-stable) exploration bonus, anti-repetition down-weighting served ids,
 * and the params snapshot making `rank` a pure/replayable function.
 */
class RerankerTest {

    private val dim = 6

    private fun unit(vararg v: Float): FloatArray {
        val padded = FloatArray(dim) { if (it < v.size) v[it] else 0f }
        var n = 0.0
        for (x in padded) n += x.toDouble() * x
        val inv = (1.0 / sqrt(n)).toFloat()
        return FloatArray(dim) { padded[it] * inv }
    }

    private fun cand(id: Long, vararg v: Float) = Candidate(id, unit(*v))

    // --- relevance ----------------------------------------------------------------------------------

    @Test
    fun rank_ordersByRelevanceToTaste_withDefaultsAndNoServedHistory() {
        val taste = unit(1f, 0f)
        val candidates = listOf(
            cand(1L, 0f, 1f),   // orthogonal (worst)
            cand(2L, 1f, 0f),   // aligned (best)
            cand(3L, 1f, 0.5f), // close
        )
        val ranked = Reranker.rank(taste, candidates, limit = 3, params = RerankParams.DEFAULT)
        assertEquals(listOf(2L, 3L, 1L), ranked.map { it.id })
        // The top result's relevance is ~1.0 (identical to taste).
        assertEquals(1f, ranked.first().relevance, 1e-4f)
        // Ranks are 0-based, best first.
        assertEquals(listOf(0, 1, 2), ranked.map { it.rank })
    }

    @Test
    fun rank_coldTaste_stillReturnsAllCandidatesBounded() {
        // No taste → relevance/novelty are 0; only exploration/diversity/anti-repetition apply. All
        // candidates still come back (just not personalized).
        val candidates = listOf(cand(1L, 1f, 0f), cand(2L, 0f, 1f), cand(3L, 1f, 1f))
        val ranked = Reranker.rank(taste = null, candidates = candidates, limit = 3)
        assertEquals(setOf(1L, 2L, 3L), ranked.map { it.id }.toSet())
        assertEquals(0f, ranked.first().relevance, 0f)
    }

    // --- MMR diversity ------------------------------------------------------------------------------

    @Test
    fun rank_mmrDiversifiesNearDuplicates() {
        // A tight cluster of three near-identical vectors (all sharing the +y component) plus one
        // slightly-less-relevant but ANGULARLY DISTINCT vector (+z instead of +y). Pure relevance ranks
        // the distinct one last; MMR — seeing the cluster is redundant — should surface it early instead.
        val taste = unit(1f, 0f, 0f)
        val candidates = listOf(
            cand(1L, 1f, 0.30f, 0f),  // cluster
            cand(2L, 1f, 0.32f, 0f),  // cluster (near-duplicate of 1)
            cand(3L, 1f, 0.34f, 0f),  // cluster (near-duplicate of 1)
            cand(4L, 1f, 0f, 0.45f),  // distinct direction, slightly lower relevance
        )
        val strongMmr = RerankParams(diversityWeight = 1.0f, noveltyWeight = 0f, explorationWeight = 0f)
        val ranked = Reranker.rank(taste, candidates, limit = 4, params = strongMmr).map { it.id }
        // The distinct candidate is pulled up ahead of the redundant cluster members.
        assertTrue("distinct pick beats the near-duplicates", ranked.indexOf(4L) < ranked.indexOf(3L))
        assertTrue("distinct pick beats the near-duplicate #2", ranked.indexOf(4L) < ranked.indexOf(2L))

        // Sanity: with NO diversity pressure pure relevance rules and the distinct one sinks to last.
        val noMmr = RerankParams(diversityWeight = 0f, noveltyWeight = 0f, explorationWeight = 0f)
        val plain = Reranker.rank(taste, candidates, limit = 4, params = noMmr).map { it.id }
        assertEquals(listOf(1L, 2L, 3L, 4L), plain)
    }

    // --- exploration --------------------------------------------------------------------------------

    @Test
    fun rank_explorationBonusIsBoundedAndDeterministicForAFixedSeed() {
        val taste = unit(1f, 0f)
        val candidates = (1L..20L).map { cand(it, 1f, 0f) }
        val params = RerankParams(seed = 42L)
        val a = Reranker.rank(taste, candidates, limit = 20, params = params)
        val b = Reranker.rank(taste, candidates, limit = 20, params = params)
        // Deterministic: identical inputs (incl. seed) → identical ranking + scores.
        assertEquals(a.map { it.id }, b.map { it.id })
        assertEquals(a.map { it.score }, b.map { it.score })
        // Bounded: exploration can't send the score to infinity/NaN — all scores are finite.
        assertTrue(a.all { it.score.isFinite() })
    }

    @Test
    fun rank_explorationVariesWithSeed() {
        // All candidates identical in every other respect → only the seeded exploration jitter can
        // reorder them, so a different seed should generally produce a different head-of-list.
        val taste = unit(1f, 0f)
        val candidates = (1L..30L).map { cand(it, 1f, 0f) }
        val strongExp = { s: Long -> RerankParams(relevanceWeight = 0f, explorationWeight = 1f, seed = s) }
        val withSeed1 = Reranker.rank(taste, candidates, limit = 30, params = strongExp(1L)).map { it.id }
        val withSeed2 = Reranker.rank(taste, candidates, limit = 30, params = strongExp(2L)).map { it.id }
        assertNotEquals("different seeds explore different orderings", withSeed1, withSeed2)
    }

    @Test
    fun rank_explorationFavoursLessServedCandidates() {
        // Two equally-relevant candidates; #1 has been served many times, #2 never. Exploration should
        // surface the unseen #2 first when the exploration term dominates.
        val taste = unit(1f, 0f)
        val candidates = listOf(cand(1L, 1f, 0f), cand(2L, 1f, 0f))
        val params = RerankParams(relevanceWeight = 0.001f, explorationWeight = 1f, seed = 0L)
        val ranked = Reranker.rank(
            taste = taste,
            candidates = candidates,
            servedCounts = mapOf(1L to 100),
            limit = 2,
            params = params,
        ).map { it.id }
        assertEquals("the never-served candidate is explored first", 2L, ranked.first())
    }

    // --- anti-repetition ----------------------------------------------------------------------------

    @Test
    fun rank_antiRepetitionDownWeightsRecentlyServedIds() {
        val taste = unit(1f, 0f)
        val candidates = listOf(cand(1L, 1f, 0f), cand(2L, 1f, 0f))
        // #1 is more relevant-tied but was just served (recency 1.0) → penalized below the fresh #2.
        val params = RerankParams(explorationWeight = 0f, antiRepetitionWeight = 1f)
        val ranked = Reranker.rank(
            taste = taste,
            candidates = candidates,
            servedRecency = mapOf(1L to 1f),
            limit = 2,
            params = params,
        ).map { it.id }
        assertEquals("recently-served id sinks below the fresh one", listOf(2L, 1L), ranked)
    }

    // --- purity / snapshot --------------------------------------------------------------------------

    @Test
    fun rank_isPureFunctionOfParamsSnapshot() {
        val taste = unit(1f, 0f)
        val candidates = listOf(cand(1L, 1f, 0f), cand(2L, 0f, 1f), cand(3L, 1f, 1f))
        val p = RerankParams(seed = 7L, noveltyWeight = 0.2f, diversityWeight = 0.3f)
        val first = Reranker.rank(taste, candidates, limit = 3, params = p)
        // Re-run with the SAME snapshot → identical result (no hidden state).
        val again = Reranker.rank(taste, candidates, limit = 3, params = p)
        assertEquals(first, again)
        // A different snapshot is free to differ; changing novelty here reorders relative to relevance.
        val diff = Reranker.rank(taste, candidates, limit = 3, params = p.copy(noveltyWeight = 5f))
        assertNotEquals(first.map { it.id }, diff.map { it.id })
    }

    @Test
    fun rank_nonFiniteEmbeddingsDoNotThrow() {
        // A corrupt embedding (NaN component) makes every blended score NaN; NaN comparisons are always
        // false, so the greedy selector can find no best candidate. It must stop gracefully rather than
        // removeAt(-1). We accept any bounded result (possibly empty) as long as it doesn't throw.
        val taste = unit(1f, 0f)
        val nan = FloatArray(dim) { Float.NaN }
        val candidates = listOf(Candidate(1L, nan), Candidate(2L, nan))
        val ranked = Reranker.rank(taste, candidates, limit = 2, params = RerankParams.DEFAULT)
        assertTrue("non-finite scores must not blow up selection", ranked.size <= 2)
    }

    @Test
    fun rank_limitAndEmptyGuards() {
        val taste = unit(1f, 0f)
        val candidates = listOf(cand(1L, 1f, 0f), cand(2L, 0f, 1f))
        assertTrue(Reranker.rank(taste, candidates, limit = 0).isEmpty())
        assertTrue(Reranker.rank(taste, emptyList<Candidate<Long>>(), limit = 5).isEmpty())
        assertEquals(1, Reranker.rank(taste, candidates, limit = 1).size)
    }
}
