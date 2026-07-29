package com.viperplayer.domain.rec

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The kind of user interaction feeding the online taste model, ordered loosely by how strongly it
 * moves the taste vector (LIKE ≫ COMPLETE > CLICK ≫ SKIP/DISLIKE). Each maps to a signed learning
 * rate in [TasteModel] — positive interactions pull the taste toward a song's embedding, negative
 * ones push it away. See [TasteModel.update].
 */
enum class InteractionType {
    /** The user liked the song — the strongest positive signal. */
    LIKE,

    /** The user un-liked the song — a mild negative correction. */
    UNLIKE,

    /** The song played (near) to completion — a strong-ish positive signal, scaled by completion. */
    COMPLETE,

    /** The song was opened / started but not really listened to — a small positive signal. */
    CLICK,

    /** The song was skipped after a very short listen — a negative signal. */
    SKIP,

    /** The user explicitly disliked/blocked the song — a strong negative signal. */
    DISLIKE,
}

/**
 * A coarse time-of-day bucket the taste is additionally learned per-bucket (P4). Each bucket keeps its
 * OWN taste vector alongside the global one, so recommendations can lean on how the user's taste shifts
 * across the day (e.g. mellow mornings, energetic evenings). Buckets are derived purely from a
 * local-clock hour ([fromHour]) so they stay deterministic under a fake clock in tests.
 */
enum class TimeBucket {
    /** 05:00–11:59 local. */
    MORNING,

    /** 12:00–16:59 local. */
    AFTERNOON,

    /** 17:00–21:59 local. */
    EVENING,

    /** 22:00–04:59 local (wraps midnight). */
    NIGHT;

    companion object {
        /**
         * The bucket for a local-clock [hour] (0–23, values outside are wrapped mod 24). Pure and
         * deterministic — the repository resolves the local hour from the injected clock and passes it in.
         */
        fun fromHour(hour: Int): TimeBucket {
            val h = ((hour % 24) + 24) % 24
            return when (h) {
                in 5..11 -> MORNING
                in 12..16 -> AFTERNOON
                in 17..21 -> EVENING
                else -> NIGHT // 22, 23, 0..4
            }
        }
    }
}

/**
 * A single learning event: a [type] applied to a song's L2-normalized CLAP [embedding].
 *
 * [completion] is the listened/duration ratio in `[0,1]` (only meaningful for [InteractionType.COMPLETE]
 * / [InteractionType.SKIP]); it scales the completion nudge so a 95%-listened track moves the taste
 * more than a 55%-listened one. Ignored for the like/dislike types (they carry their own fixed rate).
 */
data class Interaction(
    val type: InteractionType,
    val embedding: FloatArray,
    val completion: Float = 1f,
) {
    // Value-based equals/hashCode (FloatArray is referential by default) so tests can compare events.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Interaction) return false
        return type == other.type &&
            completion == other.completion &&
            embedding.contentEquals(other.embedding)
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + embedding.contentHashCode()
        result = 31 * result + completion.hashCode()
        return result
    }
}

/**
 * A played song as seen by the cold-start taste rebuild: its L2-normalized [embedding], the wall-clock
 * [timestampMs] of the play (for recency decay), and the play's [completion] ratio in `[0,1]`
 * (listened / duration — engagement weight; a skip has a tiny completion and thus little/negative pull).
 */
data class PlayRecord(
    val embedding: FloatArray,
    val timestampMs: Long,
    val completion: Float,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PlayRecord) return false
        return timestampMs == other.timestampMs &&
            completion == other.completion &&
            embedding.contentEquals(other.embedding)
    }

    override fun hashCode(): Int {
        var result = embedding.contentHashCode()
        result = 31 * result + timestampMs.hashCode()
        result = 31 * result + completion.hashCode()
        return result
    }
}

/**
 * The persisted, online-learned taste representation.
 *
 * [vector] is the single primary taste centroid — a 512-d L2-normalized direction in CLAP embedding
 * space that the reranker scores candidates against. It is `null` while cold (no signal yet), which
 * callers treat as "not personalized — fall back". [interactions] counts positive learning events
 * folded in so far and [rebuiltAtMs] stamps the last full rebuild, both so the repository can decide
 * when the taste is stale and worth rebuilding from history.
 *
 * This type is intentionally a plain immutable data holder — all math lives in [TasteModel] so it is
 * pure, framework-free and directly unit-testable. Persistence serializes exactly these fields.
 */
