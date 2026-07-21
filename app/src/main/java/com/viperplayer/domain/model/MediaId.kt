package com.viperplayer.domain.model

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * Unique identifier for media items across sources.
 *
 * A **sealed** hierarchy so that synthetic ids (radio, and the reserved auto-playlist id) live in
 * their own typed variants and can never collide with a real installed plugin id:
 *
 * - [Plugin] — content served by an installed plugin (e.g. `testsource`, `othersource`), keyed by the
 *   plugin's own `pluginId` + `sourceId`. The reserved `auto` auto-playlist id is also a [Plugin]
 *   (its "plugin" is the virtual auto-playlist provider — see
 *   [com.viperplayer.domain.autoplaylist.AutoPlaylistType]).
 * - [Local] — content from the on-device local library (formerly `pluginId == "local"`).
 * - [Radio] — a virtual "Song radio" playlist (issue #7), identified purely by its seed [MediaId];
 *   no magic string is involved.
 *
 * The canonical string form used for media3 `mediaItem.mediaId`, DataStore persistence and anywhere
 * else a `String` id is required is produced by [encode] and parsed back by [decode] (these replace
 * the former `toString()`/`fromString()`).
 */
@Serializable
sealed interface MediaId : Parcelable {

    /**
     * Convenience source id at the interface level to keep accessor churn low:
     * - [Plugin]/[Local] return their own `sourceId`.
     * - [Radio] returns the [encode]d seed (its identity, used as an opaque key).
     */
    val sourceId: String

    /**
     * The plugin-SDK routing id: [Plugin] routes to its own plugin, [Local] to the embedded local
     * plugin ([LOCAL_PLUGIN_ID]). Used at the String boundary that hands an id to a source client.
     */
    val routingPluginId: String
        get() = when (this) {
            is Plugin -> pluginId
            is Local -> LOCAL_PLUGIN_ID
            is Radio -> error("Radio MediaId has no source plugin")
        }

    /** Canonical, type-tagged, URL-encoded string form. Round-trips through [decode]. */
    fun encode(): String

    /** Content from an installed plugin (or the reserved `auto` auto-playlist provider). */
    @Serializable
    @Parcelize
    data class Plugin(val pluginId: String, override val sourceId: String) : MediaId {
        init {
            require(sourceId.isNotBlank()) { "MediaId.Plugin.sourceId must not be blank" }
        }

        override fun encode(): String =
            "t=plugin&p=${Uri.encode(pluginId)}&s=${Uri.encode(sourceId)}"
    }

    /** Content from the on-device local library. */
    @Serializable
    @Parcelize
    data class Local(override val sourceId: String) : MediaId {
        init {
            require(sourceId.isNotBlank()) { "MediaId.Local.sourceId must not be blank" }
        }

        override fun encode(): String = "t=local&s=${Uri.encode(sourceId)}"
    }

    /** A virtual "Song radio" playlist seeded by [seed] (issue #7). */
    @Serializable
    @Parcelize
    data class Radio(val seed: MediaId) : MediaId {
        /** The encoded seed — the radio's opaque identity. */
        override val sourceId: String get() = seed.encode()

        override fun encode(): String = "t=radio&seed=${Uri.encode(seed.encode())}"
    }

    companion object {
        /**
         * The reserved plugin id of the embedded on-device local library "plugin". It is a real
         * installed source id at the plugin-SDK boundary (the host embeds it), used only to route a
         * plain (pluginId, sourceId) String pair to/from the typed [Local] variant — never a synthetic
         * MediaId magic string (local content is modeled by [Local]).
         */
        const val LOCAL_PLUGIN_ID = "local"

        /**
         * Builds a [Plugin] id, or `null` when [sourceId] is blank. Absence of a link is `null` — a
         * MediaId must never exist with a blank sourceId. Use this at every construction site that
         * may receive an unlinked reference (e.g. plugin byline artists).
         */
        fun of(pluginId: String, sourceId: String): Plugin? =
            if (sourceId.isBlank()) null else Plugin(pluginId, sourceId)

        /**
         * Builds the domain [MediaId] for content served by [pluginId] with [sourceId]. The embedded
         * local plugin ([LOCAL_PLUGIN_ID]) maps to [Local]; every other plugin to [Plugin]. The single
         * choke point that turns a plugin-SDK (pluginId, sourceId) pair into the typed id.
         */
        fun from(pluginId: String, sourceId: String): MediaId =
            if (pluginId == LOCAL_PLUGIN_ID) Local(sourceId) else Plugin(pluginId, sourceId)

        /** As [from], but `null` when [sourceId] is blank (an unlinked reference). */
        fun fromOrNull(pluginId: String, sourceId: String): MediaId? =
            if (sourceId.isBlank()) null else from(pluginId, sourceId)

        /**
         * Parses a canonical [encode] string back into a [MediaId], or `null` when malformed. Callers
         * already null-check, so this never throws.
         */
        fun decode(string: String): MediaId? {
            val params = try {
                string.split("&").mapNotNull { part ->
                    val idx = part.indexOf('=')
                    if (idx <= 0) null else part.substring(0, idx) to Uri.decode(part.substring(idx + 1))
                }.toMap()
            } catch (e: Exception) {
                return null
            }
            return when (params["t"]) {
                "plugin" -> {
                    val pluginId = params["p"] ?: return null
                    val sourceId = params["s"] ?: return null
                    if (sourceId.isBlank()) null else Plugin(pluginId, sourceId)
                }
                "local" -> {
                    val sourceId = params["s"] ?: return null
                    if (sourceId.isBlank()) null else Local(sourceId)
                }
                "radio" -> {
                    val seed = params["seed"]?.let { decode(it) } ?: return null
                    Radio(seed)
                }
                else -> null
            }
        }
    }
}
