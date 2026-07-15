package com.viperplayer.local

import android.content.Context
import com.viperplayer.local.data.LocalMediaScanner
import com.viperplayer.plugin.author.ViperPluginService
import com.viperplayer.plugin.author.pluginRegistration
import com.viperplayer.plugin.model.ActionType
import com.viperplayer.plugin.model.LyricsCapabilities
import com.viperplayer.plugin.model.PluginStatus
import com.viperplayer.plugin.model.RequiredAction
import com.viperplayer.plugin.model.SourceCapabilities

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
        // Embedded (SYLT/USLT, Vorbis LYRICS/UNSYNCEDLYRICS) + sidecar (.lrc/.ttml/.srt) lyrics for
        // local files. Synced is supported when the file/sidecar carries timestamps.
        lyrics(
            LocalLyricsProvider(context, localSource),
            LyricsCapabilities(synced = true),
        )
        settingsActivity(LocalSettingsActivity::class.java.name)
        // Report the missing audio permission as a required action. The plugin is embedded in the
        // host, so the host requests [RequiredAction.permission] directly, no activity hop needed.
        status {
            PluginStatus(
                actions = if (!localSource.hasAudioPermission()) {
                    listOf(
                        RequiredAction(
                            id = "audio-permission",
                            type = ActionType.PERMISSION,
                            title = context.getString(R.string.local_permission_title),
                            description = context.getString(R.string.local_permission_body),
                            activity = LocalSettingsActivity::class.java.name,
                            permission = LocalMediaScanner.requiredPermission,
                        )
                    )
                } else {
                    emptyList()
                }
            )
        }
        onConnect { host ->
            LocalPluginRuntime.host = host
            // Warm the MediaStore cache as soon as the host connects.
            localSource.ensureScanned()
        }
        onShutdown { LocalPluginRuntime.host = null }
    }
}