data class TasteState(
    val vector: FloatArray?,
    val interactions: Int = 0,
    val rebuiltAtMs: Long = 0L,
) {
    /** True while there is no usable taste signal yet (cold start). */
    val isCold: Boolean get() = vector == null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TasteState) return false
        if (interactions != other.interactions) return false
        if (rebuiltAtMs != other.rebuiltAtMs) return false
        return when {
            vector == null && other.vector == null -> true
            vector == null || other.vector == null -> false
            else -> vector.contentEquals(other.vector)
        }
    }

    override fun hashCode(): Int {
        var result = vector?.contentHashCode() ?: 0
        result = 31 * result + interactions
        result = 31 * result + rebuiltAtMs.hashCode()
        return result
    }

    companion object {
        /** The empty/cold taste — no vector, nothing learned. */
        val COLD = TasteState(vector = null, interactions = 0, rebuiltAtMs = 0L)
    }
}

/**
 * The pure, framework-free online taste model over CLAP audio embeddings.
 *
 * It has two entry points, both deterministic and side-effect-free:
 *  - [deriveTaste] — a cold-start / periodic **rebuild** from listening history: a recency- and
 *    engagement-weighted centroid of played (and strongly-weighted liked) songs.
 *  - [update] — an **online EMA nudge** of the current taste toward a liked/completed song or away
 *    from a skipped/disliked one, with per-interaction learning rates and a saturation penalty.
 *
 * The taste [TasteState.vector] is always kept L2-normalized so it is directly comparable to the
 * library via cosine (== dot for unit vectors). All shapes here mirror the reference online-learner
 * (per-interaction rates, saturation damping, tiered recency decay) but operate on real audio
 * embeddings rather than hand-crafted features.
 */
object TasteModel {

    // --- learning rates (per interaction) ----------------------------------------------------------
    // Signed EMA step sizes. Positive pulls taste toward the embedding, negative pushes away. Ordered
    // LIKE ≫ COMPLETE > CLICK ≫ SKIP, matching the reference learner's tiered weighting.

    /** A like is the strongest positive signal. */
    const val RATE_LIKE = 0.30f

    /** A completed play, further scaled by the completion ratio (so a full listen ≈ half a like). */
    const val RATE_COMPLETE = 0.15f

    /** Opening/starting a track: a gentle positive nudge. */
    const val RATE_CLICK = 0.03f

    /** A skip (very short listen): a negative correction, scaled by how little was listened. */
    const val RATE_SKIP = -0.15f

    /** Un-liking: a mild negative correction (weaker than an explicit dislike). */
    const val RATE_UNLIKE = -0.10f

    /** An explicit dislike/block: a strong negative correction. */
    const val RATE_DISLIKE = -0.30f

    // --- recency decay -----------------------------------------------------------------------------

    /** Half-life for the exponential recency decay used by [deriveTaste], in days. */
    const val RECENCY_HALF_LIFE_DAYS = 30.0

    /** Extra multiplicative weight a liked song gets in the rebuild centroid over a plain play. */
    const val LIKED_WEIGHT_MULTIPLIER = 4.0f

    /**
     * A play whose completion ratio is at or below this is treated as a skip in the rebuild and
     * contributes a *negative* weight, pulling the centroid slightly away from disliked-sounding tracks.
     */
    const val SKIP_COMPLETION_THRESHOLD = 0.15f

    /** Default number of mood centroids (clusters) to derive — a small K keeps clustering cheap. */
    const val DEFAULT_MOOD_CENTROIDS = 3

    /** Max k-means iterations for [deriveMoodCentroids] — converges quickly on a small, capped input. */
    private const val KMEANS_MAX_ITERS = 20

    private const val MS_PER_DAY = 24.0 * 60.0 * 60.0 * 1000.0

