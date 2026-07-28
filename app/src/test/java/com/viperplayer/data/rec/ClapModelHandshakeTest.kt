package com.viperplayer.data.rec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure CLAP model version-handshake + sha256 verification logic
 * ([ClapModelHandshake]). These are the load-bearing correctness decisions of the P1b downloader:
 * whether to (re)download, and whether the downloaded bytes are trustworthy.
 */
class ClapModelHandshakeTest {

    private val expected = ClapModel.MODEL_VERSION // "laion-larger_clap_music-mel1"

    // --- shouldDownload: the version-handshake decision ---

    @Test
    fun shouldDownload_whenNothingInstalled_andManifestMatchesApp() {
        assertTrue(ClapModelHandshake.shouldDownload(installedVersion = null, manifestVersion = expected))
    }

    @Test
    fun shouldDownload_whenInstalledBlank_treatedAsAbsent() {
        assertTrue(ClapModelHandshake.shouldDownload(installedVersion = "", manifestVersion = expected))
        assertTrue(ClapModelHandshake.shouldDownload(installedVersion = "   ", manifestVersion = expected))
    }

    @Test
    fun shouldNotDownload_whenInstalledMatchesManifestAndApp() {
        assertFalse(ClapModelHandshake.shouldDownload(installedVersion = expected, manifestVersion = expected))
    }

    @Test
    fun shouldDownload_whenInstalledIsAnOldVersion_butManifestMatchesApp() {
        // Installed an old checkpoint; the server (and this app build) want `expected`.
        assertTrue(
            ClapModelHandshake.shouldDownload(
                installedVersion = "laion-larger_clap_music-mel0",
                manifestVersion = expected,
            )
        )
    }

    @Test
    fun shouldNotDownload_whenManifestVersionIsIncompatibleWithApp() {
        // Backend advertises a NEWER model this app build cannot consume → nothing safe to download.
        assertFalse(
            ClapModelHandshake.shouldDownload(
                installedVersion = null,
                manifestVersion = "laion-larger_clap_music-mel2",
                expectedVersion = expected,
            )
        )
        // Even if we already have the app's version installed, a mismatched manifest is not fetched.
        assertFalse(
            ClapModelHandshake.shouldDownload(
                installedVersion = expected,
                manifestVersion = "laion-larger_clap_music-mel2",
                expectedVersion = expected,
            )
        )
    }

    // --- isManifestCompatible / isInstalledStale ---

    @Test
    fun isManifestCompatible_matchesExpectedOnly() {
        assertTrue(ClapModelHandshake.isManifestCompatible(expected))
        assertFalse(ClapModelHandshake.isManifestCompatible("laion-larger_clap_music-mel2"))
    }

    @Test
    fun isInstalledStale_onlyWhenPresentAndDifferent() {
        assertFalse(ClapModelHandshake.isInstalledStale(null))
        assertFalse(ClapModelHandshake.isInstalledStale(""))
        assertFalse(ClapModelHandshake.isInstalledStale(expected))
        assertTrue(ClapModelHandshake.isInstalledStale("laion-larger_clap_music-mel0"))
    }

    // --- file-name convention ---

    @Test
    fun installedAndPartFileNames_areVersioned() {
        assertEquals("clap_audio_$expected.onnx", ClapModelHandshake.installedFileName(expected))
        assertEquals("clap_audio_$expected.onnx.part", ClapModelHandshake.partFileName(expected))
    }

    // --- sha256 verification (good + tampered) ---

    @Test
    fun verifySha256_acceptsCorrectDigest() {
        val bytes = "the-quick-brown-fox".toByteArray()
        val digest = ClapModelHandshake.sha256Hex(bytes)
        assertTrue(ClapModelHandshake.verifySha256(bytes, digest))
        // Case-insensitive on the hex.
        assertTrue(ClapModelHandshake.verifySha256(bytes, digest.uppercase()))
    }

    @Test
    fun verifySha256_rejectsTamperedBytes() {
        val original = "model-bytes-v1".toByteArray()
        val digest = ClapModelHandshake.sha256Hex(original)
        val tampered = "model-bytes-v1".toByteArray().also { it[0] = (it[0] + 1).toByte() }
        assertFalse(ClapModelHandshake.verifySha256(tampered, digest))
    }

    @Test
    fun verifySha256_rejectsMalformedExpected() {
        val bytes = "abc".toByteArray()
        assertFalse(ClapModelHandshake.verifySha256(bytes, "not-a-real-hex-digest"))
        assertFalse(ClapModelHandshake.verifySha256(bytes, ""))
    }

    @Test
    fun sha256Hex_isKnownVector() {
        // sha256("") = e3b0c442... (the standard empty-string digest).
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            ClapModelHandshake.sha256Hex(ByteArray(0)),
        )
    }
}
