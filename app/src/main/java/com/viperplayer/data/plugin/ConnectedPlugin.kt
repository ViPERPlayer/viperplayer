package com.viperplayer.data.plugin

import android.content.ServiceConnection
import com.viperplayer.data.plugin.handler.PluginHandler
import com.viperplayer.domain.model.PluginInfo

/**
 * Represents a connected plugin with its handler.
 * This is the abstract base class that works with any API version.
 */
class ConnectedPlugin(
    val info: PluginInfo,
    val handler: PluginHandler,
    val connection: ServiceConnection
) {
    /**
     * The API version of this plugin.
     */
    val apiVersion: Int
        get() = handler.apiVersion
}