    /**
     * Builds (or rebuilds) a taste centroid from listening history — the cold-start path and the
     * periodic refresh. Each play is weighted by **recency** (exponential half-life decay over its age
     * relative to [nowMs]) × **engagement** (its completion ratio, re-centered so a near-skip gets a
     * small negative weight — see [SKIP_COMPLETION_THRESHOLD]); liked songs get an additional strong
     * multiplier ([LIKED_WEIGHT_MULTIPLIER]). The weighted sum is L2-normalized to a unit taste vector.
     *
     * @param plays            recency/engagement-weighted play history (any order); embeddings must be
     *                         the same dimension.
     * @param likedEmbeddings  embeddings of currently-liked songs (weighted strongly, recency-agnostic).
     * @param nowMs            the reference "now" for recency decay.
     *
     * Returns a taste with a unit [TasteState.vector], or [TasteState.COLD] when there is no usable
     * signal (empty inputs, or a degenerate all-zero / cancelling centroid) — callers then fall back to
     * the non-personalized path. Never throws.
     */
    fun deriveTaste(
        plays: List<PlayRecord>,
        likedEmbeddings: List<FloatArray>,
        nowMs: Long,
    ): TasteState {
        val dim = plays.firstOrNull()?.embedding?.size
            ?: likedEmbeddings.firstOrNull()?.size
            ?: return TasteState.COLD
        if (dim == 0) return TasteState.COLD

        val acc = DoubleArray(dim)
        var contributed = 0

        for (play in plays) {
            if (play.embedding.size != dim) continue
            val recency = recencyWeight(play.timestampMs, nowMs)
            val engagement = engagementWeight(play.completion)
            val w = recency * engagement
            if (w == 0.0) continue
            addScaled(acc, play.embedding, w)
            contributed++
        }

        // Liked songs contribute strongly and recency-agnostically (a like is an enduring signal).
        for (v in likedEmbeddings) {
            if (v.size != dim) continue
            addScaled(acc, v, LIKED_WEIGHT_MULTIPLIER.toDouble())
            contributed++
        }

        if (contributed == 0) return TasteState.COLD
        val vector = normalizeOrNull(acc) ?: return TasteState.COLD
        return TasteState(vector = vector, interactions = 0, rebuiltAtMs = nowMs)
    }

    /**
     * Clusters the user's taste into up to [k] **mood centroids** (P4) — distinct directions in CLAP
     * space so recommendations can span several moods instead of collapsing to one averaged vector.
     *
     * A small, deterministic spherical k-means over the (L2-normalized) [embeddings]:
     *  - **Deterministic init** — no `Math.random`. The first seed is the input's own centroid direction;
     *    each subsequent seed is the input point *furthest* (lowest cosine) from the already-chosen seeds
     *    (a farthest-point / k-means++-style greedy pick with a fixed, reproducible tie-break by index).
     *  - **Assignment** by max cosine, **update** by re-normalizing each cluster's mean (spherical
     *    k-means; cosine == dot for unit vectors). Empty clusters are dropped.
     *  - **Guards** for degenerate input: no usable embeddings → empty; fewer than [k] *distinct*
     *    directions → that many centroids; a single direction → one centroid (equivalent to the single
     *    taste centroid). Non-finite / wrong-dimension rows are skipped.
     *
     * Every returned centroid is L2-normalized. Pure and fully deterministic given the same input order.
     *
     * @param embeddings the play + liked embeddings to cluster (any order; same dimension).
     * @param k          the target number of centroids (clamped to `[1, distinctInputs]`).
     */
    fun deriveMoodCentroids(
        embeddings: List<FloatArray>,
        k: Int = DEFAULT_MOOD_CENTROIDS,
    ): List<FloatArray> {
        if (k <= 0) return emptyList()
        val dim = embeddings.firstOrNull { it.isNotEmpty() }?.size ?: return emptyList()
        if (dim == 0) return emptyList()

        // Keep only usable, unit-normalized points (skip wrong-dim / degenerate / non-finite rows).
        val points = ArrayList<FloatArray>(embeddings.size)
        for (e in embeddings) {
            if (e.size != dim) continue
            val unit = normalizeOrNull(DoubleArray(dim) { e[it].toDouble() }) ?: continue
            points += unit
        }
        if (points.isEmpty()) return emptyList()

        // Collapse to distinct directions so K can't exceed the number of genuinely different clusters
        // (near-identical points would otherwise seed empty/duplicate centroids).
        val distinct = distinctDirections(points)
        val kEff = min(k, distinct.size)
        if (kEff <= 1) {
            // One (or one distinct) direction → the single-centroid case: the overall mean direction.
            return listOfNotNull(meanDirection(points, dim))
        }

        // --- deterministic farthest-point seeding -------------------------------------------------
        val seeds = ArrayList<FloatArray>(kEff)
        seeds += meanDirection(points, dim) ?: distinct.first()
        while (seeds.size < kEff) {
            var bestIdx = -1
            var bestDist = Float.NEGATIVE_INFINITY // maximize (1 - maxCosToSeeds) ⇒ minimize maxCos
            for (i in distinct.indices) {
                val p = distinct[i]
                var maxCos = Float.NEGATIVE_INFINITY
                for (s in seeds) {
                    val c = dot(p, s)
                    if (c > maxCos) maxCos = c
                }
                val dist = 1f - maxCos
                if (dist > bestDist) {
                    bestDist = dist
                    bestIdx = i
                }
            }
            if (bestIdx < 0) break
            seeds += distinct[bestIdx]
        }

        // --- Lloyd iterations (spherical k-means) -------------------------------------------------
        var centroids = seeds
        repeat(KMEANS_MAX_ITERS) {
            val buckets = Array(centroids.size) { ArrayList<FloatArray>() }
            for (p in points) {
                var best = 0
                var bestCos = Float.NEGATIVE_INFINITY
                for (ci in centroids.indices) {
                    val c = dot(p, centroids[ci])
                    if (c > bestCos) {
                        bestCos = c
                        best = ci
                    }
                }
                buckets[best] += p
            }
            val next = ArrayList<FloatArray>(centroids.size)
            for (b in buckets) {
                val m = meanDirection(b, dim) ?: continue // drop empty clusters
                next += m
            }
            if (next.isEmpty()) return listOfNotNull(meanDirection(points, dim))
            // Converged (same directions) ⇒ stop early.
            if (sameDirections(next, centroids)) {
                centroids = next
                return@repeat
            }
            centroids = next
        }
        return centroids
    }

