package com.viperplayer.data.rec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * Pure-JVM tests for [EmbeddingKnn] — the brute-force cosine kNN + centroid used by the local
 * recommender. All vectors here are hand-picked so the expected ranking is obvious by inspection.
 */
class EmbeddingKnnTest {

    private fun unit(vararg v: Float): FloatArray {
        var n = 0.0
        for (x in v) n += x.toDouble() * x
        val inv = (1.0 / sqrt(n)).toFloat()
        return FloatArray(v.size) { v[it] * inv }
    }

    @Test
    fun topKRanksByCosineBestFirst() {
        val query = unit(1f, 0f)
        val candidates = listOf(
            1L to unit(1f, 0f),      // identical -> score 1.0
            2L to unit(1f, 1f),      // 45deg -> ~0.707
            3L to unit(0f, 1f),      // orthogonal -> 0.0
            4L to unit(-1f, 0f),     // opposite -> -1.0
        )
        val result = EmbeddingKnn.cosineTopK(query, candidates, k = 3)
        assertEquals(listOf(1L, 2L, 3L), result.map { it.id })
        // The identical candidate scores ~1.0.
        assertEquals(1.0f, result.first().score, 1e-5f)
    }

    @Test
    fun excludeSetRemovesSeedAndDuplicates() {
        val query = unit(1f, 0f)
        val candidates = listOf(
            1L to unit(1f, 0f),   // would be top, but excluded (the seed)
            2L to unit(2f, 1f),
            3L to unit(1f, 2f),
        )
        val result = EmbeddingKnn.cosineTopK(query, candidates, k = 5, excludeIds = setOf(1L))
        assertEquals(listOf(2L, 3L), result.map { it.id })
        assertTrue(result.none { it.id == 1L })
    }

    @Test
    fun tieBreakIsDeterministicByAscendingId() {
        // Three candidates with the SAME direction -> identical scores; id order must break the tie.
        val query = unit(1f, 0f)
        val candidates = listOf(
            30L to unit(1f, 0f),
            10L to unit(1f, 0f),
            20L to unit(1f, 0f),
        )
        val result = EmbeddingKnn.cosineTopK(query, candidates, k = 3)
        assertEquals(listOf(10L, 20L, 30L), result.map { it.id })
    }

    @Test
    fun kBoundsAndEmptyInputs() {
        val query = unit(1f, 0f)
        val candidates = listOf(1L to unit(1f, 0f), 2L to unit(0f, 1f))
        assertEquals(1, EmbeddingKnn.cosineTopK(query, candidates, k = 1).size)
        assertEquals(2, EmbeddingKnn.cosineTopK(query, candidates, k = 9).size)
        assertTrue(EmbeddingKnn.cosineTopK(query, candidates, k = 0).isEmpty())
        assertTrue(EmbeddingKnn.cosineTopK(query, emptyList<Pair<Long, FloatArray>>(), k = 3).isEmpty())
    }

    @Test
    fun mismatchedDimensionCandidatesAreSkipped() {
        val query = unit(1f, 0f)
        val candidates = listOf(
            1L to floatArrayOf(1f, 0f, 0f), // wrong dim -> skipped
            2L to unit(1f, 0f),
        )
        val result = EmbeddingKnn.cosineTopK(query, candidates, k = 5)
        assertEquals(listOf(2L), result.map { it.id })
    }

    @Test
    fun meanVectorIsRenormalizedToUnitLength() {
        // Two unit vectors 90deg apart; their mean points at 45deg and, after renorm, is unit length.
        val mean = EmbeddingKnn.meanVector(listOf(unit(1f, 0f), unit(0f, 1f)))!!
        var norm = 0.0
        for (x in mean) norm += x.toDouble() * x
        assertEquals(1.0f, sqrt(norm).toFloat(), 1e-5f)
        // Symmetric inputs -> equal components.
        assertEquals(mean[0], mean[1], 1e-5f)
    }

    @Test
    fun meanVectorNullWhenEmptyOrDegenerate() {
        assertNull(EmbeddingKnn.meanVector(emptyList()))
        // Antipodal unit vectors cancel to the zero vector -> no meaningful centroid.
        assertNull(EmbeddingKnn.meanVector(listOf(unit(1f, 0f), unit(-1f, 0f))))
    }

    @Test
    fun meanVectorSkipsMismatchedDimensions() {
        // The 3-d vector is skipped; the centroid is formed from the two 2-d vectors only.
        val mean = EmbeddingKnn.meanVector(
            listOf(unit(1f, 0f), floatArrayOf(1f, 1f, 1f), unit(1f, 0f))
        )!!
        assertEquals(2, mean.size)
        assertEquals(1.0f, mean[0], 1e-5f)
    }
}
