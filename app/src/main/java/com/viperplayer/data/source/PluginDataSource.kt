package com.viperplayer.data.source

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.viperplayer.data.mapper.PluginMapper.toDomain
import com.viperplayer.data.preferences.PluginPreferences
import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.BrowseCategory
import com.viperplayer.domain.model.PagedResult
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.PluginCapabilities
import com.viperplayer.domain.model.PluginInfo
import com.viperplayer.domain.model.SearchResult
import com.viperplayer.domain.model.Song
import com.viperplayer.plugin.sdk.PluginConstants
import com.viperplayer.plugin.sdk.v1.IAlbumCallback
import com.viperplayer.plugin.sdk.v1.IAlbumsCallback
import com.viperplayer.plugin.sdk.v1.IArtistCallback
import com.viperplayer.plugin.sdk.v1.IArtistsCallback
import com.viperplayer.plugin.sdk.v1.ICategoriesCallback
import com.viperplayer.plugin.sdk.v1.IHostCallbackV1
import com.viperplayer.plugin.sdk.v1.IPlaylistCallback
import com.viperplayer.plugin.sdk.v1.IPlaylistsCallback
import com.viperplayer.plugin.sdk.v1.IPluginServiceV1
import com.viperplayer.plugin.sdk.v1.ISearchCallback
import com.viperplayer.plugin.sdk.v1.ISearchSuggestionsCallback
import com.viperplayer.plugin.sdk.v1.ISongsCallback
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import com.viperplayer.plugin.sdk.v1.Album as AidlAlbum
import com.viperplayer.plugin.sdk.v1.Artist as AidlArtist
import com.viperplayer.plugin.sdk.v1.BrowseCategory as AidlBrowseCategory
import com.viperplayer.plugin.sdk.v1.MediaId as AidlMediaId
import com.viperplayer.plugin.sdk.v1.PlayerState as AidlPlayerState
import com.viperplayer.plugin.sdk.v1.Playlist as AidlPlaylist
import com.viperplayer.plugin.sdk.v1.SearchResult as AidlSearchResult
import com.viperplayer.plugin.sdk.v1.Song as AidlSong

/**
 * Data source for plugin operations.
 * Handles plugin discovery, connection, and communication.
 */
