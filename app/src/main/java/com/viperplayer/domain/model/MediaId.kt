package com.viperplayer.domain.model

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Unique identifier for media items across plugins.
 */
@Parcelize
data class MediaId(
    val pluginId: String,
    val sourceId: String
) : Parcelable {
    override fun toString(): String {
        val encodedPluginId = Uri.encode(pluginId)
        val encodedSourceId = Uri.encode(sourceId)
        return "pluginId=$encodedPluginId&sourceId=$encodedSourceId"
    }

    companion object {
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