    /** The re-normalized mean direction of [points] (unit vectors), or null when empty/degenerate. */
    private fun meanDirection(points: List<FloatArray>, dim: Int): FloatArray? {
        if (points.isEmpty()) return null
        val acc = DoubleArray(dim)
        for (p in points) for (i in 0 until dim) acc[i] += p[i]
        return normalizeOrNull(acc)
    }

    /** Deduplicates near-identical directions (cosine ≥ 1 − [DISTINCT_EPS]), keeping first occurrence. */
    private fun distinctDirections(points: List<FloatArray>): List<FloatArray> {
        val out = ArrayList<FloatArray>()
        for (p in points) {
            if (out.none { dot(it, p) >= 1f - DISTINCT_EPS }) out += p
        }
        return out
    }

    /** True when [a] and [b] are the same set of directions in order (each pair near-identical). */
    private fun sameDirections(a: List<FloatArray>, b: List<FloatArray>): Boolean {
        if (a.size != b.size) return false
        for (i in a.indices) if (dot(a[i], b[i]) < 1f - DISTINCT_EPS) return false
        return true
    }

    /** Cosine tolerance below which two unit directions are treated as distinct. */
    private const val DISTINCT_EPS = 1e-4f

    /** Dot product of two equal-length vectors (== cosine for unit-norm inputs). */
    private fun dot(a: FloatArray, b: FloatArray): Float {
        var acc = 0.0
        val n = min(a.size, b.size)
        for (i in 0 until n) acc += a[i].toDouble() * b[i]
        return acc.toFloat()
    }

