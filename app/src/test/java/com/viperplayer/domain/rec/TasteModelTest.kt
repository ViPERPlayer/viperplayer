package com.viperplayer.domain.rec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * Pure-JVM tests for the online [TasteModel]: cold-start rebuild (recency × engagement weighting, likes
 * weighted strongly, empty → cold), and the online [TasteModel.update] nudge (like pulls toward, skip
 * pushes away, saturation damps a maxed component, result stays unit-norm, cold-start seeding).
 */
class TasteModelTest {

    private val dim = 8

    /** A unit vector along axis [axis] (all vectors here live in a small orthonormal-ish space). */
    private fun axis(axis: Int, sign: Float = 1f): FloatArray =
        FloatArray(dim) { if (it == axis) sign else 0f }

    /** L2-normalize a raw vector. */
    private fun unit(vararg v: Float): FloatArray {
        val padded = FloatArray(dim) { if (it < v.size) v[it] else 0f }
        var n = 0.0
        for (x in padded) n += x.toDouble() * x
        val inv = (1.0 / sqrt(n)).toFloat()
        return FloatArray(dim) { padded[it] * inv }
    }

    private fun cos(a: FloatArray, b: FloatArray): Float {
        var acc = 0.0
        for (i in a.indices) acc += a[i].toDouble() * b[i]
        return acc.toFloat()
    }

    private fun norm(v: FloatArray): Float {
        var n = 0.0
        for (x in v) n += x.toDouble() * x
        return sqrt(n).toFloat()
    }

    private val now = 1_000_000_000_000L
    private fun daysAgo(d: Long): Long = now - d * 24L * 60 * 60 * 1000

    // --- deriveTaste --------------------------------------------------------------------------------

    @Test
    fun deriveTaste_emptyInputs_returnsCold() {
        val taste = TasteModel.deriveTaste(emptyList(), emptyList(), nowMs = now)
        assertTrue(taste.isCold)
        assertNull(taste.vector)
    }

    @Test
    fun deriveTaste_producesUnitVectorTowardCompletedPlays() {
        val plays = listOf(
            PlayRecord(axis(0), timestampMs = daysAgo(0), completion = 1f),
            PlayRecord(axis(0), timestampMs = daysAgo(1), completion = 1f),
        )
        val vector = TasteModel.deriveTaste(plays, emptyList(), nowMs = now).vector
        assertNotNull(vector)
        assertEquals("taste is unit-norm", 1f, norm(vector!!), 1e-4f)
        // All plays point along axis 0 → taste points along axis 0.
        assertTrue(cos(vector, axis(0)) > 0.99f)
    }

    @Test
    fun deriveTaste_recencyWeightsRecentPlaysMore() {
        // A very recent play on axis 0 vs an ancient play on axis 1 → taste tilts toward axis 0.
        val plays = listOf(
            PlayRecord(axis(0), timestampMs = daysAgo(0), completion = 1f),
            PlayRecord(axis(1), timestampMs = daysAgo(120), completion = 1f),
        )
        val taste = TasteModel.deriveTaste(plays, emptyList(), nowMs = now).vector!!
        assertTrue(
            "recent axis-0 play dominates the ancient axis-1 play",
            cos(taste, axis(0)) > cos(taste, axis(1)),
        )
    }

    @Test
    fun deriveTaste_engagementWeightsCompletedOverSkipped() {
        // Fully-listened axis 0 vs barely-listened (skip) axis 1, same recency → taste toward axis 0.
        val plays = listOf(
            PlayRecord(axis(0), timestampMs = daysAgo(0), completion = 1f),
            PlayRecord(axis(1), timestampMs = daysAgo(0), completion = 0.02f),
        )
        val taste = TasteModel.deriveTaste(plays, emptyList(), nowMs = now).vector!!
        assertTrue("completed play dominates the skip", cos(taste, axis(0)) > 0.9f)
        // The near-skip contributes a small NEGATIVE weight → taste points slightly AWAY from axis 1.
        assertTrue("skip pushes away from its axis", cos(taste, axis(1)) < 0f)
    }

    @Test
    fun deriveTaste_likesWeightedStronglyOverASinglePlay() {
        // One liked song on axis 1 outweighs a single full play on axis 0 (liked multiplier ≫ 1).
        val plays = listOf(PlayRecord(axis(0), timestampMs = daysAgo(0), completion = 1f))
        val liked = listOf(axis(1))
        val taste = TasteModel.deriveTaste(plays, liked, nowMs = now).vector!!
        assertTrue("liked axis dominates", cos(taste, axis(1)) > cos(taste, axis(0)))
    }

    @Test
    fun deriveTaste_degenerateCancellingInputs_returnsCold() {
        // Two equal-weight opposite plays cancel to a zero centroid → cold (no direction).
        val plays = listOf(
            PlayRecord(axis(0, +1f), timestampMs = daysAgo(0), completion = 1f),
            PlayRecord(axis(0, -1f), timestampMs = daysAgo(0), completion = 1f),
        )
        val taste = TasteModel.deriveTaste(plays, emptyList(), nowMs = now)
        assertTrue(taste.isCold)
    }

