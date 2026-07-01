package com.viperplayer.data.source

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.ComponentName
import android.os.IBinder
import com.viperplayer.data.mapper.PluginMapper.toDomain
import com.viperplayer.data.mapper.PluginMapper.toDomainDetail
import com.viperplayer.data.mapper.PluginMapper.toDomainRef
import com.viperplayer.data.mapper.PluginMapper.toMediaType
import com.viperplayer.data.plugin.ConnectedPlugin
import com.viperplayer.data.preferences.PluginPreferences
import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.ArtistDetail
import com.viperplayer.domain.model.BrowseCategory
import com.viperplayer.domain.model.HomeContent
import com.viperplayer.domain.model.Lyrics
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.PagedResult
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.SearchFilter
import com.viperplayer.domain.model.SearchResult
import com.viperplayer.domain.model.SearchSuggestions
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.repository.AudioQuality
import com.viperplayer.domain.repository.SettingsRepository
import com.viperplayer.plugin.host.HostBridge
import com.viperplayer.plugin.host.PluginConnection
import com.viperplayer.plugin.host.PluginDiscovery
import com.viperplayer.plugin.host.ResolvedStream
import com.viperplayer.plugin.host.DiscoveredPlugin
import com.viperplayer.plugin.host.SourceClient
import com.viperplayer.plugin.model.LyricsRequest
import com.viperplayer.plugin.model.MediaType
import com.viperplayer.plugin.model.PageRequest
import com.viperplayer.plugin.model.PluginErrorCode
import com.viperplayer.plugin.model.PluginException
import com.viperplayer.plugin.model.SearchRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Discovers, connects, and talks to plugin plugins through the plugin SDK.
 *
 * Discovery and the bind/unbind/retry lifecycle are owned here; the protocol (handshake, verb
 * dispatch, paging, cancellation) is delegated to the SDK's [PluginConnection]. Operations return
 * domain models, already namespaced by plugin id.
 */