    /**
     * Applies one online interaction to [current], returning the nudged taste. Pure and deterministic.
     *
     * Cold-start: an interaction with no prior taste seeds the taste directly from the interaction's
     * embedding (a positive rate) — a skip/dislike with no taste yet is a no-op (nothing to push away
     * from). Otherwise the taste moves along an EMA step: `taste += rate · (embedding − taste)` for a
     * positive rate, and *away* (`taste -= |rate| · embedding`, i.e. an anti-EMA) for a negative one.
     * A per-component **saturation penalty** damps the step for components already near ±1 so a maxed
     * dimension stops growing; the result is re-normalized to a unit vector.
     *
     * @param nowMs stamps the returned state's [TasteState.rebuiltAtMs] only when it seeds a cold taste
     *              (so freshly-seeded taste isn't immediately considered stale); otherwise carried over.
     */
    fun update(current: TasteState, interaction: Interaction, nowMs: Long): TasteState {
        val embedding = interaction.embedding
        if (embedding.isEmpty()) return current
        val rate = rateFor(interaction)
        if (rate == 0f) return current

        val taste = current.vector
        // Cold start: no taste to nudge. A positive signal seeds it from the embedding; a negative one
        // has nothing to move away from, so it is a no-op.
        if (taste == null) {
            if (rate <= 0f) return current
            val seed = normalizeOrNull(embedding.map { it.toDouble() }.toDoubleArray())
                ?: return current
            return TasteState(vector = seed, interactions = 1, rebuiltAtMs = nowMs)
        }
        if (taste.size != embedding.size) return current

        val dim = taste.size
        val next = DoubleArray(dim)
        for (i in 0 until dim) {
            val t = taste[i].toDouble()
            // Saturation: a component near ±1 has little room to grow — damp its step by (1 − |t|).
            val room = 1.0 - abs(t)
            val step = if (rate >= 0f) {
                // EMA toward the embedding, damped near saturation.
                rate * room * (embedding[i].toDouble() - t)
            } else {
                // Anti-EMA: push away from the embedding, also damped near saturation.
                rate.toDouble() * room * embedding[i].toDouble()
            }
            next[i] = t + step
        }

        val normalized = normalizeOrNull(next) ?: return current
        val positive = rate > 0f
        return current.copy(
            vector = normalized,
            interactions = if (positive) current.interactions + 1 else current.interactions,
        )
    }

    /** The signed learning rate for [interaction] (completion-scaled for COMPLETE/SKIP). */
    private fun rateFor(interaction: Interaction): Float {
        val c = interaction.completion.coerceIn(0f, 1f)
        return when (interaction.type) {
            InteractionType.LIKE -> RATE_LIKE
            InteractionType.UNLIKE -> RATE_UNLIKE
            InteractionType.COMPLETE -> RATE_COMPLETE * c
            InteractionType.CLICK -> RATE_CLICK
            // A near-full "skip" is barely a skip; a true 0%-listen gets the full negative rate.
            InteractionType.SKIP -> RATE_SKIP * (1f - c)
            InteractionType.DISLIKE -> RATE_DISLIKE
        }
    }

    /** Exponential recency weight in `(0,1]`: 1 at [nowMs], halving every [RECENCY_HALF_LIFE_DAYS]. */
    private fun recencyWeight(timestampMs: Long, nowMs: Long): Double {
        val ageMs = max(0L, nowMs - timestampMs).toDouble()
        val ageDays = ageMs / MS_PER_DAY
        // exp(-ln2 * age / halfLife) == 2^(-age/halfLife).
        return exp(-0.6931471805599453 * ageDays / RECENCY_HALF_LIFE_DAYS)
    }

    /**
     * Engagement weight from a completion ratio: linear in `[0,1]`, but re-centered so a near-skip
     * (≤ [SKIP_COMPLETION_THRESHOLD]) gets a small negative weight — a barely-listened track pulls the
     * centroid slightly away rather than toward it.
     */
    private fun engagementWeight(completion: Float): Double {
        val c = completion.coerceIn(0f, 1f).toDouble()
        return if (c <= SKIP_COMPLETION_THRESHOLD) {
            // Map [0, threshold] -> a small negative weight in [-0.25, 0].
            -0.25 * (1.0 - c / SKIP_COMPLETION_THRESHOLD)
        } else {
            c
        }
    }

    private fun addScaled(acc: DoubleArray, v: FloatArray, w: Double) {
        for (i in acc.indices) acc[i] += v[i] * w
    }

    /**
     * L2-normalizes [v] to a unit FloatArray, or null when it is (near) all-zero / degenerate. A
     * non-finite norm (any NaN/Inf component, e.g. from a corrupt embedding BLOB) is treated as
     * degenerate too — `NaN <= 1e-12` is false, so it must be caught explicitly to keep a poisoned
     * vector from ever being persisted as the taste.
     */
    private fun normalizeOrNull(v: DoubleArray): FloatArray? {
        var norm = 0.0
        for (x in v) norm += x * x
        if (!norm.isFinite() || norm <= 1e-12) return null
        val inv = 1.0 / sqrt(norm)
        return FloatArray(v.size) { (v[it] * inv).toFloat() }
    }
}