    // --- update -------------------------------------------------------------------------------------

    @Test
    fun update_cold_positiveInteractionSeedsTaste() {
        val next = TasteModel.update(
            TasteState.COLD,
            Interaction(InteractionType.LIKE, axis(0)),
            nowMs = now,
        )
        assertFalse(next.isCold)
        val vector = next.vector!!
        assertEquals(1f, norm(vector), 1e-4f)
        assertTrue(cos(vector, axis(0)) > 0.99f)
        assertEquals(1, next.interactions)
    }

    @Test
    fun update_cold_negativeInteractionIsNoOp() {
        val next = TasteModel.update(
            TasteState.COLD,
            Interaction(InteractionType.SKIP, axis(0), completion = 0f),
            nowMs = now,
        )
        assertTrue("nothing to push away from when cold", next.isCold)
    }

    @Test
    fun update_likeMovesTasteTowardTheSong() {
        val start = TasteState(vector = axis(0), rebuiltAtMs = now)
        val target = unit(1f, 1f) // 45deg from axis 0
        val before = cos(start.vector!!, target)
        val after = cos(
            TasteModel.update(start, Interaction(InteractionType.LIKE, target), nowMs = now).vector!!,
            target,
        )
        assertTrue("a like increases similarity to the liked song", after > before)
    }

    @Test
    fun update_skipMovesTasteAwayFromTheSong() {
        val start = TasteState(vector = unit(1f, 1f), rebuiltAtMs = now)
        val skipped = axis(0)
        val before = cos(start.vector!!, skipped)
        val after = cos(
            TasteModel.update(
                start,
                Interaction(InteractionType.SKIP, skipped, completion = 0f),
                nowMs = now,
            ).vector!!,
            skipped,
        )
        assertTrue("a skip decreases similarity to the skipped song", after < before)
    }

    @Test
    fun update_completeScalesWithCompletion() {
        val start = TasteState(vector = axis(0), rebuiltAtMs = now)
        val target = unit(1f, 1f)
        val small = cos(
            TasteModel.update(
                start, Interaction(InteractionType.COMPLETE, target, completion = 0.6f), nowMs = now,
            ).vector!!,
            target,
        )
        val big = cos(
            TasteModel.update(
                start, Interaction(InteractionType.COMPLETE, target, completion = 1.0f), nowMs = now,
            ).vector!!,
            target,
        )
        assertTrue("a more-complete play moves taste more", big > small)
    }

    @Test
    fun update_alwaysRenormalizesToUnit() {
        var state = TasteState(vector = axis(0), rebuiltAtMs = now)
        val target = unit(1f, 2f, -1f)
        repeat(20) {
            state = TasteModel.update(state, Interaction(InteractionType.LIKE, target), nowMs = now)
            assertEquals("stays unit-norm through repeated updates", 1f, norm(state.vector!!), 1e-3f)
        }
    }

    @Test
    fun update_saturationDampsAMaxedComponent() {
        // Taste is already fully saturated on axis 0 (== the target). A further like toward axis 0 can't
        // push component 0 past 1.0 (room = 1 − |t| = 0), so the vector is unchanged.
        val start = TasteState(vector = axis(0), rebuiltAtMs = now)
        val nextVector = TasteModel.update(start, Interaction(InteractionType.LIKE, axis(0)), nowMs = now).vector!!
        assertEquals("a maxed component doesn't grow", 1f, nextVector[0], 1e-5f)
        assertTrue(cos(nextVector, axis(0)) > 0.999f)
    }

    @Test
    fun update_convergesTowardRepeatedlyLikedCluster() {
        // Repeatedly liking a cluster direction pulls taste to it (online-learning convergence).
        var state = TasteState(vector = axis(0), rebuiltAtMs = now)
        val cluster = unit(0f, 1f, 1f)
        repeat(30) {
            state = TasteModel.update(state, Interaction(InteractionType.LIKE, cluster), nowMs = now)
        }
        assertTrue("taste converges to the liked cluster", cos(state.vector!!, cluster) > 0.95f)
    }

    @Test
    fun update_isDeterministic() {
        val start = TasteState(vector = axis(0), rebuiltAtMs = now)
        val i = Interaction(InteractionType.LIKE, unit(1f, 1f))
        val a = TasteModel.update(start, i, nowMs = now)
        val b = TasteModel.update(start, i, nowMs = now)
        assertEquals(a, b)
    }

    @Test
    fun update_nonFiniteEmbedding_leavesTasteUnchanged() {
        // A corrupt (NaN) embedding must NOT poison the taste — normalizeOrNull rejects a non-finite
        // norm, so update returns the current state rather than an all-NaN "unit" vector.
        val start = TasteState(vector = axis(0), rebuiltAtMs = now)
        val nan = FloatArray(start.vector!!.size) { Float.NaN }
        val next = TasteModel.update(start, Interaction(InteractionType.LIKE, nan), nowMs = now)
        assertEquals("NaN embedding is a no-op", start, next)
    }

