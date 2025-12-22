package com.viperplayer.data.source

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.viperplayer.data.mapper.PluginMapper.toDomain
import com.viperplayer.data.preferences.PluginPreferences
import timber.log.Timber
import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.BrowseCategory
import com.viperplayer.domain.model.PagedResult
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.PluginCapabilities
import com.viperplayer.domain.model.PluginInfo
import com.viperplayer.domain.model.SearchResult
import com.viperplayer.domain.model.Song
import com.viperplayer.plugin.aidl.AudioStream
import com.viperplayer.plugin.aidl.IHostCallbackV1
import com.viperplayer.plugin.aidl.IPluginServiceV1
import com.viperplayer.plugin.aidl.IResultCallback
import com.viperplayer.plugin.sdk.PluginConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.viperplayer.plugin.aidl.Album as AidlAlbum
import com.viperplayer.plugin.aidl.Artist as AidlArtist
import com.viperplayer.plugin.aidl.BrowseCategory as AidlBrowseCategory
import com.viperplayer.plugin.aidl.MediaId as AidlMediaId
import com.viperplayer.plugin.aidl.PlayerState as AidlPlayerState
import com.viperplayer.plugin.aidl.Playlist as AidlPlaylist
import com.viperplayer.plugin.aidl.SearchResult as AidlSearchResult
import com.viperplayer.plugin.aidl.Song as AidlSong

/**
 * Data source for plugin operations.
 * Handles plugin discovery, connection, and communication.
 */
