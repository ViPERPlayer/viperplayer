package com.viperplayer.data.plugin.update

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * The per-plugin UPDATE MANIFEST the host fetches from a plugin's update feed (see
 * [com.viperplayer.plugin.protocol.Protocol.META_UPDATE_URL]). It's a small JSON document that
 * advertises the newest available build of the plugin and where to get it.
 *
 * Example:
 * ```json
 * {
 *   "versionCode": 7,
 *   "versionName": "1.3.0",
 *   "downloadUrl": "https://example.com/plugins/example/example-1.3.0.apk",
 *   "changelog": "Fixed login on some regions.",
 *   "minHostVersion": 5,
 *   "sha256": "3a7bd3e2360a…"
 * }
 * ```
 *
 * Only [versionCode], [versionName] and [downloadUrl] are required; the rest are optional. Kept
 * Android-free and side-effect-free so it can be unit-tested on the JVM.
 */
@Serializable
data class PluginUpdateManifest(
    /** Monotonic build number of the latest release, compared against the installed versionCode. */
    val versionCode: Long,
    /** Human-readable version label shown to the user (e.g. "1.3.0"). */
    val versionName: String,
    /** HTTPS URL of the APK to download and install. */
    val downloadUrl: String,
    /** Optional release notes shown to the user before they install. */
    val changelog: String? = null,
    /**
     * Optional minimum host [versionCode] this plugin build requires. When the host is older, the
     * update is withheld (it would install a plugin the host can't talk to). `null` = no floor.
     */
    val minHostVersion: Long? = null,
    /**
     * Optional lowercase hex SHA-256 of the APK bytes. When present the host verifies the download
     * matches before installing; a mismatch fails the install rather than handing a corrupt/tampered
     * APK to the package installer.
     */
    val sha256: String? = null,
) {
    companion object {
        /** Lenient parser: unknown keys are ignored so the manifest format can grow over time. */
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        /**
         * Parse an update manifest from raw JSON [text]. Returns `null` for malformed JSON or a
         * document missing any required field ([versionCode]/[versionName]/[downloadUrl]) — the
         * caller treats a `null` as "no usable update info" and skips the plugin gracefully.
         */
        fun parse(text: String): PluginUpdateManifest? = try {
            json.decodeFromString<PluginUpdateManifest>(text).takeIf { it.isValid() }
        } catch (e: Exception) {
            Timber.w(e, "Failed to parse plugin update manifest")
            null
        }
    }
}

/** A manifest is only usable when its required fields are present and sane. */
private fun PluginUpdateManifest.isValid(): Boolean =
    versionCode > 0 && versionName.isNotBlank() && downloadUrl.isNotBlank()