    @Test
    fun deriveTaste_nonFiniteEmbeddings_returnsCold() {
        // A rebuild fed only non-finite embeddings must degrade to COLD, not a NaN taste vector.
        val nan = PlayRecord(FloatArray(3) { Float.NaN }, timestampMs = now, completion = 1f)
        assertTrue(TasteModel.deriveTaste(listOf(nan), emptyList(), nowMs = now).isCold)
    }

    // --- TimeBucket (P4) ----------------------------------------------------------------------------

    @Test
    fun timeBucket_mapsLocalHourRangesDeterministically() {
        assertEquals(TimeBucket.NIGHT, TimeBucket.fromHour(0))
        assertEquals(TimeBucket.NIGHT, TimeBucket.fromHour(4))
        assertEquals(TimeBucket.MORNING, TimeBucket.fromHour(5))
        assertEquals(TimeBucket.MORNING, TimeBucket.fromHour(11))
        assertEquals(TimeBucket.AFTERNOON, TimeBucket.fromHour(12))
        assertEquals(TimeBucket.AFTERNOON, TimeBucket.fromHour(16))
        assertEquals(TimeBucket.EVENING, TimeBucket.fromHour(17))
        assertEquals(TimeBucket.EVENING, TimeBucket.fromHour(21))
        assertEquals(TimeBucket.NIGHT, TimeBucket.fromHour(22))
        assertEquals(TimeBucket.NIGHT, TimeBucket.fromHour(23))
    }

    @Test
    fun timeBucket_wrapsOutOfRangeHours() {
        // Out-of-range hours wrap mod 24 (25 -> 1 -> NIGHT; -1 -> 23 -> NIGHT).
        assertEquals(TimeBucket.fromHour(1), TimeBucket.fromHour(25))
        assertEquals(TimeBucket.fromHour(23), TimeBucket.fromHour(-1))
    }

    // --- deriveMoodCentroids (P4) -------------------------------------------------------------------

    @Test
    fun moodCentroids_emptyInput_returnsEmpty() {
        assertTrue(TasteModel.deriveMoodCentroids(emptyList(), k = 3).isEmpty())
    }

    @Test
    fun moodCentroids_singleDirection_returnsOneCentroidTowardIt() {
        val points = List(5) { axis(0) }
        val centroids = TasteModel.deriveMoodCentroids(points, k = 3)
        assertEquals("collapses to a single centroid for one direction", 1, centroids.size)
        assertEquals("centroid is unit-norm", 1f, norm(centroids[0]), 1e-4f)
        assertTrue(cos(centroids[0], axis(0)) > 0.99f)
    }

    @Test
    fun moodCentroids_separatesTwoClusters() {
        // Two well-separated clusters (axis 0 and axis 3) → two centroids, one aligned to each.
        val points = List(4) { axis(0) } + List(4) { axis(3) }
        val centroids = TasteModel.deriveMoodCentroids(points, k = 2)
        assertEquals(2, centroids.size)
        // Each cluster axis is captured by exactly one centroid at high cosine.
        val toAxis0 = centroids.maxOf { cos(it, axis(0)) }
        val toAxis3 = centroids.maxOf { cos(it, axis(3)) }
        assertTrue("a centroid aligns to cluster 0", toAxis0 > 0.99f)
        assertTrue("a centroid aligns to cluster 3", toAxis3 > 0.99f)
        for (c in centroids) assertEquals("centroids are unit-norm", 1f, norm(c), 1e-4f)
    }

    @Test
    fun moodCentroids_fewerDistinctThanK_returnsFewerCentroids() {
        // Only 2 distinct directions but K=3 → at most 2 centroids (guards the degenerate case).
        val points = List(3) { axis(0) } + List(3) { axis(1) }
        val centroids = TasteModel.deriveMoodCentroids(points, k = 3)
        assertTrue("no more centroids than distinct clusters", centroids.size <= 2)
        assertTrue(centroids.isNotEmpty())
    }

    @Test
    fun moodCentroids_isDeterministic() {
        val points = List(4) { axis(0) } + List(4) { axis(2) } + List(4) { axis(5) }
        val a = TasteModel.deriveMoodCentroids(points, k = 3)
        val b = TasteModel.deriveMoodCentroids(points, k = 3)
        assertEquals(a.size, b.size)
        for (i in a.indices) assertTrue("same centroid $i", cos(a[i], b[i]) > 0.9999f)
    }

    @Test
    fun moodCentroids_skipsNonFiniteAndWrongDim() {
        // A NaN row and a wrong-dimension row are skipped; the clean axis-0 rows still yield a centroid.
        val nan = FloatArray(dim) { Float.NaN }
        val wrongDim = FloatArray(dim + 1) { 1f }
        val centroids = TasteModel.deriveMoodCentroids(listOf(axis(0), nan, wrongDim, axis(0)), k = 2)
        assertEquals(1, centroids.size)
        assertTrue(cos(centroids[0], axis(0)) > 0.99f)
    }
}
