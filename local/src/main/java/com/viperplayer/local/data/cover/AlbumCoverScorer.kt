package com.viperplayer.local.data.cover

/**
 * A candidate cover-art image found in an album's folder.
 *
 * This is a pure, Android-free descriptor: the scanner (Android layer) is responsible for
 * discovering the files and cheaply reading their pixel dimensions (via a bounds-only
 * `BitmapFactory` decode), then handing plain data to [AlbumCoverScorer].
 *
 * @param fileName the image's file name including extension, e.g. `"Front Cover.jpg"`.
 * @param width pixel width if cheaply known (bounds-only decode), else null.
 * @param height pixel height if cheaply known (bounds-only decode), else null.
 * @param fileSizeBytes file size in bytes if known, else null. Used only as a last-resort
 *   tie-breaker (bigger file ≈ higher quality) when dimensions are unavailable.
 */
data class CoverCandidate(
    val fileName: String,
    val width: Int? = null,
    val height: Int? = null,
    val fileSizeBytes: Long? = null,
)

/**
 * A scored candidate, exposed so callers can inspect a full ranking (best first).
 *
 * [score] is a relative quality number; higher is better. Candidates that were rejected outright
 * (non-image extension, or an image known to be smaller than [AlbumCoverScorer.MIN_DIMENSION_PX])
 * never appear in a ranking.
 */
data class ScoredCandidate(
    val candidate: CoverCandidate,
    val score: Double,
)

/**
 * Pure, Android-free heuristic that picks the best album-cover image from a folder's candidates.
 *
 * The scanner supplies real [CoverCandidate]s (file name plus optionally cheap width/height/size)
 * and this scorer returns the single best pick, or a full ranking. All Android bitmap decoding
 * lives in the scanner behind this class, so the scoring logic stays unit-testable on the JVM.
 *
 * Scoring, roughly:
 * - Non-image extensions are rejected (never returned).
 * - Images known to be smaller than [MIN_DIMENSION_PX] on their shorter side are rejected as
 *   likely thumbnails/icons.
 * - Filename keywords dominate: exact common cover names (`cover.*`, `folder.*`, `front.*`) score
 *   highest, positive keywords (`albumart`, `album`, `artwork`, ...) add points, and negative
 *   keywords (`back`, `cd`, `disc`, `inlay`, `booklet`, `artist`, `band`, `thumb`, `scan`, ...)
 *   subtract points. Matching is case-insensitive and word-based (see [matchesAsWord]): keywords
 *   match whole words, tolerating separators (`Front Cover.jpg`, `folder-1.png`, `cd1.png`) and
 *   fused vocabulary (`backcover.jpg` scores both `back` and `cover`), but never as bare
 *   substrings of unrelated words (`chart.jpg`/`part1.jpg` do not match `art`).
 * - When keyword scores tie, larger and more-square images (aspect ratio near 1.0) win; file size
 *   is the final tie-breaker.
 */
object AlbumCoverScorer {

    /** Images whose shorter side is below this are rejected as thumbnails/icons. */
    const val MIN_DIMENSION_PX = 100

    /** Supported image extensions (lower-case, no dot). */
    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

    /**
     * File-name stems (the part before the extension) that are, on their own, the canonical name
     * for an album cover. A file whose whole stem is exactly one of these earns [CANONICAL_BONUS].
     */
    private val CANONICAL_STEMS = setOf("cover", "folder", "front")

    /** Positive keywords: each adds its points when it appears as a whole word (see [matchesAsWord]). */
    private val POSITIVE_KEYWORDS = mapOf(
        "cover" to 100.0,
        "front" to 100.0,
        "folder" to 90.0,
        "albumart" to 90.0,
        "album" to 60.0,
        "artwork" to 60.0,
        "art" to 20.0,
    )

    /** Negative keywords: each subtracts its points when it appears as a whole word (see [matchesAsWord]). */
    private val NEGATIVE_KEYWORDS = mapOf(
        "back" to 120.0,
        "inlay" to 120.0,
        "booklet" to 100.0,
        "obi" to 80.0,
        "disc" to 90.0,
        "disk" to 90.0,
        "cd" to 90.0,
        "artist" to 100.0,
        "band" to 80.0,
        "thumb" to 150.0,
        "thumbnail" to 150.0,
        "scan" to 70.0,
        "spine" to 90.0,
        "tray" to 90.0,
        "logo" to 80.0,
        "screenshot" to 150.0,
    )

    /** Extra bonus for a stem that is exactly a canonical cover name. */
    private const val CANONICAL_BONUS = 60.0

    /**
     * Returns the best cover candidate, or null when [candidates] is empty or nothing usable
     * remains after rejecting non-images and known-tiny images.
     */
    fun pickBest(candidates: List<CoverCandidate>): CoverCandidate? =
        rank(candidates).firstOrNull()?.candidate

    /**
     * Ranks [candidates] best-first. Non-image extensions and images known to be below
     * [MIN_DIMENSION_PX] are dropped entirely. Stable: ties keep the input order.
     */
    fun rank(candidates: List<CoverCandidate>): List<ScoredCandidate> =
        candidates
            .asSequence()
            .filter { isSupportedImage(it.fileName) }
            .filterNot { isKnownTiny(it) }
            .map { ScoredCandidate(it, score(it)) }
            .sortedByDescending { it.score }
            .toList()

