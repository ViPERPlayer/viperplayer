package com.viperplayer.data.plugin

import android.content.ServiceConnection
import com.viperplayer.data.mapper.PluginMapper.toDomainCapabilities
import com.viperplayer.data.mapper.PluginMapper.toDomainInfo
import com.viperplayer.domain.model.PluginCapabilities
import com.viperplayer.domain.model.PluginInfo
import com.viperplayer.plugin.host.PluginClient
import com.viperplayer.plugin.host.PluginConnection

/**
 * A handshaken plugin the host is bound to: the SDK [PluginConnection] plus the precomputed domain
 * view (info + capabilities) derived from its manifest.
 */
class ConnectedPlugin(
    val connection: PluginConnection,
    val serviceConnection: ServiceConnection,
) {
    val id: String get() = connection.pluginId
    val client: PluginClient get() = connection.client
    val info: PluginInfo = connection.manifest.toDomainInfo()
    val capabilities: PluginCapabilities = connection.manifest.toDomainCapabilities()
}
