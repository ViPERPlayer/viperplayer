package com.viperplayer.data.plugin.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for parsing the plugin update manifest JSON: valid, malformed, and missing-field. */
class PluginUpdateManifestTest {

    @Test
    fun parse_validFullManifest() {
        val json = """
            {
              "versionCode": 7,
              "versionName": "1.3.0",
              "downloadUrl": "https://example.com/example.apk",
              "changelog": "Fixed login on some regions.",
              "minHostVersion": 5,
              "sha256": "3a7bd3e2360a"
            }
        """.trimIndent()

        val manifest = PluginUpdateManifest.parse(json)
        assertNotNull(manifest)
        manifest!!
        assertEquals(7L, manifest.versionCode)
        assertEquals("1.3.0", manifest.versionName)
        assertEquals("https://example.com/example.apk", manifest.downloadUrl)
        assertEquals("Fixed login on some regions.", manifest.changelog)
        assertEquals(5L, manifest.minHostVersion)
        assertEquals("3a7bd3e2360a", manifest.sha256)
    }

    @Test
    fun parse_validMinimalManifest_optionalsNull() {
        val json = """
            { "versionCode": 2, "versionName": "1.1", "downloadUrl": "https://x/y.apk" }
        """.trimIndent()

        val manifest = PluginUpdateManifest.parse(json)
        assertNotNull(manifest)
        manifest!!
        assertEquals(2L, manifest.versionCode)
        assertNull(manifest.changelog)
        assertNull(manifest.minHostVersion)
        assertNull(manifest.sha256)
    }

    @Test
    fun parse_ignoresUnknownKeys() {
        val json = """
            {
              "versionCode": 3, "versionName": "1.2",
              "downloadUrl": "https://x/y.apk",
              "futureField": { "nested": true }, "extra": 42
            }
        """.trimIndent()

        val manifest = PluginUpdateManifest.parse(json)
        assertNotNull(manifest)
        assertEquals(3L, manifest!!.versionCode)
    }

    @Test
    fun parse_malformedJson_returnsNull() {
        assertNull(PluginUpdateManifest.parse("{ this is not json"))
        assertNull(PluginUpdateManifest.parse(""))
        assertNull(PluginUpdateManifest.parse("[]"))
    }

    @Test
    fun parse_missingRequiredField_versionCode_returnsNull() {
        val json = """
            { "versionName": "1.1", "downloadUrl": "https://x/y.apk" }
        """.trimIndent()
        assertNull(PluginUpdateManifest.parse(json))
    }

    @Test
    fun parse_missingRequiredField_downloadUrl_returnsNull() {
        val json = """
            { "versionCode": 2, "versionName": "1.1" }
        """.trimIndent()
        assertNull(PluginUpdateManifest.parse(json))
    }

    @Test
    fun parse_blankDownloadUrl_returnsNull() {
        val json = """
            { "versionCode": 2, "versionName": "1.1", "downloadUrl": "" }
        """.trimIndent()
        assertNull(PluginUpdateManifest.parse(json))
    }

    @Test
    fun parse_blankVersionName_returnsNull() {
        val json = """
            { "versionCode": 2, "versionName": "", "downloadUrl": "https://x/y.apk" }
        """.trimIndent()
        assertNull(PluginUpdateManifest.parse(json))
    }

    @Test
    fun parse_nonPositiveVersionCode_returnsNull() {
        val json = """
            { "versionCode": 0, "versionName": "1.1", "downloadUrl": "https://x/y.apk" }
        """.trimIndent()
        assertNull(PluginUpdateManifest.parse(json))
    }

    // ---- https enforcement seam: a manifest whose downloadUrl is not https yields no update ----

    @Test
    fun httpDownloadUrl_isRejectedByHttpsGate() {
        // A manifest can parse fine (JSON is well-formed) yet still be refused by the manager's
        // in-code https gate. This mirrors the check in PluginUpdateManager.checkInternal: a parsed
        // manifest with a cleartext downloadUrl is treated as "no update" and never downloaded.
        val json = """
            { "versionCode": 2, "versionName": "1.1", "downloadUrl": "http://x/y.apk" }
        """.trimIndent()

        val manifest = PluginUpdateManifest.parse(json)
        assertNotNull(manifest)
        assertFalse(PluginUpdateLogic.isHttpsUrl(manifest!!.downloadUrl))
    }

    @Test
    fun httpsDownloadUrl_passesHttpsGate() {
        val json = """
            { "versionCode": 2, "versionName": "1.1", "downloadUrl": "https://x/y.apk" }
        """.trimIndent()

        val manifest = PluginUpdateManifest.parse(json)
        assertNotNull(manifest)
        assertTrue(PluginUpdateLogic.isHttpsUrl(manifest!!.downloadUrl))
    }
}
