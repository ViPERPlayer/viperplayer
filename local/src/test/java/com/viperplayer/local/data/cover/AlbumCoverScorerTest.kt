package com.viperplayer.local.data.cover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure [AlbumCoverScorer] heuristic. These run on the JVM with no Android
 * dependencies — the scanner supplies real File/dimension data behind this scorer.
 */
class AlbumCoverScorerTest {

    private fun pick(vararg names: String): String? =
        AlbumCoverScorer.pickBest(names.map { CoverCandidate(it) })?.fileName

    // --- keyword scoring ---

    @Test
    fun coverBeatsBack() {
        assertEquals("cover.jpg", pick("back.jpg", "cover.jpg"))
    }

    @Test
    fun frontBeatsRandomName() {
        assertEquals("front.png", pick("IMG_20210101_random.png", "front.png"))
    }

    @Test
    fun folderIsPreferred() {
        assertEquals("folder.jpg", pick("folder.jpg", "randomphoto.jpg"))
    }

    @Test
    fun albumartIsPreferredOverGeneric() {
        assertEquals("albumart.png", pick("scenery.png", "albumart.png"))
    }

    @Test
    fun backCdDiscArtistAreAllPenalizedVsNeutral() {
        // A neutral, generic name should beat each explicitly-penalized name.
        assertEquals("picture.jpg", pick("back.jpg", "picture.jpg"))
        assertEquals("picture.jpg", pick("cd1.jpg", "picture.jpg"))
        assertEquals("picture.jpg", pick("disc.png", "picture.jpg"))
        assertEquals("picture.jpg", pick("artist.jpg", "picture.jpg"))
        assertEquals("picture.jpg", pick("booklet.jpg", "picture.jpg"))
    }

    @Test
    fun spacedAndSuffixedNamesStillMatchKeywords() {
        assertEquals("Front Cover.jpg", pick("Front Cover.jpg", "back.jpg"))
        assertEquals("folder-1.png", pick("folder-1.png", "band.png"))
    }

    @Test
    fun negativeKeywordDoesNotFireInsideUnrelatedWord() {
        // "cd" must not penalize "cdcover" context, "art" must not penalize "artwork".
        // artwork.jpg (positive) should clearly beat a penalized cd file.
        assertEquals("artwork.jpg", pick("cd.jpg", "artwork.jpg"))
        // "band" penalized, but "abandon.jpg" contains "band" as a substring only -> not penalized,
        // so it should beat an explicitly penalized "back.jpg".
        assertEquals("abandon.jpg", pick("abandon.jpg", "back.jpg"))
    }

    @Test
    fun positiveKeywordArtDoesNotFireOnUnrelatedWords() {
        // Regression: "art" must NOT reward "chart"/"part1"/"heart"/"start" — they are not cover
        // vocabulary, so they stay neutral (0) and must not beat a real cover.
        assertEquals("cover.jpg", pick("chart.jpg", "cover.jpg"))
        // A generic neutral name must tie/beat these, never lose to them: a penalized "back.jpg"
        // (which really is negative) still loses to neutral "chart.jpg" (0), proving chart is 0.
        assertEquals("chart.jpg", pick("chart.jpg", "back.jpg"))
        // "part1" is neutral (0), so it beats a penalized "back.jpg" but is beaten by a real cover.
        assertEquals("part1.jpg", pick("part1.jpg", "back.jpg"))
        assertEquals("cover.jpg", pick("part1.jpg", "cover.jpg"))
    }

    @Test
    fun fusedMultiWordNamesScoreEveryVocabularyWord() {
        // "backcover"/"back cover" must be penalized by "back" even though they contain "cover":
        // a canonical "cover.jpg" (front, +160) must beat "backcover.jpg" (back+cover, -20).
        assertEquals("cover.jpg", pick("backcover.jpg", "cover.jpg"))
        assertEquals("cover.jpg", pick("back cover.jpg", "cover.jpg"))
        // "back cover" (net -20) must lose to a neutral name (0).
        assertEquals("photo.jpg", pick("back cover.jpg", "photo.jpg"))
        // "discart"/"disc art" is disc-label art -> penalized, loses to neutral.
        assertEquals("photo.jpg", pick("discart.jpg", "photo.jpg"))
    }

    @Test
    fun backArtDoesNotBeatNeutralImage() {
        // Regression for the "back art.jpg" asymmetry: it must NOT read as positive. "back" (-120)
        // dominates "art" (+20) so it loses to a neutral "photo.jpg" (0).
        assertEquals("photo.jpg", pick("back art.jpg", "photo.jpg"))
    }

    // --- dimension / quality tie-breaking ---

