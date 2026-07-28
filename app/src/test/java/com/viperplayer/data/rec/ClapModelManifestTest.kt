package com.viperplayer.data.rec

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Unit tests for parsing the backend model manifest ([ClapModelManifest]) — the exact JSON shape
 * served by `GET /v1/models/manifest`. Uses the same lenient [Json] config the app's ClapModelApi
 * uses (ignoreUnknownKeys) so a backend adding new models does not break parsing.
 */
class ClapModelManifestTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parses_clapAudioInt8Entry() {
        val body = """
            {
              "clap_audio_int8": {
                "version": "laion-larger_clap_music-mel1",
                "sha256": "abc123def456",
                "bytes": 76543210,
                "path": "/v1/models/clap-audio-int8.onnx"
              }
            }
        """.trimIndent()

        val manifest = json.decodeFromString<ClapModelManifest>(body)
        val entry = manifest.clapAudioInt8

        assertEquals("laion-larger_clap_music-mel1", entry.version)
        assertEquals("abc123def456", entry.sha256)
        assertEquals(76543210L, entry.bytes)
        assertEquals("/v1/models/clap-audio-int8.onnx", entry.path)
    }

    @Test
    fun ignoresUnknownTopLevelModels() {
        // The backend may advertise more models later; the app only reads the CLAP one.
        val body = """
            {
              "clap_audio_int8": {
                "version": "laion-larger_clap_music-mel1",
                "sha256": "deadbeef",
                "bytes": 100,
                "path": "/v1/models/clap-audio-int8.onnx"
              },
              "some_future_model": { "version": "x", "sha256": "y", "bytes": 1, "path": "/z" }
            }
        """.trimIndent()

        val manifest = json.decodeFromString<ClapModelManifest>(body)
        assertEquals("deadbeef", manifest.clapAudioInt8.sha256)
    }

    @Test
    fun ignoresUnknownEntryFields() {
        val body = """
            {
              "clap_audio_int8": {
                "version": "v1",
                "sha256": "aa",
                "bytes": 1,
                "path": "/p",
                "extraFutureField": "ignored"
              }
            }
        """.trimIndent()

        val entry = json.decodeFromString<ClapModelManifest>(body).clapAudioInt8
        assertEquals("v1", entry.version)
    }

    @Test
    fun missingClapEntry_throws() {
        // A manifest with no CLAP model is malformed for this app — the API maps the thrown parse
        // failure to Unavailable; here we just assert it does not silently succeed.
        val body = """{ "other_model": { "version": "x", "sha256": "y", "bytes": 1, "path": "/z" } }"""
        assertThrows(Exception::class.java) {
            json.decodeFromString<ClapModelManifest>(body)
        }
    }
}