@Singleton
class PluginDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val pluginPreferences: PluginPreferences,
    private val settingsRepository: SettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _discoveredPlugins = MutableStateFlow<Map<String, DiscoveredPlugin>>(emptyMap())
    val discoveredPlugins: StateFlow<Map<String, DiscoveredPlugin>> = _discoveredPlugins.asStateFlow()

    private val _connectedPlugins = MutableStateFlow<Map<String, ConnectedPlugin>>(emptyMap())
    val connectedPlugins: StateFlow<Map<String, ConnectedPlugin>> = _connectedPlugins.asStateFlow()

    /** Set of plugin ids the user has explicitly disabled. */
    val disabledPlugins: Flow<Set<String>> get() = pluginPreferences.disabledPlugins

    private val ongoingConnections = mutableMapOf<String, ServiceConnection>()
    private val connectionMutex = Mutex()

    private val hostBridge = HostBridgeImpl()

    private val hostVersion: String by lazy {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "1"
    }

    init {
        scope.launch {
            callbackFlow {
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        if (context == null || intent == null) return
                        scope.launch {
                            runCatching { trySend(discoverPlugins()) }
                                .onFailure { Timber.w(it, "Error discovering plugins") }
                        }
                    }
                }
                val filter = IntentFilter().apply {
                    addAction(Intent.ACTION_PACKAGE_ADDED)
                    addAction(Intent.ACTION_PACKAGE_REPLACED)
                    addAction(Intent.ACTION_PACKAGE_REMOVED)
                    addAction(Intent.ACTION_PACKAGE_CHANGED)
                    addDataScheme("package")
                }
                context.registerReceiver(receiver, filter)
                trySend(discoverPlugins())
                awaitClose { context.unregisterReceiver(receiver) }
            }.collect { discovered -> _discoveredPlugins.update { discovered } }
        }

        scope.launch {
            combine(discoveredPlugins, pluginPreferences.disabledPlugins) { discovered, disabled ->
                discovered to disabled
            }.collect { (discovered, disabled) ->
                val connected = _connectedPlugins.value

                connected.keys
                    .filter { it !in discovered || it in disabled }
                    .forEach { runCatching { disconnectPlugin(it) } }

                discovered.keys
                    .filter { it !in connected && it !in disabled }
                    .forEach { runCatching { connectPlugin(it) } }
            }
        }
    }

    private suspend fun discoverPlugins(): Map<String, DiscoveredPlugin> = withContext(Dispatchers.IO) {
        PluginDiscovery.discover(context).associateBy { it.id }
    }

    suspend fun refreshPlugins() {
        _discoveredPlugins.value = discoverPlugins()
    }

    suspend fun connectPlugin(pluginId: String) {
        val discovered = _discoveredPlugins.value[pluginId] ?: return

        var serviceConnection: ServiceConnection? = null
        serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder) {
                scope.launch { onBinderAvailable(discovered, binder, serviceConnection!!) }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                onPluginDisconnected(pluginId)
            }

            override fun onBindingDied(name: ComponentName?) {
                onPluginConnectionFailed(pluginId, retry = true)
            }

            override fun onNullBinding(name: ComponentName?) {
                onPluginConnectionFailed(pluginId, retry = false)
            }
        }
        val connection: ServiceConnection = serviceConnection

        connectionMutex.withLock {
            if (_connectedPlugins.value.containsKey(pluginId) || ongoingConnections.containsKey(pluginId)) return
            ongoingConnections[pluginId] = connection
        }

        val intent = Intent().apply { component = discovered.component }
        try {
            val bound = withContext(Dispatchers.Main) {
                context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            }
            if (!bound) {
                // Android still requires unbindService even when bindService returns false, to
                // release the connection record.
                withContext(Dispatchers.Main) { runCatching { context.unbindService(connection) } }
                connectionMutex.withLock { ongoingConnections.remove(pluginId) }
                onPluginConnectionFailed(pluginId, retry = false)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error binding to plugin service: ${intent.component}")
            connectionMutex.withLock { ongoingConnections.remove(pluginId) }
            onPluginConnectionFailed(pluginId, retry = false)
        }
    }

    private suspend fun onBinderAvailable(
        discovered: DiscoveredPlugin,
        binder: IBinder,
        serviceConnection: ServiceConnection,
    ) {
        try {
            val pluginConnection = PluginConnection.connect(
                pluginId = discovered.id,
                binder = binder,
                hostBridge = hostBridge,
                scope = scope,
                hostVersion = hostVersion,
            )

            val connected = ConnectedPlugin(pluginConnection, serviceConnection)
            // Decide acceptance atomically with the insertion: if a disconnect/disable raced the
            // handshake (so our ongoing entry was removed/replaced), don't publish a stale, dead
            // connection over it.
            val accepted = connectionMutex.withLock {
                val stillWanted = ongoingConnections[discovered.id] === serviceConnection &&
                    _discoveredPlugins.value.containsKey(discovered.id) &&
                    !pluginPreferences.isDisabledSync(discovered.id)
                if (stillWanted) {
                    _connectedPlugins.update { it + (discovered.id to connected) }
                    ongoingConnections.remove(discovered.id)
                }
                stillWanted
            }
            if (!accepted) {
                runCatching { pluginConnection.shutdown() }
                onPluginConnectionFailed(discovered.id, retry = false)
                return
            }
            Timber.d("Plugin connected: ${connected.info.name} (${connected.capabilities})")
        } catch (e: Exception) {
            Timber.e(e, "Error connecting to plugin: ${discovered.id}")
            onPluginConnectionFailed(discovered.id, retry = false)
        }
    }

    private fun onPluginConnectionFailed(pluginId: String, retry: Boolean) {
        scope.launch {
            val serviceConnection = connectionMutex.withLock {
                val sc = _connectedPlugins.value[pluginId]?.serviceConnection ?: ongoingConnections[pluginId]
                _connectedPlugins.update { it - pluginId }
                ongoingConnections.remove(pluginId)
                sc
            }
            serviceConnection?.let {
                withContext(Dispatchers.Main) { runCatching { context.unbindService(it) } }
            }
            if (retry) {
                delay(1000)
                runCatching { connectPlugin(pluginId) }
            }
        }
    }

    private fun onPluginDisconnected(pluginId: String) {
        scope.launch {
            // The service died: drop it AND unbind, otherwise the ServiceConnection stays registered
            // (a leaked binding with no later way to release it).
            val serviceConnection = connectionMutex.withLock {
                val sc = _connectedPlugins.value[pluginId]?.serviceConnection ?: ongoingConnections[pluginId]
                _connectedPlugins.update { it - pluginId }
                ongoingConnections.remove(pluginId)
                sc
            }
            serviceConnection?.let {
                withContext(Dispatchers.Main) { runCatching { context.unbindService(it) } }
            }
        }
    }

    suspend fun disconnectPlugin(pluginId: String) {
        val (ongoing, connected) = connectionMutex.withLock {
            ongoingConnections[pluginId] to _connectedPlugins.value[pluginId]
        }
        ongoing?.let {
            withContext(Dispatchers.Main) { runCatching { context.unbindService(it) } }
            connectionMutex.withLock { ongoingConnections.remove(pluginId) }
        }
        if (connected == null) return

        runCatching { connected.connection.shutdown() }
        withContext(Dispatchers.Main) { runCatching { context.unbindService(connected.serviceConnection) } }
        connectionMutex.withLock { _connectedPlugins.update { it - pluginId } }
    }

    suspend fun disconnectAll() {
        val ids = _connectedPlugins.value.keys.toList()
        ids.forEach { runCatching { disconnectPlugin(it) } }
    }

    suspend fun enablePlugin(pluginId: String) {
        pluginPreferences.setDisabled(pluginId, false)
        connectPlugin(pluginId)
    }

    suspend fun disablePlugin(pluginId: String) {
        pluginPreferences.setDisabled(pluginId, true)
        disconnectPlugin(pluginId)
    }

    // ---- operations ----

    private fun plugin(pluginId: String): ConnectedPlugin =
        _connectedPlugins.value[pluginId]
            ?: throw PluginException(PluginErrorCode.INTERNAL, "Plugin not connected: $pluginId")

    private fun source(pluginId: String): SourceClient =
        plugin(pluginId).client.source
            ?: throw PluginException(PluginErrorCode.UNSUPPORTED, "Plugin is not a source: $pluginId")

    /**
     * Like [source], but waits (briefly) for the plugin to finish connecting instead of failing
     * immediately. On app start the player can try to prepare a restored track before plugin
     * discovery/handshake has completed (a ~250ms race); without this the track errored instantly and
     * stayed errored ("pauses immediately on play"). Connection normally lands well within the window.
     */
    private suspend fun awaitSource(pluginId: String, timeoutMs: Long = 8_000): SourceClient {
        val connected = _connectedPlugins.value[pluginId]
            ?: withTimeoutOrNull(timeoutMs) {
                _connectedPlugins.mapNotNull { it[pluginId] }.first()
            }
            ?: throw PluginException(PluginErrorCode.INTERNAL, "Plugin not connected: $pluginId")
        return connected.client.source
            ?: throw PluginException(PluginErrorCode.UNSUPPORTED, "Plugin is not a source: $pluginId")
    }

    suspend fun search(
        pluginId: String,
        query: String,
        filter: SearchFilter?,
        cursor: String?,
        limit: Int,
    ): Result<SearchResult> = runCatching {
        val types = filter?.let { setOf(it.toMediaType()) } ?: emptySet()
        source(pluginId).search(SearchRequest(query, types, PageRequest(cursor, limit))).toDomain(pluginId)
    }.onFailure { Timber.e(it, "search failed for $pluginId") }

    fun getSearchSuggestions(query: String): Flow<List<Result<SearchSuggestions>>> = channelFlow {
        val results = mutableListOf<Result<SearchSuggestions>>()
        _connectedPlugins.value.forEach { (pluginId, plugin) ->
            val sourceClient = plugin.client.source ?: return@forEach
            launch {
                val result = runCatching { sourceClient.getSearchSuggestions(query).toDomain(pluginId) }
                    .onFailure { Timber.e(it, "suggestions failed for $pluginId") }
                synchronized(results) {
                    results.add(result)
                    trySend(results.toList())
                }
            }
        }
    }

    suspend fun getBrowseCategories(pluginId: String, cursor: String?, limit: Int): Result<PagedResult<BrowseCategory>> =
        runCatching {
            source(pluginId).getBrowseCategories(PageRequest(cursor, limit)).toDomain { it.toDomain(pluginId) }
        }.onFailure { Timber.e(it, "browse categories failed for $pluginId") }

    suspend fun getCategoryContents(pluginId: String, categoryId: String, cursor: String?, limit: Int): Result<SearchResult> =
        runCatching {
            source(pluginId).getCategoryContents(categoryId, PageRequest(cursor, limit)).toDomain(pluginId)
        }.onFailure { Timber.e(it, "category contents failed for $pluginId") }

    suspend fun getLibrarySongs(pluginId: String, cursor: String?, limit: Int): Result<PagedResult<Song>> =
        runCatching {
            source(pluginId).getLibrarySongs(PageRequest(cursor, limit)).toDomain { it.toDomain(pluginId) }
        }.onFailure { Timber.e(it, "library songs failed for $pluginId") }

    suspend fun getLibraryAlbums(pluginId: String, cursor: String?, limit: Int): Result<PagedResult<Album>> =
        runCatching {
            source(pluginId).getLibraryAlbums(PageRequest(cursor, limit)).toDomain { it.toDomain(pluginId) }
        }.onFailure { Timber.e(it, "library albums failed for $pluginId") }

    suspend fun getLibraryArtists(pluginId: String, cursor: String?, limit: Int): Result<PagedResult<Artist>> =
        runCatching {
            source(pluginId).getLibraryArtists(PageRequest(cursor, limit)).toDomain { it.toDomainRef(pluginId) }
        }.onFailure { Timber.e(it, "library artists failed for $pluginId") }

    suspend fun getLibraryPlaylists(pluginId: String, cursor: String?, limit: Int): Result<PagedResult<Playlist>> =
        runCatching {
            source(pluginId).getLibraryPlaylists(PageRequest(cursor, limit)).toDomain { it.toDomain(pluginId) }
        }.onFailure { Timber.e(it, "library playlists failed for $pluginId") }

    suspend fun getSong(id: MediaId): Result<Song> = runCatching {
        source(id.pluginId).getSong(id.sourceId).toDomain(id.pluginId)
    }.onFailure { Timber.e(it, "getSong failed for $id") }

    suspend fun getAlbum(id: MediaId): Result<Album> = runCatching {
        source(id.pluginId).getAlbum(id.sourceId).toDomain(id.pluginId)
    }.onFailure { Timber.e(it, "getAlbum failed for $id") }

    suspend fun getArtist(id: MediaId): Result<ArtistDetail> = runCatching {
        source(id.pluginId).getArtist(id.sourceId).toDomainDetail(id.pluginId)
    }.onFailure { Timber.e(it, "getArtist failed for $id") }

    suspend fun getPlaylist(id: MediaId): Result<Playlist> = runCatching {
        source(id.pluginId).getPlaylist(id.sourceId).toDomain(id.pluginId)
    }.onFailure { Timber.e(it, "getPlaylist failed for $id") }

    suspend fun getLyrics(
        id: MediaId,
        title: String?,
        artist: String?,
        album: String?,
        durationMs: Long?
    ): Result<Lyrics?> = runCatching {
        // Lyrics is an optional capability — client.lyrics is null when the plugin doesn't advertise it.
        plugin(id.pluginId).client.lyrics?.getLyrics(
            LyricsRequest(
                songId = id.sourceId,
                title = title,
                artist = artist,
                album = album,
                durationMs = durationMs
            )
        )?.toDomain()
    }.onFailure { Timber.e(it, "getLyrics failed for $id") }

    suspend fun getArtistSongs(artistId: MediaId, cursor: String?, limit: Int): Result<PagedResult<Song>> =
        runCatching {
            source(artistId.pluginId).getArtistSongs(artistId.sourceId, PageRequest(cursor, limit))
                .toDomain { it.toDomain(artistId.pluginId) }
        }.onFailure { Timber.e(it, "artist songs failed for $artistId") }

    suspend fun getArtistAlbums(artistId: MediaId, cursor: String?, limit: Int): Result<PagedResult<Album>> =
        runCatching {
            source(artistId.pluginId).getArtistAlbums(artistId.sourceId, PageRequest(cursor, limit))
                .toDomain { it.toDomain(artistId.pluginId) }
        }.onFailure { Timber.e(it, "artist albums failed for $artistId") }

    suspend fun getPlaylistSongs(playlistId: MediaId, cursor: String?, limit: Int): Result<PagedResult<Song>> =
        runCatching {
            source(playlistId.pluginId).getPlaylistSongs(playlistId.sourceId, PageRequest(cursor, limit))
                .toDomain { it.toDomain(playlistId.pluginId) }
        }.onFailure { Timber.e(it, "playlist songs failed for $playlistId") }

    /** Radio/autoplay seed: songs related to [songId], used to keep the queue going. */
    suspend fun getRelatedSongs(songId: MediaId): Result<PagedResult<Song>> =
        runCatching {
            source(songId.pluginId).getRelatedSongs(songId.sourceId)
                .toDomain { it.toDomain(songId.pluginId) }
        }.onFailure { Timber.e(it, "related songs failed for $songId") }

    suspend fun getHomeContent(pluginId: String): Result<HomeContent> = runCatching {
        source(pluginId).getHome().toDomain(pluginId)
    }.onFailure { Timber.e(it, "home content failed for $pluginId") }

    suspend fun filterSection(pluginId: String, sectionId: String, filterKey: String): Result<SearchResult> =
        runCatching {
            source(pluginId).filterSection(sectionId, filterKey).toDomain(pluginId)
        }.onFailure { Timber.e(it, "filter section failed for $pluginId") }

    /** Resolve a playable stream for [mediaId]. The returned [ResolvedStream] may carry a live FD. */
    suspend fun getStream(mediaId: MediaId, isVideo: Boolean): Result<ResolvedStream> = runCatching {
        val type = if (isVideo) MediaType.VIDEO else MediaType.SONG
        val maxBitrateKbps = maxBitrateKbpsFor(settingsRepository.audioQuality.first())
        awaitSource(mediaId.pluginId).resolveStream(mediaId.sourceId, type, maxBitrateKbps)
    }.onFailure { Timber.e(it, "getStream failed for $mediaId") }

    /** Map the user's audio-quality setting to a max bitrate (kbps) the plugin caps to; null = highest. */
    private fun maxBitrateKbpsFor(quality: AudioQuality): Int? = when (quality) {
        AudioQuality.LOW -> 128
        AudioQuality.MEDIUM -> 256
        AudioQuality.HIGH -> 320
        AudioQuality.LOSSLESS -> null
    }

    private inner class HostBridgeImpl : HostBridge {
        override fun onContentChanged(pluginId: String) {
            Timber.d("Plugin $pluginId reported content changed")
        }

        override fun onError(pluginId: String, code: Int, message: String?) {
            Timber.e("Plugin $pluginId error: code=$code, message=$message")
        }
    }
}