    /**
     * Computes a candidate's score. Assumes the candidate is a supported image (see [rank]).
     * Exposed for testing and inspection.
     */
    fun score(candidate: CoverCandidate): Double {
        val stem = stemOf(candidate.fileName)
        // Split into word tokens so keywords only ever match whole words (or whole words fused
        // together like "frontcover"), never as substrings of unrelated words ("chart", "part1").
        val tokens = tokenize(stem)

        var keywordScore = 0.0

        // Exact canonical name (e.g. "cover", "folder", "front"): the whole stem is a single token
        // that IS one of the canonical names. Strongest signal.
        if (tokens.size == 1 && tokens.first() in CANONICAL_STEMS) {
            keywordScore += CANONICAL_BONUS
        }

        // Keywords are matched the same way for positive and negative sets: a keyword scores when it
        // is a whole token, OR when it appears at a word boundary inside a fused token (so
        // "frontcover" hits both "front" and "cover", but "chart" never hits "art" and "backcover"
        // correctly hits "back").
        for ((keyword, points) in POSITIVE_KEYWORDS) {
            if (tokens.any { matchesAsWord(it, keyword) }) keywordScore += points
        }
        for ((keyword, points) in NEGATIVE_KEYWORDS) {
            if (tokens.any { matchesAsWord(it, keyword) }) keywordScore -= points
        }

        // Quality score: prefer larger + more-square images; keep it well below keyword weights so
        // it only ever breaks ties between similarly-named files.
        return keywordScore + qualityScore(candidate)
    }

    /**
     * A small [0, ~15) quality contribution from dimensions (and file size as a fallback), used to
     * break keyword ties. Larger area and aspect ratio nearer 1.0 score higher.
     */
    private fun qualityScore(candidate: CoverCandidate): Double {
        val w = candidate.width
        val h = candidate.height
        if (w != null && h != null && w > 0 && h > 0) {
            // Size component: log-scaled shorter side, so 100px≈0, 500px≈~7, 1000px≈~9, capped ~10.
            val shorter = minOf(w, h)
            val sizeScore = (Math.log10(shorter.toDouble() / MIN_DIMENSION_PX + 1) * 7.5)
                .coerceIn(0.0, 10.0)
            // Squareness component: 1.0 aspect -> full 5 points, degrades as it gets oblong.
            val aspect = maxOf(w, h).toDouble() / minOf(w, h).toDouble()
            val squareScore = (5.0 / aspect).coerceIn(0.0, 5.0)
            return sizeScore + squareScore
        }
        // No dimensions: a bigger file is weakly better. Cap at ~2 so it never rivals dimensions.
        val bytes = candidate.fileSizeBytes ?: return 0.0
        return (Math.log10(bytes.toDouble() / 1024.0 + 1) * 0.5).coerceIn(0.0, 2.0)
    }

    /** True when the file name has a supported image extension. */
    fun isSupportedImage(fileName: String): Boolean =
        extensionOf(fileName) in IMAGE_EXTENSIONS

    /** True when dimensions are known and the image's shorter side is below [MIN_DIMENSION_PX]. */
    private fun isKnownTiny(candidate: CoverCandidate): Boolean {
        val w = candidate.width ?: return false
        val h = candidate.height ?: return false
        return w in 1 until MIN_DIMENSION_PX || h in 1 until MIN_DIMENSION_PX
    }

    private fun extensionOf(fileName: String): String =
        fileName.substringAfterLast('.', "").lowercase()

    private fun stemOf(fileName: String): String =
        fileName.substringBeforeLast('.', fileName)

    /**
     * Splits a file-name stem into lower-cased word tokens on separators (space, `_`, `-`, `.`)
     * and letter/digit boundaries, so `"Front Cover"`, `"front-cover"`, `"folder-1"` and `"cd1"`
     * become `["front","cover"]`, `["front","cover"]`, `["folder","1"]` and `["cd","1"]`. Empty
     * tokens are dropped.
     */
    private fun tokenize(stem: String): List<String> =
        TOKEN_SPLIT.split(stem.lowercase()).filter { it.isNotEmpty() }

    /**
     * True when [keyword] describes [token]. A keyword matches when the token equals it, or when
     * the token is a run of known cover-vocabulary words fused together (e.g. `"frontcover"`,
     * `"backcover"`, `"albumcover"`) and [keyword] is one of those words. This lets fused names
     * hit every vocabulary word they contain while never letting a keyword match as a bare
     * substring of an unrelated word — `"chart"` / `"part"` / `"heart"` do NOT match `"art"`,
     * `"discography"` does NOT match `"disc"`, but `"backcover"` correctly matches both
     * `"back"` and `"cover"`.
     */
    private fun matchesAsWord(token: String, keyword: String): Boolean {
        if (token == keyword) return true
        val words = decomposeIntoVocab(token) ?: return false
        return keyword in words
    }

    /**
     * Greedily splits [token] into a sequence of known [VOCABULARY] words (longest-first), or
     * returns null when the token is not fully composed of vocabulary words. Used so that only
     * genuinely fused cover terms ("frontcover") are decomposed, never arbitrary words.
     */
    private fun decomposeIntoVocab(token: String): List<String>? {
        if (token.isEmpty()) return emptyList()
        for (word in VOCABULARY) { // VOCABULARY is longest-first
            if (token.startsWith(word)) {
                val rest = decomposeIntoVocab(token.removePrefix(word)) ?: continue
                return listOf(word) + rest
            }
        }
        return null
    }

    private val TOKEN_SPLIT = Regex("[\\s_.\\-]+|(?<=\\p{L})(?=\\d)|(?<=\\d)(?=\\p{L})")

    /**
     * All cover-related vocabulary words, longest-first so greedy decomposition prefers the longest
     * match (e.g. `"albumart"` before `"album"`). Used only to split fused tokens like
     * `"frontcover"`; a token is decomposed only when it is entirely vocabulary.
     */
    private val VOCABULARY: List<String> =
        (POSITIVE_KEYWORDS.keys + NEGATIVE_KEYWORDS.keys + CANONICAL_STEMS)
            .distinct()
            .sortedByDescending { it.length }
}
