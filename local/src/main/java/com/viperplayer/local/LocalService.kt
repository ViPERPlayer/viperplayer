package com.viperplayer.local

import android.content.Context
import com.viperplayer.plugin.model.SourceCapabilities
import com.viperplayer.plugin.author.ViperPluginService
import com.viperplayer.plugin.author.pluginRegistration

class LocalService : ViperPluginService() {
    override fun onCreatePlugin(context: Context) = pluginRegistration(
        id = "com.viperplayer.local",
        name = "Local Files",
        version = "1.0",
    ) {
        val localSource = LocalSource(context)
        source(
            localSource,
            SourceCapabilities(
                search = true,
                library = true,
                playlists = false,
                supportsOffline = true,
                canSeek = true,
            ),
        )
        settingsActivity(LocalSettingsActivity::class.java.name)
        // Warm the MediaStore cache as soon as the host connects.
        onConnect { localSource.ensureScanned() }
    }
}