@Singleton
class PluginDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pluginPreferences: PluginPreferences
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private val _discoveredPlugins = MutableStateFlow<Map<String, DiscoveredPluginInfo>>(emptyMap())
    val discoveredPlugins: StateFlow<Map<String, DiscoveredPluginInfo>> = _discoveredPlugins.asStateFlow()
    
    private val _connectedPlugins = MutableStateFlow<Map<String, ConnectedPluginData>>(emptyMap())
    val connectedPlugins: StateFlow<Map<String, ConnectedPluginData>> = _connectedPlugins.asStateFlow()
    
    // Track ongoing connection attempts to prevent duplicates
    private val ongoingConnections = mutableMapOf<String, ServiceConnection>()
    
    private val hostCallback = HostCallbackImpl()
    
    /**
     * Discover all installed plugins.
     * Plugin ID is the package name.
     */
    fun discoverPlugins() {
        Timber.d("Discovering plugins")

        val intent = Intent(PluginConstants.ACTION_PLUGIN_SERVICE)
        val resolveInfos = context.packageManager.queryIntentServices(
            intent,
            PackageManager.GET_META_DATA
        )
        Timber.d("Found ${resolveInfos.size} services matching plugin intent")

        val plugins = resolveInfos.mapNotNull { resolveInfo ->
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
            val apiVersion = metaData.getInt(PluginConstants.META_API_VERSION, -1).let {
                if (it == -1) {
                    Timber.w("Plugin $pluginId has no API version in metadata, using MIN_API_VERSION")
                    PluginConstants.MIN_API_VERSION
                } else {
                    it
                }
            }
            val description = metaData.getString(PluginConstants.META_PLUGIN_DESCRIPTION)
            val iconUrl = metaData.getString(PluginConstants.META_PLUGIN_ICON)

            // Get version from package info
            val version = try {
                val packageInfo = context.packageManager.getPackageInfo(pluginId, 0)
                packageInfo.versionName ?: "N/A"
            } catch (e: Exception) {
                Timber.w(e, "Failed to get version for plugin: $pluginId")
                "N/A"
            }

            val componentName = ComponentName(serviceInfo.packageName, serviceInfo.name)
            Timber.d("Discovered plugin: id=$pluginId, name=$pluginName, version=$version, apiVersion=$apiVersion, component=$componentName")

            pluginId to DiscoveredPluginInfo(
                id = pluginId,
                name = pluginName,
                description = description,
                apiVersion = apiVersion,
                version = version,
                iconUrl = iconUrl,
                componentName = componentName
            )
        }.toMap()

        _discoveredPlugins.value = plugins
        Timber.d("Successfully discovered ${plugins.size} plugins: ${plugins.keys}")

        // Auto-connect enabled plugins
        plugins.keys.forEach { pluginId ->
            scope.launch {
                val isEnabled = pluginPreferences.isEnabledSync(pluginId)
                Timber.d("Plugin $pluginId enabled state: $isEnabled")
                if (isEnabled) {
                    try {
                        Timber.d("Auto-connecting enabled plugin: $pluginId")
                        connectPlugin(pluginId)
                        Timber.d("Successfully auto-connected plugin: $pluginId")
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to auto-connect plugin: $pluginId")
                    }
                } else {
                    Timber.d("Plugin $pluginId is disabled, skipping auto-connect")
                }
            }
        }
    }
    
    /**
     * Connect to a plugin.
     */
    suspend fun connectPlugin(pluginId: String): ConnectedPluginData {
        Timber.d("Connecting to plugin: $pluginId")
        
        // Check if already connected
        _connectedPlugins.value[pluginId]?.let {
            Timber.d("Plugin $pluginId already connected, returning existing connection")
            return it
        }
        
        // Find the plugin
        val discovered = _discoveredPlugins.value[pluginId]
            ?: throw IllegalArgumentException("Plugin not found: $pluginId")
        
        // Unbind any existing ongoing connection attempt
        synchronized(ongoingConnections) {
            ongoingConnections[pluginId]?.let { oldConnection ->
                Timber.d("Unbinding existing connection attempt for plugin: $pluginId")
                try {
                    context.unbindService(oldConnection)
                } catch (e: Exception) {
                    Timber.w(e, "Error unbinding old connection for plugin: $pluginId")
                }
                ongoingConnections.remove(pluginId)
            }
        }
        
        return suspendCancellableCoroutine { cont ->
            var connectionEstablished = false
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    Timber.d("onServiceConnected() called: name=$name for plugin: $pluginId")
                    
                    // Check if continuation is already completed (race condition protection)
                    if (cont.isCompleted) {
                        Timber.w("onServiceConnected() called but continuation already completed for plugin: $pluginId")
                        return
                    }
                    
                    connectionEstablished = true
                    
                    if (binder == null) {
                        synchronized(ongoingConnections) {
                            ongoingConnections.remove(pluginId)
                        }
                        if (!cont.isCompleted) {
                            cont.resumeWithException(IllegalStateException("Binder is null for plugin: $pluginId"))
                        }
                        return
                    }

                    try {
                        val connectedData = when (discovered.apiVersion) {
                            1 -> connectPluginV1(discovered, binder)
                            else -> {
                                synchronized(ongoingConnections) {
                                    ongoingConnections.remove(pluginId)
                                }
                                if (!cont.isCompleted) {
                                    cont.resumeWithException(IllegalStateException("Unsupported API version: ${discovered.apiVersion} for plugin: $pluginId"))
                                }
                                return
                            }
                        }

                        synchronized(ongoingConnections) {
                            ongoingConnections.remove(pluginId)
                        }
                        if (!cont.isCompleted) {
                            cont.resume(connectedData)
                        }
                    } catch (e: Exception) {
                        synchronized(ongoingConnections) {
                            ongoingConnections.remove(pluginId)
                        }
                        if (!cont.isCompleted) {
                            cont.resumeWithException(e)
                        }
                    }
                }
                
                override fun onServiceDisconnected(name: ComponentName) {
                    Timber.w("onServiceDisconnected() called: $name for plugin: $pluginId")
                    synchronized(ongoingConnections) {
                        ongoingConnections.remove(pluginId)
                    }
                    _connectedPlugins.update { it - pluginId }
                    
                    // Auto-reconnect if enabled
                    scope.launch {
                        val isEnabled = pluginPreferences.isEnabledSync(pluginId)
                        Timber.d("Plugin $pluginId disconnected, enabled state: $isEnabled")
                        if (isEnabled) {
                            try {
                                Timber.d("Attempting to reconnect plugin: $pluginId")
                                connectPlugin(pluginId)
                                Timber.d("Successfully reconnected plugin: $pluginId")
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to reconnect plugin: $pluginId")
                            }
                        } else {
                            Timber.d("Plugin $pluginId is disabled, not reconnecting")
                        }
                    }
                }
                
                override fun onBindingDied(name: ComponentName) {
                    Timber.e("onBindingDied() called: $name for plugin: $pluginId")
                    synchronized(ongoingConnections) {
                        ongoingConnections.remove(pluginId)
                    }
                    if (!connectionEstablished && !cont.isCompleted) {
                        cont.resumeWithException(IllegalStateException("Binding died before connection established for plugin: $pluginId"))
                    }
                }
                
                override fun onNullBinding(name: ComponentName) {
                    Timber.e("onNullBinding() called: $name for plugin: $pluginId")
                    synchronized(ongoingConnections) {
                        ongoingConnections.remove(pluginId)
                    }
                    if (!connectionEstablished && !cont.isCompleted) {
                        cont.resumeWithException(IllegalStateException("Null binding for plugin: $pluginId"))
                    }
                }
            }
            
            // Track this connection attempt
            synchronized(ongoingConnections) {
                ongoingConnections[pluginId] = connection
            }
            
            // Create intent with explicit component
            val intent = Intent().apply {
                component = discovered.componentName
            }

            try {
                Timber.d("Binding to plugin service: ${intent.component}")
                val bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
                Timber.d("bindService() returned: $bound for plugin: $pluginId")
                
                if (!bound) {
                    synchronized(ongoingConnections) {
                        ongoingConnections.remove(pluginId)
                    }
                    if (!cont.isCompleted) {
                        cont.resumeWithException(IllegalStateException("Failed to bind to plugin service: $pluginId. Service may not exist or may not be exported."))
                    }
                } else {
                    Timber.d("Service bind initiated successfully for plugin: $pluginId")
                    // Set a timeout to detect if onServiceConnected never gets called
                    scope.launch {
                        delay(10000) // 10 second timeout
                        if (!connectionEstablished && !cont.isCompleted) {
                            synchronized(ongoingConnections) {
                                ongoingConnections.remove(pluginId)
                            }
                            if (!cont.isCompleted) {
                                cont.resumeWithException(IllegalStateException("Timeout waiting for service connection: $pluginId."))
                            }
                        }
                    }
                }
            } catch (e: SecurityException) {
                synchronized(ongoingConnections) {
                    ongoingConnections.remove(pluginId)
                }
                if (!cont.isCompleted) {
                    cont.resumeWithException(IllegalStateException("Security exception binding to plugin: $pluginId", e))
                }
            } catch (e: Exception) {
                synchronized(ongoingConnections) {
                    ongoingConnections.remove(pluginId)
                }
                if (!cont.isCompleted) {
                    cont.resumeWithException(IllegalStateException("Exception binding to plugin: $pluginId", e))
                }
            }
            
            cont.invokeOnCancellation {
                Timber.d("Connection cancelled for plugin: $pluginId")
                synchronized(ongoingConnections) {
                    ongoingConnections.remove(pluginId)
                }
                try {
                    context.unbindService(connection)
                } catch (e: Exception) {
                    Timber.w(e, "Error unbinding service on cancellation")
                }
            }
        }
    }

    private fun ServiceConnection.connectPluginV1(discovered: DiscoveredPluginInfo, binder: IBinder): ConnectedPluginData {
        val service = IPluginServiceV1.Stub.asInterface(binder)
            ?: throw IllegalStateException("Failed to get IPluginServiceV1 interface from binder for plugin: ${discovered.id}")

        Timber.d("Calling connect() on plugin service: ${discovered.id}")
        service.connect(hostCallback)

        val capabilities = service.capabilities
        Timber.d("Got capabilities from plugin: ${discovered.id}")

        // Create PluginInfo from discovered plugin info (host app creates it)
        val pluginInfo = PluginInfo(
            id = discovered.id,
            name = discovered.name,
            version = discovered.version,
            apiVersion = discovered.apiVersion,
            description = discovered.description,
            author = null, // Can be added to metadata if needed
            iconUrl = discovered.iconUrl
        )

        val connectedData = ConnectedPluginData(
            info = pluginInfo,
            capabilities = capabilities.toDomain(),
            service = service,
            connection = this
        )

        _connectedPlugins.update { it + (discovered.id to connectedData) }

        Timber.d("Plugin connected successfully: ${pluginInfo.name}")

        return connectedData
    }
    
    /**
     * Disconnect from a plugin.
     */
    fun disconnectPlugin(pluginId: String) {
        Timber.d("Disconnecting plugin: $pluginId")
        val connected = _connectedPlugins.value[pluginId]
        
        // Clean up any ongoing connection attempts
        synchronized(ongoingConnections) {
            ongoingConnections[pluginId]?.let { ongoingConnection ->
                Timber.d("Unbinding ongoing connection attempt for plugin: $pluginId")
                try {
                    context.unbindService(ongoingConnection)
                } catch (e: Exception) {
                    Timber.w(e, "Error unbinding ongoing connection for plugin: $pluginId")
                }
                ongoingConnections.remove(pluginId)
            }
        }
        
        if (connected == null) {
            Timber.w("Plugin $pluginId not connected, nothing to disconnect")
            return
        }
        
        try {
            Timber.d("Calling disconnect() on plugin service: $pluginId")
            connected.service.disconnect()
            Timber.d("Successfully called disconnect() on plugin: $pluginId")
        } catch (e: Exception) {
            Timber.e(e, "Error calling disconnect() on plugin: $pluginId")
        }
        
        try {
            Timber.d("Unbinding service for plugin: $pluginId")
            context.unbindService(connected.connection)
            Timber.d("Successfully unbound service for plugin: $pluginId")
        } catch (e: Exception) {
            Timber.e(e, "Error unbinding service for plugin: $pluginId")
        }
        
        _connectedPlugins.update { it - pluginId }
        Timber.d("Plugin $pluginId disconnected and removed from connected plugins")
    }
    
    /**
     * Disconnect from all plugins.
     */
    fun disconnectAll() {
        Timber.d("Disconnecting all plugins")
        val pluginIds = _connectedPlugins.value.keys.toList()
        Timber.d("Disconnecting ${pluginIds.size} plugins: $pluginIds")
        pluginIds.forEach { disconnectPlugin(it) }
        Timber.d("All plugins disconnected")
    }
    
    /**
     * Enable a plugin (connects it automatically).
     */
    suspend fun enablePlugin(pluginId: String) {
        Timber.d("Enabling plugin: $pluginId")
        pluginPreferences.setEnabled(pluginId, true)
        connectPlugin(pluginId)
    }
    
    /**
     * Disable a plugin (disconnects it).
     */
    suspend fun disablePlugin(pluginId: String) {
        Timber.d("Disabling plugin: $pluginId")
        pluginPreferences.setEnabled(pluginId, false)
        disconnectPlugin(pluginId)
    }
    
    /**
     * Get a connected plugin by ID.
     */
    fun getPlugin(pluginId: String): ConnectedPluginData? {
        return _connectedPlugins.value[pluginId]
    }
    
    /**
     * Search in a specific plugin.
     */
    suspend fun search(
        pluginId: String,
        query: String,
        types: Int,
        cursor: String?,
        limit: Int
    ): SearchResult {
        Timber.d("Searching in plugin: $pluginId, query: $query, types: $types, limit: $limit")
        val plugin = getPlugin(pluginId)
        if (plugin == null) {
            Timber.e("Plugin not connected for search: $pluginId")
            throw IllegalStateException("Plugin not connected: $pluginId")
        }
        
        return suspendCancellableCoroutine { cont ->
            Timber.d("Calling search() on plugin service: $pluginId")
            try {
                plugin.service.search(query, types, cursor, limit, object : ISearchCallback.Stub() {
                    override fun onSuccess(result: AidlSearchResult) {
                        Timber.d("Search result received from plugin: $pluginId, songs: ${result.songs.size}, albums: ${result.albums.size}")
                        cont.resume(result.toDomain())
                    }
                })
            } catch (e: Exception) {
                Timber.e(e, "Search failed for plugin $pluginId")
                cont.resumeWithException(e)
            }
        }
    }
    
    /**
     * Get browse categories from a plugin.
     */
    suspend fun getBrowseCategories(
        pluginId: String,
        cursor: String?,
        limit: Int
    ): PagedResult<BrowseCategory> {
        Timber.d("Getting browse categories from plugin: $pluginId, cursor: $cursor, limit: $limit")
        val plugin = getPlugin(pluginId)
        if (plugin == null) {
            Timber.e("Plugin not connected for browse categories: $pluginId")
            throw IllegalStateException("Plugin not connected: $pluginId")
        }
        
        return suspendCancellableCoroutine { cont ->
            try {
                plugin.service.getBrowseCategories(cursor, limit, object : ICategoriesCallback.Stub() {
                    override fun onSuccess(categories: MutableList<AidlBrowseCategory>, nextCursor: String?) {
                        Timber.d("Browse categories received from plugin: $pluginId, count: ${categories.size}, nextCursor: $nextCursor")
                        cont.resume(PagedResult(categories.map { it.toDomain() }, nextCursor))
                    }
                })
            } catch (e: Exception) {
                Timber.e(e, "Failed to get browse categories from plugin $pluginId")
                cont.resumeWithException(e)
            }
        }
    }
    
    /**
     * Get library songs from a plugin.
     */
    suspend fun getLibrarySongs(
        pluginId: String,
        cursor: String?,
        limit: Int
    ): PagedResult<Song> {
        Timber.d("Getting library songs from plugin: $pluginId, cursor: $cursor, limit: $limit")
        val plugin = getPlugin(pluginId)
        if (plugin == null) {
            Timber.e("Plugin not connected for library songs: $pluginId")
            throw IllegalStateException("Plugin not connected: $pluginId")
        }
        
        return suspendCancellableCoroutine { cont ->
            try {
                plugin.service.getLibrarySongs(cursor, limit, object : ISongsCallback.Stub() {
                    override fun onSuccess(songs: MutableList<AidlSong>, nextCursor: String?) {
                        Timber.d("Library songs received from plugin: $pluginId, count: ${songs.size}, nextCursor: $nextCursor")
                        cont.resume(PagedResult(songs.map { it.toDomain() }, nextCursor))
                    }
                })
            } catch (e: Exception) {
                Timber.e(e, "Failed to get library songs from plugin $pluginId")
                cont.resumeWithException(e)
            }
        }
    }
    
    /**
     * Get library albums from a plugin.
     */
    suspend fun getLibraryAlbums(
        pluginId: String,
        cursor: String?,
        limit: Int
    ): PagedResult<Album> {
        Timber.d("Getting library albums from plugin: $pluginId, cursor: $cursor, limit: $limit")
        val plugin = getPlugin(pluginId)
        if (plugin == null) {
            Timber.e("Plugin not connected for library albums: $pluginId")
            throw IllegalStateException("Plugin not connected: $pluginId")
        }
        
        return suspendCancellableCoroutine { cont ->
            try {
                plugin.service.getLibraryAlbums(cursor, limit, object : IAlbumsCallback.Stub() {
                    override fun onSuccess(albums: MutableList<AidlAlbum>, nextCursor: String?) {
                        Timber.d("Library albums received from plugin: $pluginId, count: ${albums.size}, nextCursor: $nextCursor")
                        cont.resume(PagedResult(albums.map { it.toDomain() }, nextCursor))
                    }
                })
            } catch (e: Exception) {
                Timber.e(e, "Failed to get library albums from plugin $pluginId")
                cont.resumeWithException(e)
            }
        }
    }
    
    /**
     * Get library artists from a plugin.
     */
    suspend fun getLibraryArtists(
        pluginId: String,
        cursor: String?,
        limit: Int
    ): PagedResult<Artist> {
        Timber.d("Getting library artists from plugin: $pluginId, cursor: $cursor, limit: $limit")
        val plugin = getPlugin(pluginId)
        if (plugin == null) {
            Timber.e("Plugin not connected for library artists: $pluginId")
            throw IllegalStateException("Plugin not connected: $pluginId")
        }
        
        return suspendCancellableCoroutine { cont ->
            try {
                plugin.service.getLibraryArtists(cursor, limit, object : IArtistsCallback.Stub() {
                    override fun onSuccess(artists: MutableList<AidlArtist>, nextCursor: String?) {
                        Timber.d("Library artists received from plugin: $pluginId, count: ${artists.size}, nextCursor: $nextCursor")
                        cont.resume(PagedResult(artists.map { it.toDomain() }, nextCursor))
                    }
                })
            } catch (e: Exception) {
                Timber.e(e, "Failed to get library artists from plugin $pluginId")
                cont.resumeWithException(e)
            }
        }
    }
    
    /**
     * Get library playlists from a plugin.
     */
    suspend fun getLibraryPlaylists(
        pluginId: String,
        cursor: String?,
        limit: Int
    ): PagedResult<Playlist> {
        Timber.d("Getting library playlists from plugin: $pluginId, cursor: $cursor, limit: $limit")
        val plugin = getPlugin(pluginId)
        if (plugin == null) {
            Timber.e("Plugin not connected for library playlists: $pluginId")
            throw IllegalStateException("Plugin not connected: $pluginId")
        }
        
        return suspendCancellableCoroutine { cont ->
            try {
                plugin.service.getLibraryPlaylists(cursor, limit, object : IPlaylistsCallback.Stub() {
                    override fun onSuccess(playlists: MutableList<AidlPlaylist>, nextCursor: String?) {
                        Timber.d("Library playlists received from plugin: $pluginId, count: ${playlists.size}, nextCursor: $nextCursor")
                        cont.resume(PagedResult(playlists.map { it.toDomain() }, nextCursor))
                    }
                })
            } catch (e: Exception) {
                Timber.e(e, "Failed to get library playlists from plugin $pluginId")
                cont.resumeWithException(e)
            }
        }
    }
    
    /**
     * Get artist details from a plugin.
     */
    suspend fun getArtist(mediaId: AidlMediaId): Artist {
        Timber.d("Getting artist from plugin: ${mediaId.pluginId}:${mediaId.sourceId}")
        val plugin = getPlugin(mediaId.pluginId)
        if (plugin == null) {
            Timber.e("Plugin not connected for artist: ${mediaId.pluginId}")
            throw IllegalStateException("Plugin not connected: ${mediaId.pluginId}")
        }

        return suspendCancellableCoroutine { cont ->
            try {
                plugin.service.getArtist(mediaId, object : IArtistCallback.Stub() {
                    override fun onSuccess(artist: AidlArtist) {
                        Timber.d("Artist received from plugin: ${artist.name}")
                        cont.resume(artist.toDomain())
                    }
                })
            } catch (e: Exception) {
                Timber.e(e, "Failed to get artist from plugin")
                cont.resumeWithException(e)
            }
        }
    }

    /**
     * Get album details from a plugin.
     */
    suspend fun getAlbum(mediaId: AidlMediaId): Album {
        Timber.d("Getting album from plugin: ${mediaId.pluginId}:${mediaId.sourceId}")
        val plugin = getPlugin(mediaId.pluginId)
        if (plugin == null) {
            Timber.e("Plugin not connected for album: ${mediaId.pluginId}")
            throw IllegalStateException("Plugin not connected: ${mediaId.pluginId}")
        }

        return suspendCancellableCoroutine { cont ->
            try {
                plugin.service.getAlbum(mediaId, object : IAlbumCallback.Stub() {
                    override fun onSuccess(album: AidlAlbum) {
                        Timber.d("Album received from plugin: ${album.name}")
                        cont.resume(album.toDomain())
                    }
                })
            } catch (e: Exception) {
                Timber.e(e, "Failed to get album from plugin")
                cont.resumeWithException(e)
            }
        }
    }

    /**
     * Get playlist details from a plugin.
     */
    suspend fun getPlaylist(mediaId: AidlMediaId): Playlist {
        Timber.d("Getting playlist from plugin: ${mediaId.pluginId}:${mediaId.sourceId}")
        val plugin = getPlugin(mediaId.pluginId)
        if (plugin == null) {
            Timber.e("Plugin not connected for playlist: ${mediaId.pluginId}")
            throw IllegalStateException("Plugin not connected: ${mediaId.pluginId}")
        }

        return suspendCancellableCoroutine { cont ->
            try {
                plugin.service.getPlaylist(mediaId, object : IPlaylistCallback.Stub() {
                    override fun onSuccess(playlist: AidlPlaylist) {
                        Timber.d("Playlist received from plugin: ${playlist.name}")
                        cont.resume(playlist.toDomain())
                    }
                })
            } catch (e: Exception) {
                Timber.e(e, "Failed to get playlist from plugin")
                cont.resumeWithException(e)
            }
        }
    }

    /**
     * Get artist songs from a plugin.
     */
    suspend fun getArtistSongs(
        artistId: AidlMediaId,
        cursor: String?,
        limit: Int
    ): PagedResult<Song> {
        Timber.d("Getting artist songs from plugin: ${artistId.pluginId}:${artistId.sourceId}")
        val plugin = getPlugin(artistId.pluginId)
        if (plugin == null) {
            Timber.e("Plugin not connected for artist songs: ${artistId.pluginId}")
            throw IllegalStateException("Plugin not connected: ${artistId.pluginId}")
        }

        return suspendCancellableCoroutine { cont ->
            try {
                plugin.service.getArtistSongs(artistId, cursor, limit, object : ISongsCallback.Stub() {
                    override fun onSuccess(songs: MutableList<AidlSong>, nextCursor: String?) {
                        Timber.d("Artist songs received from plugin, count: ${songs.size}, nextCursor: $nextCursor")
                        cont.resume(PagedResult(songs.map { it.toDomain() }, nextCursor))
                    }
                })
            } catch (e: Exception) {
                Timber.e(e, "Failed to get artist songs from plugin")
                cont.resumeWithException(e)
            }
        }
    }

    /**
     * Get artist albums from a plugin.
     */
    suspend fun getArtistAlbums(
        artistId: AidlMediaId,
        cursor: String?,
        limit: Int
    ): PagedResult<Album> {
        Timber.d("Getting artist albums from plugin: ${artistId.pluginId}:${artistId.sourceId}")
        val plugin = getPlugin(artistId.pluginId)
        if (plugin == null) {
            Timber.e("Plugin not connected for artist albums: ${artistId.pluginId}")
            throw IllegalStateException("Plugin not connected: ${artistId.pluginId}")
        }

        return suspendCancellableCoroutine { cont ->
            try {
                plugin.service.getArtistAlbums(artistId, cursor, limit, object : IAlbumsCallback.Stub() {
                    override fun onSuccess(albums: MutableList<AidlAlbum>, nextCursor: String?) {
                        Timber.d("Artist albums received from plugin, count: ${albums.size}, nextCursor: $nextCursor")
                        cont.resume(PagedResult(albums.map { it.toDomain() }, nextCursor))
                    }
                })
            } catch (e: Exception) {
                Timber.e(e, "Failed to get artist albums from plugin")
                cont.resumeWithException(e)
            }
        }
    }

    /**
     * Get playlist songs from a plugin.
     */
    suspend fun getPlaylistSongs(
        playlistId: AidlMediaId,
        cursor: String?,
        limit: Int
    ): PagedResult<Song> {
        Timber.d("Getting playlist songs from plugin: ${playlistId.pluginId}:${playlistId.sourceId}")
        val plugin = getPlugin(playlistId.pluginId)
        if (plugin == null) {
            Timber.e("Plugin not connected for playlist songs: ${playlistId.pluginId}")
            throw IllegalStateException("Plugin not connected: ${playlistId.pluginId}")
        }

        return suspendCancellableCoroutine { cont ->
            try {
                plugin.service.getPlaylistSongs(playlistId, cursor, limit, object : ISongsCallback.Stub() {
                    override fun onSuccess(songs: MutableList<AidlSong>, nextCursor: String?) {
                        Timber.d("Playlist songs received from plugin, count: ${songs.size}, nextCursor: $nextCursor")
                        cont.resume(PagedResult(songs.map { it.toDomain() }, nextCursor))
                    }
                })
            } catch (e: Exception) {
                Timber.e(e, "Failed to get playlist songs from plugin")
                cont.resumeWithException(e)
            }
        }
    }

    // Gets search suggestions from all plugins asynchronously and publishes merged results
    // as soon as it gets them
    fun getSearchSuggestions(query: String): Flow<List<Result<List<String>>>> {
        return channelFlow {
            _connectedPlugins.value.forEach { (_, plugin) ->
                launch {
                    val result = try {
                        val suggestions = suspendCoroutine { continuation ->
                            plugin.service.getSearchSuggestions(query, object : ISearchSuggestionsCallback.Stub() {
                                override fun onSuccess(result: List<String>) {
                                    continuation.resume(result)
                                }
                            })
                        }
                        Result.success(suggestions)
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to get search suggestions from plugin")
                        Result.failure(e)
                    }

                    send(result)
                }
            }
        }
            .scan(emptyList<Result<List<String>>>()) { acc, value -> acc + value }
            .drop(1)
    }

    private inner class HostCallbackImpl : IHostCallbackV1.Stub() {
        override fun play(mediaId: AidlMediaId) {}
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
        override fun notifyMetadataUpdated(mediaId: AidlMediaId) {}
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
    val apiVersion: Int,
    val version: String,
    val iconUrl: String?,
    val componentName: ComponentName
)

/**
 * Connected plugin data.
 */
data class ConnectedPluginData(
    val info: PluginInfo,
    val capabilities: PluginCapabilities,
    val service: IPluginServiceV1,
    val connection: ServiceConnection
)

/**
 * Plugin exception.
 */
class PluginException(val errorCode: Int, override val message: String) : Exception(message)