    @Test
    fun largerAndSquarerWinsWhenKeywordsTie() {
        // Two identically-named-quality generic files: larger + square should win.
        val big = CoverCandidate("art_a.jpg", width = 1000, height = 1000)
        val small = CoverCandidate("art_b.jpg", width = 300, height = 300)
        assertEquals("art_a.jpg", AlbumCoverScorer.pickBest(listOf(small, big))?.fileName)
    }

    @Test
    fun squarerWinsOverOblongAtSimilarSize() {
        val square = CoverCandidate("img_a.jpg", width = 600, height = 600)
        val oblong = CoverCandidate("img_b.jpg", width = 1200, height = 300)
        assertEquals("img_a.jpg", AlbumCoverScorer.pickBest(listOf(oblong, square))?.fileName)
    }

    @Test
    fun keywordBeatsDimensionsWhenTheyConflict() {
        // A well-named but modest cover should still beat a huge, square, but back-labelled image.
        val cover = CoverCandidate("cover.jpg", width = 500, height = 500)
        val hugeBack = CoverCandidate("back.jpg", width = 3000, height = 3000)
        assertEquals("cover.jpg", AlbumCoverScorer.pickBest(listOf(hugeBack, cover))?.fileName)
    }

    // --- rejection ---

    @Test
    fun tinyImagesAreRejected() {
        val tiny = CoverCandidate("cover.jpg", width = 48, height = 48)
        assertNull(AlbumCoverScorer.pickBest(listOf(tiny)))
    }

    @Test
    fun tinyImageRejectedEvenWithBestName_fallsBackToLargerGeneric() {
        val tinyCover = CoverCandidate("cover.jpg", width = 60, height = 60)
        val bigGeneric = CoverCandidate("photo.jpg", width = 800, height = 800)
        assertEquals("photo.jpg", AlbumCoverScorer.pickBest(listOf(tinyCover, bigGeneric))?.fileName)
    }

    @Test
    fun nonImageExtensionsAreIgnored() {
        assertEquals(
            "cover.jpg",
            pick("cover.txt", "cover.mp3", "notes.pdf", "cover.jpg"),
        )
        // A folder with only non-images yields no cover.
        assertNull(pick("readme.txt", "album.nfo", "playlist.m3u"))
    }

    @Test
    fun webpIsSupported() {
        assertEquals("cover.webp", pick("cover.webp", "back.jpg"))
    }

    @Test
    fun emptyCandidateListReturnsNull() {
        assertNull(AlbumCoverScorer.pickBest(emptyList()))
    }

    // --- realistic mixed folder ---

    @Test
    fun realisticMixedFolderPicksFrontOrCover() {
        val best = pick("cover.jpg", "back.jpg", "cd1.png", "artist.jpg", "front.jpg")
        assertTrue(
            "expected front.jpg or cover.jpg but got $best",
            best == "front.jpg" || best == "cover.jpg",
        )
    }

    @Test
    fun realisticMixedFolderWithDimensionsPrefersCanonicalCover() {
        val candidates = listOf(
            CoverCandidate("back.jpg", width = 1500, height = 1500),
            CoverCandidate("cd.png", width = 1400, height = 1400),
            CoverCandidate("artist.jpg", width = 1200, height = 1600),
            CoverCandidate("cover.jpg", width = 1000, height = 1000),
            CoverCandidate("screenshot_2021.png", width = 1920, height = 1080),
        )
        assertEquals("cover.jpg", AlbumCoverScorer.pickBest(candidates)?.fileName)
    }

    // --- ranking API ---

    @Test
    fun rankDropsNonImagesAndTinyImages() {
        val candidates = listOf(
            CoverCandidate("cover.jpg", width = 1000, height = 1000),
            CoverCandidate("notes.txt"),
            CoverCandidate("thumb.png", width = 50, height = 50),
            CoverCandidate("back.jpg", width = 900, height = 900),
        )
        val ranked = AlbumCoverScorer.rank(candidates)
        assertEquals(2, ranked.size)
        assertEquals("cover.jpg", ranked.first().candidate.fileName)
        assertNotNull(ranked)
    }

    @Test
    fun tieBreakIsDeterministicByFileName() {
        // Two generic same-score images with no dimensions: the pick must not depend on input
        // order (which, from File.listFiles(), is filesystem-arbitrary).
        val a = CoverCandidate("img_alpha.jpg")
        val b = CoverCandidate("img_zebra.jpg")
        assertEquals("img_alpha.jpg", AlbumCoverScorer.pickBest(listOf(a, b))?.fileName)
        assertEquals("img_alpha.jpg", AlbumCoverScorer.pickBest(listOf(b, a))?.fileName)
    }
}
