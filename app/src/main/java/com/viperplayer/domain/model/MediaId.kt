package com.viperplayer.domain.model

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * Unique identifier for media items across plugins.
 */
@Serializable
@Parcelize
data class MediaId(
    val pluginId: String,
    val sourceId: String
) : Parcelable {
    init {
        require(sourceId.isNotBlank()) { "MediaId.sourceId must not be blank" }
    }

    override fun toString(): String {
        val encodedPluginId = Uri.encode(pluginId)
        val encodedSourceId = Uri.encode(sourceId)
        return "pluginId=$encodedPluginId&sourceId=$encodedSourceId"
    }

    companion object {
        /**
         * Builds a [MediaId], or null when [sourceId] is blank. Absence of a link is `null` — a
         * MediaId must never exist with a blank sourceId. Use this at every construction site that
         * may receive an unlinked reference (e.g. plugin byline artists).
         */
        fun of(pluginId: String, sourceId: String): MediaId? =
            if (sourceId.isBlank()) null else MediaId(pluginId, sourceId)

        fun fromString(string: String): MediaId {
            try {
                val params = string.split("&").associate {
                    val (key, value) = it.split("=")
                    key to Uri.decode(value)
                }
                val pluginId = params["pluginId"] ?: error("Missing pluginId in MediaId")
                val sourceId = params["sourceId"] ?: error("Missing sourceId in MediaId")
                return MediaId(pluginId, sourceId)
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid MediaId format: $string", e)
            }
        }
    }
}

