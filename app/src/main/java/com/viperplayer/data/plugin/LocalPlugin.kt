package com.viperplayer.data.plugin

import com.viperplayer.domain.model.MediaId

/**
 * Data-layer aliases for the domain [MediaId] local-plugin routing helpers, so call sites in the data
 * layer read naturally. The canonical definitions live on [MediaId] / its companion.
 */

/** @see MediaId.Companion.LOCAL_PLUGIN_ID */
const val LOCAL_PLUGIN_ID = MediaId.LOCAL_PLUGIN_ID

/** @see MediaId.Companion.from */
fun mediaIdFor(pluginId: String, sourceId: String): MediaId = MediaId.from(pluginId, sourceId)

/** @see MediaId.Companion.fromOrNull */
fun mediaIdForOrNull(pluginId: String, sourceId: String): MediaId? = MediaId.fromOrNull(pluginId, sourceId)
