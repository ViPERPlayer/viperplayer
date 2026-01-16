package com.viperplayer.data.source

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.RemoteException
import com.viperplayer.data.mapper.SearchSuggestionsMapper.toDomain
import com.viperplayer.data.plugin.ConnectedPlugin
import com.viperplayer.data.plugin.handler.PluginHandlerFactory
import com.viperplayer.data.preferences.PluginPreferences
import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.BrowseCategory
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.PagedResult
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.PluginInfo
import com.viperplayer.domain.model.SearchSuggestions
import com.viperplayer.domain.model.Song
import com.viperplayer.plugin.IConnectCallback
import com.viperplayer.plugin.PluginConstants
import com.viperplayer.plugin.v1.IHostCallbackV1
import com.viperplayer.plugin.v1.IViperPluginV1
import com.viperplayer.plugin.v1.SearchFilter
import com.viperplayer.plugin.v1.StreamSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.viperplayer.plugin.v1.PlayerState as AidlPlayerState
import com.viperplayer.plugin.v1.SearchResult as AidlSearchResult
import com.viperplayer.plugin.v1.Song as AidlSong

/**
 * Data source for plugin operations.
 * Handles plugin discovery, connection, and communication.
 */
@Singleton
class PluginDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val pluginPreferences: PluginPreferences,
    private val handlerFactory: PluginHandlerFactory
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    private val _discoveredPlugins = MutableStateFlow<Map<String, DiscoveredPluginInfo>>(emptyMap())
    val discoveredPlugins: StateFlow<Map<String, DiscoveredPluginInfo>> = _discoveredPlugins.asStateFlow()
    
    private val _connectedPlugins = MutableStateFlow<Map<String, ConnectedPlugin>>(emptyMap())
    val connectedPlugins: StateFlow<Map<String, ConnectedPlugin>> = _connectedPlugins.asStateFlow()
    
    // Track ongoing connection attempts to prevent duplicates
    // Protected by connectionMutex for thread safety
    private val ongoingConnections = mutableMapOf<String, ServiceConnection>()
    private val connectionMutex = Mutex()
    
    private val hostCallback = HostCallbackImpl()

    init {
        scope.launch {
            callbackFlow {
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        if (context == null || intent == null) return
                        scope.launch { 
                            try {
                                Timber.d("Triggering plugin discovery from broadcast receiver")
                                trySend(discoverPlugins())
                            } catch (e: Exception) {
                                Timber.w(e, "Error discovering plugins in broadcast receiver")
                            }
                        }
                    }
                }

                val intentFilter = IntentFilter().apply {
                    addAction(Intent.ACTION_PACKAGE_ADDED)
                    addAction(Intent.ACTION_PACKAGE_REPLACED)
                    addAction(Intent.ACTION_PACKAGE_REMOVED)
                    addAction(Intent.ACTION_PACKAGE_CHANGED)
                    addDataScheme("package")
                }
                
                Timber.d("Registering BroadcastReceiver for package events")
                context.registerReceiver(receiver, intentFilter)

                trySend(discoverPlugins())

                awaitClose {
                    context.unregisterReceiver(receiver)
                }
            }.collect { discoveredPlugins ->
                // Use update for atomic state modification
                _discoveredPlugins.update { discoveredPlugins }
            }
        }

        scope.launch {
            // Combine discovered plugins with disabled state to only connect enabled plugins
            combine(
                discoveredPlugins,
                pluginPreferences.disabledPlugins
            ) { discovered, disabled ->
                discovered to disabled
            }.collect { (discovered, disabled) ->
                // Get current state atomically
                val currentConnected = _connectedPlugins.value
                val currentDiscovered = discovered
                
                // Disconnect plugins that are no longer discovered or disabled
                val toDisconnect = currentConnected.keys.filter { pluginId ->
                    !currentDiscovered.containsKey(pluginId) || pluginId in disabled
                }
                
                toDisconnect.forEach { pluginId ->
                    try {
                        disconnectPlugin(pluginId)
                    } catch (e: Exception) {
                        Timber.w(e, "Error disconnecting plugin: $pluginId")
                    }
                }

                // Connect new plugins that are discovered and not disabled, sequentially to avoid race conditions
                val toConnect = currentDiscovered.keys.filter { pluginId ->
                    pluginId !in currentConnected && pluginId !in disabled
                }
                
                toConnect.forEach { pluginId ->
                    try {
                        connectPlugin(pluginId)
                    } catch (e: Exception) {
                        Timber.w(e, "Error connecting plugin: $pluginId")
                    }
                }
            }
        }
    }

    private fun onPluginConnectionFailed(pluginId: String, retry: Boolean) {
        scope.launch {
            val connection = connectionMutex.withLock {
                // Get connection from either connected or ongoing, then clean up atomically
                val connection = _connectedPlugins.value[pluginId]?.connection
                    ?: ongoingConnections[pluginId]
                // Remove from both maps atomically
                _connectedPlugins.update { it - pluginId }
                ongoingConnections.remove(pluginId)
                connection
            }

            // Unbind outside the lock to avoid holding it during I/O
            connection?.let {
                withContext(Dispatchers.Main) {
                    try {
                        context.unbindService(it)
                    } catch (e: Exception) {
                        Timber.w(e, "Error unbinding old connection for plugin: $pluginId")
                    }
                }
            }

            if (retry) {
                // Delay retry to avoid immediate reconnection loops
                delay(1000)
                try {
                    connectPlugin(pluginId)
                } catch (e: Exception) {
                    Timber.w(e, "Error retrying connection for plugin: $pluginId")
                }
            }
        }
    }

    private fun onPluginDisconnected(pluginId: String) {
        scope.launch {
            connectionMutex.withLock {
                _connectedPlugins.update { it - pluginId }
                ongoingConnections.remove(pluginId)
            }
        }
    }
    
    /**
     * Discover all installed plugins.
     * Plugin ID is the package name.
     */
    suspend fun discoverPlugins(): Map<String, DiscoveredPluginInfo> = withContext(Dispatchers.IO) {
        Timber.d("Discovering plugins")

        // Retrieve all installed packages with the plugin intent
        val intent = Intent(PluginConstants.ACTION_PLUGIN_SERVICE)
        val resolveInfos = context.packageManager.queryIntentServices(
            intent,
            PackageManager.GET_META_DATA
        )
        Timber.d("Found ${resolveInfos.size} services matching plugin intent")

        resolveInfos.mapNotNull { resolveInfo ->
            val serviceInfo = resolveInfo.serviceInfo
            if (serviceInfo == null) {
                Timber.w("ResolveInfo has no serviceInfo")
                return@mapNotNull null
            }

            val metaData = serviceInfo.applicationInfo.metaData
            if (metaData == null) {
                Timber.w("ApplicationInfo has no metadata")
                return@mapNotNull null
            }

            // Use package name as plugin ID
            val pluginId = serviceInfo.packageName

            // Get plugin info from metadata and package manager
            val pluginName = metaData.getString(PluginConstants.META_PLUGIN_NAME) ?: serviceInfo.loadLabel(context.packageManager).toString()
            val description = metaData.getString(PluginConstants.META_PLUGIN_DESCRIPTION)

            // Get version from package info
            val version = try {
                val packageInfo = context.packageManager.getPackageInfo(pluginId, 0)
                packageInfo.versionName ?: "N/A"
            } catch (e: Exception) {
                Timber.w(e, "Failed to get version for plugin: $pluginId")
                "N/A"
            }

            val componentName = ComponentName(serviceInfo.packageName, serviceInfo.name)
            Timber.d("Discovered plugin: id=$pluginId, name=$pluginName, version=$version, component=$componentName")

            pluginId to DiscoveredPluginInfo(
                id = pluginId,
                name = pluginName,
                description = description,
                version = version,
                componentName = componentName
            )
        }.toMap()
    }

    suspend fun refreshPlugins() {
        _discoveredPlugins.value = discoverPlugins()
    }
    
    /**
     * Connect to a plugin.
     * Thread-safe and prevents concurrent connection attempts for the same plugin.
     */
    suspend fun connectPlugin(pluginId: String) {
        // Find the plugin first (read-only operation, no race condition)
        val discovered = _discoveredPlugins.value[pluginId]
        if (discovered == null) {
            Timber.w("Plugin not discovered: $pluginId")
            return
        }

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder) {
                Timber.d("onServiceConnected() called: name=$name for plugin: $pluginId")

                val descriptor = try {
                    binder.interfaceDescriptor
                } catch (e: RemoteException) {
                    Timber.w(e, "Error getting interface descriptor for plugin: $pluginId")
                    onPluginConnectionFailed(pluginId = pluginId, retry = false)
                    return
                }

                try {
                    // Use handler factory to determine API version and create handler
                    val apiVersion = handlerFactory.getApiVersion(binder)
                    if (apiVersion == null) {
                        Timber.w("Unknown interface descriptor: $descriptor for plugin: $pluginId")
                        onPluginConnectionFailed(pluginId = pluginId, retry = false)
                        return
                    }
                    
                    when (apiVersion) {
                        1 -> connectPluginV1(discovered, binder, this)
                        else -> {
                            Timber.w("Unsupported API version: $apiVersion for plugin: $pluginId")
                            onPluginConnectionFailed(pluginId = pluginId, retry = false)
                            return
                        }
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Error connecting to plugin: $pluginId")
                    onPluginConnectionFailed(pluginId = pluginId, retry = false)
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                Timber.w("onServiceDisconnected() called: $name for plugin: $pluginId")
                onPluginDisconnected(pluginId = pluginId)
            }

            override fun onBindingDied(name: ComponentName?) {
                Timber.e("onBindingDied() called: $name for plugin: $pluginId")
                onPluginConnectionFailed(pluginId = pluginId, retry = true)
            }

            override fun onNullBinding(name: ComponentName?) {
                Timber.e("onNullBinding() called: $name for plugin: $pluginId")
                onPluginConnectionFailed(pluginId = pluginId, retry = false)
            }
        }

        // Atomically check and add to ongoingConnections to prevent race conditions
        connectionMutex.withLock {
            // Check if already connected (double-check after acquiring lock)
            if (_connectedPlugins.value.containsKey(pluginId)) {
                Timber.d("Plugin $pluginId already connected")
                return
            }

            // Check if connection already in progress
            if (ongoingConnections.containsKey(pluginId)) {
                Timber.d("Plugin $pluginId already connecting")
                return
            }

            // Add to ongoingConnections atomically with the check
            ongoingConnections[pluginId] = connection
        }

        // Create intent with explicit component
        val intent = Intent().apply {
            component = discovered.componentName
        }

        try {
            Timber.d("Binding to plugin service: ${intent.component}")
            val bound = withContext(Dispatchers.Main) {
                context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            }
            Timber.d("bindService() returned: $bound for plugin: $pluginId")

            if (!bound) {
                Timber.e("Failed to bind to plugin service: ${intent.component}")
                connectionMutex.withLock {
                    ongoingConnections.remove(pluginId)
                }
                onPluginConnectionFailed(pluginId = pluginId, retry = false)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error binding to plugin service: ${intent.component}")
            connectionMutex.withLock {
                ongoingConnections.remove(pluginId)
            }
            onPluginConnectionFailed(pluginId = pluginId, retry = false)
        }
    }

    private fun connectPluginV1(
        discovered: DiscoveredPluginInfo,
        binder: IBinder,
        connection: ServiceConnection
    ) {
        val service = IViperPluginV1.Stub.asInterface(binder)
            ?: throw IllegalStateException("Failed to get IViperPluginV1 interface from binder for plugin: ${discovered.id}")

        Timber.d("Calling connect() on plugin service: ${discovered.id}")

        scope.launch {
            try {
                suspendCancellableCoroutine { continuation ->
                    service.onConnect(hostCallback, object : IConnectCallback.Stub() {
                        override fun onSuccess() {
                            Timber.d("Plugin connect() callback succeeded: ${discovered.id}")
                            if (continuation.isActive) {
                                continuation.resume(Unit)
                            }
                        }

                        override fun onFailure(errorCode: Int, message: String?) {
                            Timber.e("Plugin connect() callback failed: $errorCode, $message")
                            if (continuation.isActive) {
                                continuation.resumeWithException(
                                    PluginException(errorCode, message ?: "Connection failed")
                                )
                            }
                        }
                    })
                }

                // Double-check that plugin is still in discovered list and not disabled before connecting
                val isStillDiscovered = _discoveredPlugins.value.containsKey(discovered.id)
                val isDisabled = pluginPreferences.isDisabledSync(discovered.id)
                
                if (!isStillDiscovered || isDisabled) {
                    Timber.d("Plugin ${discovered.id} no longer discovered or disabled, aborting connection")
                    onPluginConnectionFailed(discovered.id, retry = false)
                    return@launch
                }

                // Create PluginInfo from discovered plugin info (host app creates it)
                val pluginInfo = PluginInfo(
                    id = discovered.id,
                    name = discovered.name,
                    version = discovered.version,
                    apiVersion = 1,
                    description = discovered.description,
                    author = null, // Can be added to metadata if needed
                )

                // Create handler using factory
                val handler = handlerFactory.createHandler(discovered.id, binder)
                
                // Verify handler API version matches
                require(handler.apiVersion == 1) {
                    "Handler API version mismatch: expected 1, got ${handler.apiVersion}"
                }
                
                // Create connected plugin using the handler
                val connectedData = ConnectedPlugin(
                    info = pluginInfo,
                    connection = connection,
                    handler = handler
                )

                // Atomically add to connected plugins and remove from ongoing connections
                connectionMutex.withLock {
                    // Double-check again inside the lock
                    if (!_discoveredPlugins.value.containsKey(discovered.id)) {
                        Timber.d("Plugin ${discovered.id} no longer discovered, aborting connection")
                        ongoingConnections.remove(discovered.id)
                        return@launch
                    }
                    _connectedPlugins.update { it + (discovered.id to connectedData) }
                    ongoingConnections.remove(discovered.id)
                }

                Timber.d("Plugin connected successfully: ${pluginInfo.name}")
            } catch (e: Exception) {
                Timber.e(e, "Error during plugin connection: ${discovered.id}")
                onPluginConnectionFailed(discovered.id, retry = false)
            }
        }
    }
    
    /**
     * Disconnect from a plugin.
     * Thread-safe and ensures proper cleanup.
     */
    suspend fun disconnectPlugin(pluginId: String) {
        Timber.d("Disconnecting plugin: $pluginId")
        
        // Get connections atomically but don't remove from maps yet
        // This prevents race conditions where another thread might try to use the plugin
        val (ongoingConnection, connected) = connectionMutex.withLock {
            val ongoingConnection = ongoingConnections[pluginId]
            val connected = _connectedPlugins.value[pluginId]
            ongoingConnection to connected
        }

        // Unbind ongoing connection attempts first
        ongoingConnection?.let {
            Timber.d("Unbinding ongoing connection attempt for plugin: $pluginId")
            try {
                withContext(Dispatchers.Main) {
                    context.unbindService(it)
                }
            } catch (e: Exception) {
                Timber.w(e, "Error unbinding ongoing connection for plugin: $pluginId")
            }
            // Remove from ongoing connections after unbinding
            connectionMutex.withLock {
                ongoingConnections.remove(pluginId)
            }
        }

        if (connected == null) {
            Timber.w("Plugin $pluginId not connected, nothing to disconnect")
            return
        }

        // Call disconnect() on the handler first, then unbind
        // This ensures the plugin knows it's being disconnected
        try {
            Timber.d("Calling disconnect() on plugin handler: $pluginId")
            connected.handler.disconnect()
        } catch (e: Exception) {
            Timber.e(e, "Error calling disconnect() on plugin: $pluginId")
            // Continue with unbinding even if disconnect() fails
        }

        // Unbind the service
        try {
            Timber.d("Unbinding service for plugin: $pluginId")
            withContext(Dispatchers.Main) {
                context.unbindService(connected.connection)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error unbinding service for plugin: $pluginId")
        }
        
        // Finally, remove from connected plugins map after unbinding is complete
        // This ensures the plugin is fully disconnected before it's removed from the map
        connectionMutex.withLock {
            _connectedPlugins.update { it - pluginId }
        }
        
        Timber.d("Plugin $pluginId disconnected and removed from connected plugins")
    }
    
    /**
     * Disconnect from all plugins.
     * Thread-safe and ensures proper cleanup.
     */
    suspend fun disconnectAll() {
        Timber.d("Disconnecting all plugins")

        // Get all plugin IDs and ongoing connections atomically
        val (pluginIds, ongoingConnectionsToUnbind) = connectionMutex.withLock {
            val ids = _connectedPlugins.value.keys.toList()
            val ongoing = ongoingConnections.toMap() // Create a copy
            ongoingConnections.clear() // Clear immediately to prevent new connections
            ids to ongoing
        }

        // Unbind ongoing connections first (these are handled separately to avoid double-unbind)
        ongoingConnectionsToUnbind.forEach { (pluginId, connection) ->
            try {
                withContext(Dispatchers.Main) {
                    context.unbindService(connection)
                }
            } catch (e: Exception) {
                Timber.w(e, "Error unbinding ongoing connection for plugin: $pluginId")
            }
        }

        // Disconnect all connected plugins
        // disconnectPlugin will handle its own unbinding, so we don't double-unbind
        pluginIds.forEach { pluginId ->
            try {
                disconnectPlugin(pluginId)
            } catch (e: Exception) {
                Timber.w(e, "Error disconnecting plugin: $pluginId")
            }
        }

        Timber.d("All plugins disconnected")
    }
    
    /**
     * Enable a plugin (connects it automatically).
     */
    suspend fun enablePlugin(pluginId: String) {
        Timber.d("Enabling plugin: $pluginId")
        pluginPreferences.setDisabled(pluginId, false)
        connectPlugin(pluginId)
    }
    
    /**
     * Disable a plugin (disconnects it).
     */
    suspend fun disablePlugin(pluginId: String) {
        Timber.d("Disabling plugin: $pluginId")
        pluginPreferences.setDisabled(pluginId, true)
        disconnectPlugin(pluginId)
    }
    
    /**
     * Get a connected plugin by ID (internal use only).
     * Throws PluginException if plugin is not connected.
     */
    private fun getPlugin(pluginId: String): ConnectedPlugin {
        return _connectedPlugins.value[pluginId]
            ?: throw PluginException(0, "Plugin not connected: $pluginId")
    }
    
    /**
     * Search in a specific plugin.
     */
    suspend fun search(
        pluginId: String,
        query: String,
        filter: SearchFilter?,
        cursor: String?,
        limit: Int
    ): Result<AidlSearchResult> {
        Timber.d("Searching in plugin: $pluginId, query: $query, filter: $filter, limit: $limit")
        return runCatching {
            val plugin = getPlugin(pluginId)
            plugin.handler.search(query, filter, cursor, limit)
        }.onFailure {
            Timber.e(it, "Error searching in plugin: $pluginId, query: $query")
        }
    }
    
    /**
     * Get browse categories from a plugin.
     */
    suspend fun getBrowseCategories(
        pluginId: String,
        cursor: String?,
        limit: Int
    ): Result<PagedResult<BrowseCategory>> {
        return runCatching {
            val result = getPlugin(pluginId).handler.getBrowseCategories(cursor, limit)
            PagedResult(
                items = result.items,
                nextCursor = result.nextCursor,
                totalCount = result.totalCount
            )
        }.onFailure {
            Timber.e(it, "Error getting browse categories from plugin: $pluginId")
        }
    }
    
    /**
     * Get category contents from a plugin.
     */
    suspend fun getCategoryContents(
        pluginId: String,
        categoryId: String,
        cursor: String?,
        limit: Int
    ): Result<AidlSearchResult> {
        return runCatching {
            val plugin = getPlugin(pluginId)
            plugin.handler.getCategoryContents(categoryId, cursor, limit)
        }.onFailure {
            Timber.e(it, "Error getting category contents from plugin: $pluginId, categoryId: $categoryId")
        }
    }
    
    /**
     * Get library songs from a plugin.
     */
    suspend fun getLibrarySongs(
        pluginId: String,
        cursor: String?,
        limit: Int
    ): Result<PagedResult<Song>> {
        return runCatching {
            val plugin = getPlugin(pluginId)
            plugin.handler.getLibrarySongs(cursor, limit)
        }.onFailure {
            Timber.e(it, "Error getting library songs from plugin: $pluginId")
        }
    }
    
    /**
     * Get library albums from a plugin.
     */
    suspend fun getLibraryAlbums(
        pluginId: String,
        cursor: String?,
        limit: Int
    ): Result<PagedResult<Album>> {
        return runCatching {
            val plugin = getPlugin(pluginId)
            plugin.handler.getLibraryAlbums(cursor, limit)
        }.onFailure {
            Timber.e(it, "Error getting library albums from plugin: $pluginId")
        }
    }
    
    /**
     * Get library artists from a plugin.
     */
    suspend fun getLibraryArtists(
        pluginId: String,
        cursor: String?,
        limit: Int
    ): Result<PagedResult<Artist>> {
        return runCatching {
            val plugin = getPlugin(pluginId)
            plugin.handler.getLibraryArtists(cursor, limit)
        }.onFailure {
            Timber.e(it, "Error getting library artists from plugin: $pluginId")
        }
    }
    
    /**
     * Get library playlists from a plugin.
     */
    suspend fun getLibraryPlaylists(
        pluginId: String,
        cursor: String?,
        limit: Int
    ): Result<PagedResult<Playlist>> {
        return runCatching {
            val plugin = getPlugin(pluginId)
            plugin.handler.getLibraryPlaylists(cursor, limit)
        }.onFailure {
            Timber.e(it, "Error getting library playlists from plugin: $pluginId")
        }
    }
    
    /**
     * Get song details from a plugin.
     */
    suspend fun getSong(id: MediaId): Result<Song> {
        return runCatching {
            val plugin = getPlugin(id.pluginId)
            plugin.handler.getSong(id.sourceId)
        }.onFailure {
            Timber.e(it, "Error getting song from plugin: ${id.pluginId}, songId: ${id.sourceId}")
        }
    }

    /**
     * Get artist details from a plugin.
     */
    suspend fun getArtist(id: MediaId): Result<Artist> {
        return runCatching {
            val plugin = getPlugin(id.pluginId)
            plugin.handler.getArtist(id.sourceId)
        }.onFailure {
            Timber.e(it, "Error getting artist from plugin: ${id.pluginId}, artistId: ${id.sourceId}")
        }
    }

    /**
     * Get album details from a plugin.
     */
    suspend fun getAlbum(id: MediaId): Result<Album> {
        return runCatching {
            Timber.d("getAlbum() called for: $id")
            val plugin = getPlugin(id.pluginId)
            plugin.handler.getAlbum(id.sourceId)
        }.onFailure {
            Timber.e(it, "Error getting album from plugin: ${id.pluginId}, albumId: ${id.sourceId}")
        }
    }

    /**
     * Get playlist details from a plugin.
     */
    suspend fun getPlaylist(id: MediaId): Result<Playlist> {
        return runCatching {
            val plugin = getPlugin(id.pluginId)
            plugin.handler.getPlaylist(id.sourceId)
        }.onFailure {
            Timber.e(it, "Error getting playlist from plugin: ${id.pluginId}, playlistId: ${id.sourceId}")
        }
    }

    /**
     * Get artist songs from a plugin.
     */
    suspend fun getArtistSongs(
        artistId: MediaId,
        cursor: String?,
        limit: Int
    ): Result<PagedResult<Song>> {
        return runCatching {
            val plugin = getPlugin(artistId.pluginId)
            plugin.handler.getArtistSongs(artistId.sourceId, cursor, limit)
        }.onFailure {
            Timber.e(it, "Error getting artist songs from plugin: ${artistId.pluginId}, artistId: ${artistId.sourceId}")
        }
    }

    /**
     * Get artist albums from a plugin.
     */
    suspend fun getArtistAlbums(
        artistId: MediaId,
        cursor: String?,
        limit: Int
    ): Result<PagedResult<Album>> {
        return runCatching {
            val plugin = getPlugin(artistId.pluginId)
            plugin.handler.getArtistAlbums(artistId.sourceId, cursor, limit)
        }.onFailure {
            Timber.e(it, "Error getting artist albums from plugin: ${artistId.pluginId}, artistId: ${artistId.sourceId}")
        }
    }

    /**
     * Get playlist songs from a plugin.
     */
    suspend fun getPlaylistSongs(
        playlistId: MediaId,
        cursor: String?,
        limit: Int
    ): Result<PagedResult<Song>> {
        return runCatching {
            val plugin = getPlugin(playlistId.pluginId)
            plugin.handler.getPlaylistSongs(playlistId.sourceId, cursor, limit)
        }.onFailure {
            Timber.e(it, "Error getting playlist songs from plugin: ${playlistId.pluginId}, playlistId: ${playlistId.sourceId}")
        }
    }
    
    /**
     * Get a stream source for playback from a plugin.
     * Can return a URL, DASH XML, or AudioStream based on what the plugin supports.
     */
    suspend fun getStream(mediaId: MediaId): Result<StreamSource> {
        return runCatching {
            val plugin = getPlugin(mediaId.pluginId)
            plugin.handler.getStream(mediaId.sourceId)
        }.onFailure {
            Timber.e(it, "Error getting stream from plugin: ${mediaId.pluginId}, mediaId: ${mediaId.sourceId}")
        }
    }

    // Gets search suggestions from all plugins asynchronously and publishes merged results
    // as soon as it gets them
    fun getSearchSuggestions(query: String): Flow<List<Result<SearchSuggestions>>> {
        return channelFlow {
            val results = mutableListOf<Result<SearchSuggestions>>()

            _connectedPlugins.value.forEach { (pluginId, plugin) ->
                launch {
                    val result = plugin.handler.getSearchSuggestions(query)
                        .mapCatching { it.toDomain(pluginId) }
                        .onFailure { Timber.e(it, "Error getting search suggestions from plugin: $pluginId, query: $query") }

                    synchronized(results) {
                        results.add(result)
                        trySend(results.toList()) // emit every time a plugin responds
                    }
                }
            }
        }
    }

    private inner class HostCallbackImpl : IHostCallbackV1.Stub() {
        override fun play(mediaId: String) {}
        override fun pause() {}
        override fun resume() {}
        override fun stop() {}
        override fun skipToNext() {}
        override fun skipToPrevious() {}
        override fun seekTo(positionMs: Long) {}
        override fun addToQueue(song: AidlSong) {}
        override fun playNext(song: AidlSong) {}
        override fun clearQueue() {}
        override fun getPlayerState(): AidlPlayerState = AidlPlayerState()
        override fun getCurrentSong(): AidlSong? = null
        override fun getPlaybackPosition(): Long = 0
        override fun notifyContentChanged() {}
        override fun notifyMetadataUpdated(mediaId: String) {}
        override fun reportError(errorCode: Int, message: String) {
            Timber.e("Plugin reported error: code=$errorCode, message=$message")
        }
    }
}

/**
 * Discovered plugin info (before connection).
 */
data class DiscoveredPluginInfo(
    val id: String,
    val name: String,
    val description: String?,
    val version: String,
    val componentName: ComponentName
)

/**
 * Plugin exception.
 */
class PluginException(val errorCode: Int, override val message: String?) : Exception(message ?: "Unknown error")
