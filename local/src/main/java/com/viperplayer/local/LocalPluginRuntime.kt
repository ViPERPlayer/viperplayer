package com.viperplayer.local

import com.viperplayer.plugin.author.PluginHost

/**
 * Process-wide handle to the connected host. The plugin is embedded in the host app (same
 * process), so the settings screen can push a status update the instant the permission is granted.
 */
internal object LocalPluginRuntime {
    @Volatile
    var host: PluginHost? = null
}