@Singleton
class PluginDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pluginPreferences: PluginPreferences
) {
    companion object {
        private const val TAG = "PluginDataSource"
    }
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private val _discoveredPlugins = MutableStateFlow<Map<String, DiscoveredPluginInfo>>(emptyMap())
    val discoveredPlugins: StateFlow<Map<String, DiscoveredPluginInfo>> = _discoveredPlugins.asStateFlow()
    
    private val _connectedPlugins = MutableStateFlow<Map<String, ConnectedPluginData>>(emptyMap())
    val connectedPlugins: StateFlow<Map<String, ConnectedPluginData>> = _connectedPlugins.asStateFlow()
    
    private val hostCallback = HostCallbackImpl()
    
    /**
     * Discover all installed plugins.
     * Plugin ID is the package name.
     */
    suspend fun discoverPlugins() {
        Timber.d("[$TAG] Starting plugin discovery")
        try {
            val intent = Intent(PluginConstants.ACTION_PLUGIN_SERVICE)
            val resolveInfos = context.packageManager.queryIntentServices(
                intent,
                PackageManager.GET_META_DATA
            )
            Timber.d("[$TAG] Found ${resolveInfos.size} services matching plugin intent")
            
            val plugins = resolveInfos.mapNotNull { resolveInfo ->
                val serviceInfo = resolveInfo.serviceInfo
                if (serviceInfo == null) {
                    Timber.w("[$TAG] ResolveInfo has no serviceInfo")
                    return@mapNotNull null
                }
                val metaData = serviceInfo.metaData
                
                // Use package name as plugin ID
                val pluginId = serviceInfo.packageName
                
                // Get plugin info from metadata and package manager
                val pluginName = metaData?.getString(PluginConstants.META_PLUGIN_NAME)
                    ?: serviceInfo.loadLabel(context.packageManager).toString()
                val apiVersion = metaData?.getInt(PluginConstants.META_API_VERSION, 1) ?: 1
                val description = metaData?.getString(PluginConstants.META_PLUGIN_DESCRIPTION)
                val iconUrl = metaData?.getString(PluginConstants.META_PLUGIN_ICON)
                
                // Get version from package info
                val version = try {
                    val packageInfo = context.packageManager.getPackageInfo(pluginId, 0)
                    packageInfo.versionName ?: "1.0"
                } catch (e: Exception) {
                    Timber.w(e, "[$TAG] Failed to get version for plugin: $pluginId")
                    "1.0"
                }
                
                val componentName = ComponentName(serviceInfo.packageName, serviceInfo.name)
                Timber.d("[$TAG] Discovered plugin: id=$pluginId, name=$pluginName, apiVersion=$apiVersion, version=$version")
                Timber.d("[$TAG] Plugin component: package=${componentName.packageName}, class=${componentName.className}, flattened=${componentName.flattenToString()}")
                
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
            Timber.d("[$TAG] Successfully discovered ${plugins.size} plugins: ${plugins.keys}")
            
            // Auto-connect enabled plugins
            plugins.keys.forEach { pluginId ->
                scope.launch {
                    val isEnabled = pluginPreferences.isEnabledSync(pluginId)
                    Timber.d("[$TAG] Plugin $pluginId enabled state: $isEnabled")
                    if (isEnabled) {
                        try {
                            Timber.d("[$TAG] Auto-connecting enabled plugin: $pluginId")
                            connectPlugin(pluginId)
                            Timber.d("[$TAG] Successfully auto-connected plugin: $pluginId")
                        } catch (e: Exception) {
                            Timber.e(e, "[$TAG] Failed to auto-connect plugin: $pluginId")
                        }
                    } else {
                        Timber.d("[$TAG] Plugin $pluginId is disabled, skipping auto-connect")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "[$TAG] Error during plugin discovery")
            throw e
        }
    }
    
    /**
     * Connect to a plugin.
     */
    suspend fun connectPlugin(pluginId: String): ConnectedPluginData {
        Timber.d("[$TAG] Connecting to plugin: $pluginId")
        
        // Check if already connected
        _connectedPlugins.value[pluginId]?.let {
            Timber.d("[$TAG] Plugin $pluginId already connected, returning existing connection")
            return it
        }
        
        // Find the plugin
        val discovered = _discoveredPlugins.value[pluginId]
        if (discovered == null) {
            Timber.e("[$TAG] Plugin not found in discovered plugins: $pluginId")
            throw IllegalArgumentException("Plugin not found: $pluginId")
        }
        
        Timber.d("[$TAG] Binding to service: ${discovered.componentName}")
        Timber.d("[$TAG] Component details: package=${discovered.componentName.packageName}, class=${discovered.componentName.className}")
        
        // Verify the package is installed
        try {
            val packageInfo = context.packageManager.getPackageInfo(discovered.componentName.packageName, 0)
            Timber.d("[$TAG] Package verified: ${packageInfo.packageName}, version=${packageInfo.versionName}")
        } catch (e: Exception) {
            Timber.e(e, "[$TAG] Package not found or not accessible: ${discovered.componentName.packageName}")
            throw IllegalStateException("Plugin package not accessible: $pluginId", e)
        }
        
        // Verify the service component exists
        try {
            val resolveInfo = context.packageManager.resolveService(
                Intent(PluginConstants.ACTION_PLUGIN_SERVICE).apply {
                    setPackage(discovered.componentName.packageName)
                },
                PackageManager.GET_META_DATA
            )
            if (resolveInfo == null) {
                Timber.e("[$TAG] Service not found via resolveService for package: ${discovered.componentName.packageName}")
            } else {
                Timber.d("[$TAG] Service resolved: ${resolveInfo.serviceInfo?.name}")
            }
        } catch (e: Exception) {
            Timber.w(e, "[$TAG] Could not resolve service (this is okay if using explicit component)")
        }
        
        return suspendCancellableCoroutine { cont ->
            var connectionEstablished = false
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                    Timber.d("[$TAG] onServiceConnected() called: name=$name for plugin: $pluginId")
                    connectionEstablished = true
                    
                    if (binder == null) {
                        Timber.e("[$TAG] Binder is null for plugin: $pluginId")
                        cont.resumeWithException(IllegalStateException("Binder is null for plugin: $pluginId"))
                        return
                    }
                    
                    try {
                        val service = IPluginServiceV1.Stub.asInterface(binder)
                        if (service == null) {
                            Timber.e("[$TAG] Failed to get IPluginServiceV1 interface from binder for plugin: $pluginId")
                            cont.resumeWithException(IllegalStateException("Failed to get service interface for plugin: $pluginId"))
                            return
                        }
                        
                        Timber.d("[$TAG] Calling connect() on plugin service: $pluginId")
                        val apiVersion = service.connect(hostCallback)
                        Timber.d("[$TAG] Plugin service connect() returned API version: $apiVersion")
                        
                        val capabilities = service.capabilities
                        Timber.d("[$TAG] Got capabilities from plugin: $pluginId")
                        
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
                        
                        Timber.d("[$TAG] Plugin $pluginId connected with API v$apiVersion, name: ${pluginInfo.name}")
                        
                        val connectedData = ConnectedPluginData(
                            info = pluginInfo,
                            capabilities = capabilities.toDomain(),
                            service = service,
                            connection = this
                        )
                        
                        _connectedPlugins.update { it + (pluginId to connectedData) }
                        
                        Timber.d("[$TAG] Plugin connected successfully: ${pluginInfo.name} (API v$apiVersion)")
                        cont.resume(connectedData)
                    } catch (e: Exception) {
                        Timber.e(e, "[$TAG] Failed to connect plugin: $pluginId")
                        cont.resumeWithException(e)
                    }
                }
                
                override fun onServiceDisconnected(name: ComponentName) {
                    Timber.w("[$TAG] onServiceDisconnected() called: $name for plugin: $pluginId")
                    _connectedPlugins.update { it - pluginId }
                    
                    // Auto-reconnect if enabled
                    scope.launch {
                        val isEnabled = pluginPreferences.isEnabledSync(pluginId)
                        Timber.d("[$TAG] Plugin $pluginId disconnected, enabled state: $isEnabled")
                        if (isEnabled) {
                            try {
                                Timber.d("[$TAG] Attempting to reconnect plugin: $pluginId")
                                connectPlugin(pluginId)
                                Timber.d("[$TAG] Successfully reconnected plugin: $pluginId")
                            } catch (e: Exception) {
                                Timber.e(e, "[$TAG] Failed to reconnect plugin: $pluginId")
                            }
                        } else {
                            Timber.d("[$TAG] Plugin $pluginId is disabled, not reconnecting")
                        }
                    }
                }
                
                override fun onBindingDied(name: ComponentName) {
                    Timber.e("[$TAG] onBindingDied() called: $name for plugin: $pluginId")
                    if (!connectionEstablished) {
                        cont.resumeWithException(IllegalStateException("Binding died before connection established for plugin: $pluginId"))
                    }
                }
                
                override fun onNullBinding(name: ComponentName) {
                    Timber.e("[$TAG] onNullBinding() called: $name for plugin: $pluginId")
                    if (!connectionEstablished) {
                        cont.resumeWithException(IllegalStateException("Null binding for plugin: $pluginId"))
                    }
                }
            }
            
            // Create intent with explicit component AND action
            // The action is needed for onBind() to recognize it as a plugin service
            val intent = Intent().apply {
                component = discovered.componentName
            }
            
            Timber.d("[$TAG] Creating bind intent:")
            Timber.d("[$TAG]   - component: ${intent.component}")
            Timber.d("[$TAG] Attempting to bind service with flags: BIND_AUTO_CREATE")

            try {
                val bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
                Timber.d("[$TAG] bindService() returned: $bound for plugin: $pluginId")
                
                if (!bound) {
                    Timber.e("[$TAG] bindService() returned false for plugin: $pluginId")
                    Timber.e("[$TAG] Component: ${discovered.componentName}")
                    Timber.e("[$TAG] Intent action: ${intent.action}")
                    Timber.e("[$TAG] This usually means the service doesn't exist or isn't exported")
                    cont.resumeWithException(IllegalStateException("Failed to bind to plugin service: $pluginId. Service may not exist or may not be exported."))
                } else {
                    Timber.d("[$TAG] Service bind initiated successfully for plugin: $pluginId")
                    // Set a timeout to detect if onServiceConnected never gets called
                    scope.launch {
                        kotlinx.coroutines.delay(10000) // 10 second timeout
                        if (!connectionEstablished) {
                            Timber.e("[$TAG] Timeout: onServiceConnected() never called for plugin: $pluginId after 10 seconds")
                            Timber.e("[$TAG] This usually means onBind() returned null or the service crashed")
                            Timber.e("[$TAG] Check plugin service logs for onBind() calls")
                            if (!cont.isCompleted) {
                                cont.resumeWithException(IllegalStateException("Timeout waiting for service connection: $pluginId. Check if service onBind() is returning the binder."))
                            }
                        }
                    }
                }
            } catch (e: SecurityException) {
                Timber.e(e, "[$TAG] SecurityException binding to plugin service: $pluginId")
                cont.resumeWithException(IllegalStateException("Security exception binding to plugin: $pluginId", e))
            } catch (e: Exception) {
                Timber.e(e, "[$TAG] Exception binding to plugin service: $pluginId")
                cont.resumeWithException(IllegalStateException("Exception binding to plugin: $pluginId", e))
            }
            
            cont.invokeOnCancellation {
                Timber.d("[$TAG] Connection cancelled for plugin: $pluginId")
                try {
                    context.unbindService(connection)
                } catch (e: Exception) {
                    Timber.w(e, "[$TAG] Error unbinding service on cancellation")
                }
            }
        }
    }
    
    /**
     * Disconnect from a plugin.
     */
    suspend fun disconnectPlugin(pluginId: String) {
        Timber.d("[$TAG] Disconnecting plugin: $pluginId")
        val connected = _connectedPlugins.value[pluginId]
        if (connected == null) {
            Timber.w("[$TAG] Plugin $pluginId not connected, nothing to disconnect")
            return
        }
        
        try {
            Timber.d("[$TAG] Calling disconnect() on plugin service: $pluginId")
            connected.service.disconnect()
            Timber.d("[$TAG] Successfully called disconnect() on plugin: $pluginId")
        } catch (e: Exception) {
            Timber.e(e, "[$TAG] Error calling disconnect() on plugin: $pluginId")
        }
        
        try {
            Timber.d("[$TAG] Unbinding service for plugin: $pluginId")
            context.unbindService(connected.connection)
            Timber.d("[$TAG] Successfully unbound service for plugin: $pluginId")
        } catch (e: Exception) {
            Timber.e(e, "[$TAG] Error unbinding service for plugin: $pluginId")
        }
        
        _connectedPlugins.update { it - pluginId }
        Timber.d("[$TAG] Plugin $pluginId disconnected and removed from connected plugins")
    }
    
    /**
     * Disconnect from all plugins.
     */
    suspend fun disconnectAll() {
        Timber.d("[$TAG] Disconnecting all plugins")
        val pluginIds = _connectedPlugins.value.keys.toList()
        Timber.d("[$TAG] Disconnecting ${pluginIds.size} plugins: $pluginIds")
        pluginIds.forEach { disconnectPlugin(it) }
        Timber.d("[$TAG] All plugins disconnected")
    }
    
    /**
     * Enable a plugin (connects it automatically).
     */
    suspend fun enablePlugin(pluginId: String) {
        Timber.d("[$TAG] Enabling plugin: $pluginId")
        try {
            pluginPreferences.setEnabled(pluginId, true)
            Timber.d("[$TAG] Plugin $pluginId enabled in preferences, attempting to connect")
            connectPlugin(pluginId)
            Timber.d("[$TAG] Plugin $pluginId enabled and connected successfully")
        } catch (e: Exception) {
            Timber.e(e, "[$TAG] Failed to connect plugin after enabling: $pluginId")
            throw e
        }
    }
    
    /**
     * Disable a plugin (disconnects it).
     */
    suspend fun disablePlugin(pluginId: String) {
        Timber.d("[$TAG] Disabling plugin: $pluginId")
        try {
            pluginPreferences.setEnabled(pluginId, false)
            Timber.d("[$TAG] Plugin $pluginId disabled in preferences, disconnecting")
            disconnectPlugin(pluginId)
            Timber.d("[$TAG] Plugin $pluginId disabled and disconnected successfully")
        } catch (e: Exception) {
            Timber.e(e, "[$TAG] Error disabling plugin: $pluginId")
            throw e
        }
    }
    
    /**
     * Get a connected plugin by ID.
     */
    fun getPlugin(pluginId: String): ConnectedPluginData? {
        val plugin = _connectedPlugins.value[pluginId]
        if (plugin == null) {
            Timber.w("[$TAG] Plugin not found: $pluginId")
        }
        return plugin
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
        Timber.d("[$TAG] Searching in plugin: $pluginId, query: $query, types: $types, limit: $limit")
        val plugin = getPlugin(pluginId)
        if (plugin == null) {
            Timber.e("[$TAG] Plugin not connected for search: $pluginId")
            throw IllegalStateException("Plugin not connected: $pluginId")
        }
        
        return suspendCancellableCoroutine { cont ->
            Timber.d("[$TAG] Calling search() on plugin service: $pluginId")
            plugin.service.search(query, types, cursor, limit, object : IResultCallback.Stub() {
                override fun onSearchResult(result: AidlSearchResult) {
                    Timber.d("[$TAG] Search result received from plugin: $pluginId, songs: ${result.songs.size}, albums: ${result.albums.size}")
                    cont.resume(result.toDomain())
                }
                override fun onSearchError(errorCode: Int, message: String) {
                    Timber.e("[$TAG] Search error from plugin $pluginId: code=$errorCode, message=$message")
                    cont.resumeWithException(PluginException(errorCode, message))
                }
                override fun onSongResult(song: AidlSong) {}
                override fun onAlbumResult(album: AidlAlbum) {}
                override fun onArtistResult(artist: AidlArtist) {}
                override fun onPlaylistResult(playlist: AidlPlaylist) {}
                override fun onSongsResult(songs: MutableList<AidlSong>, nextCursor: String?) {}
                override fun onAlbumsResult(albums: MutableList<AidlAlbum>, nextCursor: String?) {}
                override fun onArtistsResult(artists: MutableList<AidlArtist>, nextCursor: String?) {}
                override fun onPlaylistsResult(playlists: MutableList<AidlPlaylist>, nextCursor: String?) {}
                override fun onCategoriesResult(categories: MutableList<AidlBrowseCategory>, nextCursor: String?) {}
                override fun onAudioStreamReady(stream: AudioStream) {}
                override fun onAudioStreamError(errorCode: Int, message: String) {}
                override fun onError(errorCode: Int, message: String) {
                    cont.resumeWithException(PluginException(errorCode, message))
                }
            })
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
        Timber.d("[$TAG] Getting browse categories from plugin: $pluginId, cursor: $cursor, limit: $limit")
        val plugin = getPlugin(pluginId)
        if (plugin == null) {
            Timber.e("[$TAG] Plugin not connected for browse categories: $pluginId")
            throw IllegalStateException("Plugin not connected: $pluginId")
        }
        
        return suspendCancellableCoroutine { cont ->
            plugin.service.getBrowseCategories(cursor, limit, object : IResultCallback.Stub() {
                override fun onSearchResult(result: AidlSearchResult) {}
                override fun onSearchError(errorCode: Int, message: String) {}
                override fun onSongResult(song: AidlSong) {}
                override fun onAlbumResult(album: AidlAlbum) {}
                override fun onArtistResult(artist: AidlArtist) {}
                override fun onPlaylistResult(playlist: AidlPlaylist) {}
                override fun onSongsResult(songs: MutableList<AidlSong>, nextCursor: String?) {}
                override fun onAlbumsResult(albums: MutableList<AidlAlbum>, nextCursor: String?) {}
                override fun onArtistsResult(artists: MutableList<AidlArtist>, nextCursor: String?) {}
                override fun onPlaylistsResult(playlists: MutableList<AidlPlaylist>, nextCursor: String?) {}
                override fun onCategoriesResult(categories: MutableList<AidlBrowseCategory>, nextCursor: String?) {
                    Timber.d("[$TAG] Browse categories received from plugin: $pluginId, count: ${categories.size}, nextCursor: $nextCursor")
                    cont.resume(PagedResult(categories.map { it.toDomain() }, nextCursor))
                }
                override fun onAudioStreamReady(stream: AudioStream) {}
                override fun onAudioStreamError(errorCode: Int, message: String) {}
                override fun onError(errorCode: Int, message: String) {
                    cont.resumeWithException(PluginException(errorCode, message))
                }
            })
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
        Timber.d("[$TAG] Getting library songs from plugin: $pluginId, cursor: $cursor, limit: $limit")
        val plugin = getPlugin(pluginId)
        if (plugin == null) {
            Timber.e("[$TAG] Plugin not connected for library songs: $pluginId")
            throw IllegalStateException("Plugin not connected: $pluginId")
        }
        
        return suspendCancellableCoroutine { cont ->
            plugin.service.getLibrarySongs(cursor, limit, object : IResultCallback.Stub() {
                override fun onSearchResult(result: AidlSearchResult) {}
                override fun onSearchError(errorCode: Int, message: String) {}
                override fun onSongResult(song: AidlSong) {}
                override fun onAlbumResult(album: AidlAlbum) {}
                override fun onArtistResult(artist: AidlArtist) {}
                override fun onPlaylistResult(playlist: AidlPlaylist) {}
                override fun onSongsResult(songs: MutableList<AidlSong>, nextCursor: String?) {
                    Timber.d("[$TAG] Library songs received from plugin: $pluginId, count: ${songs.size}, nextCursor: $nextCursor")
                    cont.resume(PagedResult(songs.map { it.toDomain() }, nextCursor))
                }
                override fun onAlbumsResult(albums: MutableList<AidlAlbum>, nextCursor: String?) {}
                override fun onArtistsResult(artists: MutableList<AidlArtist>, nextCursor: String?) {}
                override fun onPlaylistsResult(playlists: MutableList<AidlPlaylist>, nextCursor: String?) {}
                override fun onCategoriesResult(categories: MutableList<AidlBrowseCategory>, nextCursor: String?) {}
                override fun onAudioStreamReady(stream: AudioStream) {}
                override fun onAudioStreamError(errorCode: Int, message: String) {}
                override fun onError(errorCode: Int, message: String) {
                    cont.resumeWithException(PluginException(errorCode, message))
                }
            })
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
        Timber.d("[$TAG] Getting library albums from plugin: $pluginId, cursor: $cursor, limit: $limit")
        val plugin = getPlugin(pluginId)
        if (plugin == null) {
            Timber.e("[$TAG] Plugin not connected for library albums: $pluginId")
            throw IllegalStateException("Plugin not connected: $pluginId")
        }
        
        return suspendCancellableCoroutine { cont ->
            plugin.service.getLibraryAlbums(cursor, limit, object : IResultCallback.Stub() {
                override fun onSearchResult(result: AidlSearchResult) {}
                override fun onSearchError(errorCode: Int, message: String) {}
                override fun onSongResult(song: AidlSong) {}
                override fun onAlbumResult(album: AidlAlbum) {}
                override fun onArtistResult(artist: AidlArtist) {}
                override fun onPlaylistResult(playlist: AidlPlaylist) {}
                override fun onSongsResult(songs: MutableList<AidlSong>, nextCursor: String?) {}
                override fun onAlbumsResult(albums: MutableList<AidlAlbum>, nextCursor: String?) {
                    Timber.d("[$TAG] Library albums received from plugin: $pluginId, count: ${albums.size}, nextCursor: $nextCursor")
                    cont.resume(PagedResult(albums.map { it.toDomain() }, nextCursor))
                }
                override fun onArtistsResult(artists: MutableList<AidlArtist>, nextCursor: String?) {}
                override fun onPlaylistsResult(playlists: MutableList<AidlPlaylist>, nextCursor: String?) {}
                override fun onCategoriesResult(categories: MutableList<AidlBrowseCategory>, nextCursor: String?) {}
                override fun onAudioStreamReady(stream: AudioStream) {}
                override fun onAudioStreamError(errorCode: Int, message: String) {}
                override fun onError(errorCode: Int, message: String) {
                    cont.resumeWithException(PluginException(errorCode, message))
                }
            })
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
        Timber.d("[$TAG] Getting library artists from plugin: $pluginId, cursor: $cursor, limit: $limit")
        val plugin = getPlugin(pluginId)
        if (plugin == null) {
            Timber.e("[$TAG] Plugin not connected for library artists: $pluginId")
            throw IllegalStateException("Plugin not connected: $pluginId")
        }
        
        return suspendCancellableCoroutine { cont ->
            plugin.service.getLibraryArtists(cursor, limit, object : IResultCallback.Stub() {
                override fun onSearchResult(result: AidlSearchResult) {}
                override fun onSearchError(errorCode: Int, message: String) {}
                override fun onSongResult(song: AidlSong) {}
                override fun onAlbumResult(album: AidlAlbum) {}
                override fun onArtistResult(artist: AidlArtist) {}
                override fun onPlaylistResult(playlist: AidlPlaylist) {}
                override fun onSongsResult(songs: MutableList<AidlSong>, nextCursor: String?) {}
                override fun onAlbumsResult(albums: MutableList<AidlAlbum>, nextCursor: String?) {}
                override fun onArtistsResult(artists: MutableList<AidlArtist>, nextCursor: String?) {
                    Timber.d("[$TAG] Library artists received from plugin: $pluginId, count: ${artists.size}, nextCursor: $nextCursor")
                    cont.resume(PagedResult(artists.map { it.toDomain() }, nextCursor))
                }
                override fun onPlaylistsResult(playlists: MutableList<AidlPlaylist>, nextCursor: String?) {}
                override fun onCategoriesResult(categories: MutableList<AidlBrowseCategory>, nextCursor: String?) {}
                override fun onAudioStreamReady(stream: AudioStream) {}
                override fun onAudioStreamError(errorCode: Int, message: String) {}
                override fun onError(errorCode: Int, message: String) {
                    cont.resumeWithException(PluginException(errorCode, message))
                }
            })
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
        Timber.d("[$TAG] Getting library playlists from plugin: $pluginId, cursor: $cursor, limit: $limit")
        val plugin = getPlugin(pluginId)
        if (plugin == null) {
            Timber.e("[$TAG] Plugin not connected for library playlists: $pluginId")
            throw IllegalStateException("Plugin not connected: $pluginId")
        }
        
        return suspendCancellableCoroutine { cont ->
            plugin.service.getLibraryPlaylists(cursor, limit, object : IResultCallback.Stub() {
                override fun onSearchResult(result: AidlSearchResult) {}
                override fun onSearchError(errorCode: Int, message: String) {}
                override fun onSongResult(song: AidlSong) {}
                override fun onAlbumResult(album: AidlAlbum) {}
                override fun onArtistResult(artist: AidlArtist) {}
                override fun onPlaylistResult(playlist: AidlPlaylist) {}
                override fun onSongsResult(songs: MutableList<AidlSong>, nextCursor: String?) {}
                override fun onAlbumsResult(albums: MutableList<AidlAlbum>, nextCursor: String?) {}
                override fun onArtistsResult(artists: MutableList<AidlArtist>, nextCursor: String?) {}
                override fun onPlaylistsResult(playlists: MutableList<AidlPlaylist>, nextCursor: String?) {
                    Timber.d("[$TAG] Library playlists received from plugin: $pluginId, count: ${playlists.size}, nextCursor: $nextCursor")
                    cont.resume(PagedResult(playlists.map { it.toDomain() }, nextCursor))
                }
                override fun onCategoriesResult(categories: MutableList<AidlBrowseCategory>, nextCursor: String?) {}
                override fun onAudioStreamReady(stream: AudioStream) {}
                override fun onAudioStreamError(errorCode: Int, message: String) {}
                override fun onError(errorCode: Int, message: String) {
                    cont.resumeWithException(PluginException(errorCode, message))
                }
            })
        }
    }
    
    /**
     * Get artist details from a plugin.
     */
    suspend fun getArtist(mediaId: AidlMediaId): Artist {
        Timber.d("[$TAG] Getting artist from plugin: ${mediaId.pluginId}:${mediaId.sourceId}")
        val plugin = getPlugin(mediaId.pluginId)
        if (plugin == null) {
            Timber.e("[$TAG] Plugin not connected for artist: ${mediaId.pluginId}")
            throw IllegalStateException("Plugin not connected: ${mediaId.pluginId}")
        }

        return suspendCancellableCoroutine { cont ->
            plugin.service.getArtist(mediaId, object : IResultCallback.Stub() {
                override fun onSearchResult(result: AidlSearchResult) {}
                override fun onSearchError(errorCode: Int, message: String) {}
                override fun onSongResult(song: AidlSong) {}
                override fun onAlbumResult(album: AidlAlbum) {}
                override fun onArtistResult(artist: AidlArtist) {
                    Timber.d("[$TAG] Artist received from plugin: ${artist.name}")
                    cont.resume(artist.toDomain())
                }
                override fun onPlaylistResult(playlist: AidlPlaylist) {}
                override fun onSongsResult(songs: MutableList<AidlSong>, nextCursor: String?) {}
                override fun onAlbumsResult(albums: MutableList<AidlAlbum>, nextCursor: String?) {}
                override fun onArtistsResult(artists: MutableList<AidlArtist>, nextCursor: String?) {}
                override fun onPlaylistsResult(playlists: MutableList<AidlPlaylist>, nextCursor: String?) {}
                override fun onCategoriesResult(categories: MutableList<AidlBrowseCategory>, nextCursor: String?) {}
                override fun onAudioStreamReady(stream: AudioStream) {}
                override fun onAudioStreamError(errorCode: Int, message: String) {}
                override fun onError(errorCode: Int, message: String) {
                    cont.resumeWithException(PluginException(errorCode, message))
                }
            })
        }
    }

    /**
     * Get album details from a plugin.
     */
    suspend fun getAlbum(mediaId: AidlMediaId): Album {
        Timber.d("[$TAG] Getting album from plugin: ${mediaId.pluginId}:${mediaId.sourceId}")
        val plugin = getPlugin(mediaId.pluginId)
        if (plugin == null) {
            Timber.e("[$TAG] Plugin not connected for album: ${mediaId.pluginId}")
            throw IllegalStateException("Plugin not connected: ${mediaId.pluginId}")
        }

        return suspendCancellableCoroutine { cont ->
            plugin.service.getAlbum(mediaId, object : IResultCallback.Stub() {
                override fun onSearchResult(result: AidlSearchResult) {}
                override fun onSearchError(errorCode: Int, message: String) {}
                override fun onSongResult(song: AidlSong) {}
                override fun onAlbumResult(album: AidlAlbum) {
                    Timber.d("[$TAG] Album received from plugin: ${album.name}")
                    cont.resume(album.toDomain())
                }
                override fun onArtistResult(artist: AidlArtist) {}
                override fun onPlaylistResult(playlist: AidlPlaylist) {}
                override fun onSongsResult(songs: MutableList<AidlSong>, nextCursor: String?) {}
                override fun onAlbumsResult(albums: MutableList<AidlAlbum>, nextCursor: String?) {}
                override fun onArtistsResult(artists: MutableList<AidlArtist>, nextCursor: String?) {}
                override fun onPlaylistsResult(playlists: MutableList<AidlPlaylist>, nextCursor: String?) {}
                override fun onCategoriesResult(categories: MutableList<AidlBrowseCategory>, nextCursor: String?) {}
                override fun onAudioStreamReady(stream: AudioStream) {}
                override fun onAudioStreamError(errorCode: Int, message: String) {}
                override fun onError(errorCode: Int, message: String) {
                    cont.resumeWithException(PluginException(errorCode, message))
                }
            })
        }
    }

    /**
     * Get playlist details from a plugin.
     */
    suspend fun getPlaylist(mediaId: AidlMediaId): Playlist {
        Timber.d("[$TAG] Getting playlist from plugin: ${mediaId.pluginId}:${mediaId.sourceId}")
        val plugin = getPlugin(mediaId.pluginId)
        if (plugin == null) {
            Timber.e("[$TAG] Plugin not connected for playlist: ${mediaId.pluginId}")
            throw IllegalStateException("Plugin not connected: ${mediaId.pluginId}")
        }

        return suspendCancellableCoroutine { cont ->
            plugin.service.getPlaylist(mediaId, object : IResultCallback.Stub() {
                override fun onSearchResult(result: AidlSearchResult) {}
                override fun onSearchError(errorCode: Int, message: String) {}
                override fun onSongResult(song: AidlSong) {}
                override fun onAlbumResult(album: AidlAlbum) {}
                override fun onArtistResult(artist: AidlArtist) {}
                override fun onPlaylistResult(playlist: AidlPlaylist) {
                    Timber.d("[$TAG] Playlist received from plugin: ${playlist.name}")
                    cont.resume(playlist.toDomain())
                }
                override fun onSongsResult(songs: MutableList<AidlSong>, nextCursor: String?) {}
                override fun onAlbumsResult(albums: MutableList<AidlAlbum>, nextCursor: String?) {}
                override fun onArtistsResult(artists: MutableList<AidlArtist>, nextCursor: String?) {}
                override fun onPlaylistsResult(playlists: MutableList<AidlPlaylist>, nextCursor: String?) {}
                override fun onCategoriesResult(categories: MutableList<AidlBrowseCategory>, nextCursor: String?) {}
                override fun onAudioStreamReady(stream: AudioStream) {}
                override fun onAudioStreamError(errorCode: Int, message: String) {}
                override fun onError(errorCode: Int, message: String) {
                    cont.resumeWithException(PluginException(errorCode, message))
                }
            })
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
        Timber.d("[$TAG] Getting artist songs from plugin: ${artistId.pluginId}:${artistId.sourceId}")
        val plugin = getPlugin(artistId.pluginId)
        if (plugin == null) {
            Timber.e("[$TAG] Plugin not connected for artist songs: ${artistId.pluginId}")
            throw IllegalStateException("Plugin not connected: ${artistId.pluginId}")
        }

        return suspendCancellableCoroutine { cont ->
            plugin.service.getArtistSongs(artistId, cursor, limit, object : IResultCallback.Stub() {
                override fun onSearchResult(result: AidlSearchResult) {}
                override fun onSearchError(errorCode: Int, message: String) {}
                override fun onSongResult(song: AidlSong) {}
                override fun onAlbumResult(album: AidlAlbum) {}
                override fun onArtistResult(artist: AidlArtist) {}
                override fun onPlaylistResult(playlist: AidlPlaylist) {}
                override fun onSongsResult(songs: MutableList<AidlSong>, nextCursor: String?) {
                    Timber.d("[$TAG] Artist songs received from plugin, count: ${songs.size}, nextCursor: $nextCursor")
                    cont.resume(PagedResult(songs.map { it.toDomain() }, nextCursor))
                }
                override fun onAlbumsResult(albums: MutableList<AidlAlbum>, nextCursor: String?) {}
                override fun onArtistsResult(artists: MutableList<AidlArtist>, nextCursor: String?) {}
                override fun onPlaylistsResult(playlists: MutableList<AidlPlaylist>, nextCursor: String?) {}
                override fun onCategoriesResult(categories: MutableList<AidlBrowseCategory>, nextCursor: String?) {}
                override fun onAudioStreamReady(stream: AudioStream) {}
                override fun onAudioStreamError(errorCode: Int, message: String) {}
                override fun onError(errorCode: Int, message: String) {
                    cont.resumeWithException(PluginException(errorCode, message))
                }
            })
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
        Timber.d("[$TAG] Getting artist albums from plugin: ${artistId.pluginId}:${artistId.sourceId}")
        val plugin = getPlugin(artistId.pluginId)
        if (plugin == null) {
            Timber.e("[$TAG] Plugin not connected for artist albums: ${artistId.pluginId}")
            throw IllegalStateException("Plugin not connected: ${artistId.pluginId}")
        }

        return suspendCancellableCoroutine { cont ->
            plugin.service.getArtistAlbums(artistId, cursor, limit, object : IResultCallback.Stub() {
                override fun onSearchResult(result: AidlSearchResult) {}
                override fun onSearchError(errorCode: Int, message: String) {}
                override fun onSongResult(song: AidlSong) {}
                override fun onAlbumResult(album: AidlAlbum) {}
                override fun onArtistResult(artist: AidlArtist) {}
                override fun onPlaylistResult(playlist: AidlPlaylist) {}
                override fun onSongsResult(songs: MutableList<AidlSong>, nextCursor: String?) {}
                override fun onAlbumsResult(albums: MutableList<AidlAlbum>, nextCursor: String?) {
                    Timber.d("[$TAG] Artist albums received from plugin, count: ${albums.size}, nextCursor: $nextCursor")
                    cont.resume(PagedResult(albums.map { it.toDomain() }, nextCursor))
                }
                override fun onArtistsResult(artists: MutableList<AidlArtist>, nextCursor: String?) {}
                override fun onPlaylistsResult(playlists: MutableList<AidlPlaylist>, nextCursor: String?) {}
                override fun onCategoriesResult(categories: MutableList<AidlBrowseCategory>, nextCursor: String?) {}
                override fun onAudioStreamReady(stream: AudioStream) {}
                override fun onAudioStreamError(errorCode: Int, message: String) {}
                override fun onError(errorCode: Int, message: String) {
                    cont.resumeWithException(PluginException(errorCode, message))
                }
            })
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
        Timber.d("[$TAG] Getting playlist songs from plugin: ${playlistId.pluginId}:${playlistId.sourceId}")
        val plugin = getPlugin(playlistId.pluginId)
        if (plugin == null) {
            Timber.e("[$TAG] Plugin not connected for playlist songs: ${playlistId.pluginId}")
            throw IllegalStateException("Plugin not connected: ${playlistId.pluginId}")
        }

        return suspendCancellableCoroutine { cont ->
            plugin.service.getPlaylistSongs(playlistId, cursor, limit, object : IResultCallback.Stub() {
                override fun onSearchResult(result: AidlSearchResult) {}
                override fun onSearchError(errorCode: Int, message: String) {}
                override fun onSongResult(song: AidlSong) {}
                override fun onAlbumResult(album: AidlAlbum) {}
                override fun onArtistResult(artist: AidlArtist) {}
                override fun onPlaylistResult(playlist: AidlPlaylist) {}
                override fun onSongsResult(songs: MutableList<AidlSong>, nextCursor: String?) {
                    Timber.d("[$TAG] Playlist songs received from plugin, count: ${songs.size}, nextCursor: $nextCursor")
                    cont.resume(PagedResult(songs.map { it.toDomain() }, nextCursor))
                }
                override fun onAlbumsResult(albums: MutableList<AidlAlbum>, nextCursor: String?) {}
                override fun onArtistsResult(artists: MutableList<AidlArtist>, nextCursor: String?) {}
                override fun onPlaylistsResult(playlists: MutableList<AidlPlaylist>, nextCursor: String?) {}
                override fun onCategoriesResult(categories: MutableList<AidlBrowseCategory>, nextCursor: String?) {}
                override fun onAudioStreamReady(stream: AudioStream) {}
                override fun onAudioStreamError(errorCode: Int, message: String) {}
                override fun onError(errorCode: Int, message: String) {
                    cont.resumeWithException(PluginException(errorCode, message))
                }
            })
        }
    }

    private inner class HostCallbackImpl : IHostCallbackV1.Stub() {
        override fun getHostApiVersion(): Int = PluginConstants.CURRENT_API_VERSION
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
            Timber.e("[$TAG] Plugin reported error: code=$errorCode, message=$message")
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
