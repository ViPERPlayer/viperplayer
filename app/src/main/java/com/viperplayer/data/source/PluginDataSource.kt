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
import com.viperplayer.data.mapper.PluginMapper.toDomainNotNull
import com.viperplayer.data.mapper.PluginMapper.toDomainRef
import com.viperplayer.data.mapper.PluginMapper.toMediaType
import com.viperplayer.data.plugin.ConnectedPlugin
import com.viperplayer.data.plugin.PluginActionNotifier
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
import com.viperplayer.data.sync.push.PluginWriteGateway
import com.viperplayer.data.sync.push.RemoteSet
import com.viperplayer.data.sync.push.WriteCapability
import com.viperplayer.plugin.host.SourceClient
import com.viperplayer.plugin.model.LyricsRequest
import com.viperplayer.plugin.model.MediaType
import com.viperplayer.plugin.model.PageRequest
import com.viperplayer.plugin.model.PluginErrorCode
import com.viperplayer.plugin.model.PluginException
import com.viperplayer.plugin.model.RequiredAction
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
import kotlinx.coroutines.flow.map
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
    private val actionNotifier: PluginActionNotifier,
) : PluginWriteGateway {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _discoveredPlugins = MutableStateFlow<Map<String, DiscoveredPlugin>>(emptyMap())
    val discoveredPlugins: StateFlow<Map<String, DiscoveredPlugin>> = _discoveredPlugins.asStateFlow()

    private val _connectedPlugins = MutableStateFlow<Map<String, ConnectedPlugin>>(emptyMap())
    val connectedPlugins: StateFlow<Map<String, ConnectedPlugin>> = _connectedPlugins.asStateFlow()

    /** Pending user actions per connected plugin, refreshed at connect, on push, and on demand. */
    private val _pluginActions = MutableStateFlow<Map<String, List<RequiredAction>>>(emptyMap())
    val pluginActions: StateFlow<Map<String, List<RequiredAction>>> = _pluginActions.asStateFlow()

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
            refreshPluginStatus(discovered.id)
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
            _pluginActions.update { it - pluginId }
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
            _pluginActions.update { it - pluginId }
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
        _pluginActions.update { it - pluginId }
    }

    /** Re-query [pluginId]'s pending user actions. No-op if the plugin isn't connected. */
    suspend fun refreshPluginStatus(pluginId: String) {
        val connected = _connectedPlugins.value[pluginId] ?: return
        runCatching { connected.client.getStatus() }
            .onSuccess { status ->
                _pluginActions.update { it + (pluginId to status.actions) }
                if (status.actions.isEmpty()) actionNotifier.cancel(pluginId)
            }
            .onFailure { Timber.w(it, "getStatus failed for $pluginId") }
    }

    /** Re-query every connected plugin's status (e.g. after returning from a resolution step). */
    suspend fun refreshAllPluginStatuses() {
        _connectedPlugins.value.keys.forEach { refreshPluginStatus(it) }
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

    /**
     * Resolve a connected plugin, WAITING briefly for it to (re)connect rather than failing
     * instantly. On resume from background the plugin service unbinds, so a screen that re-fetches
     * before the handshake lands would otherwise hit "Plugin not connected". We only wait when it's
     * worth it — a plugin the user disabled, or one that's been uninstalled, fails fast with a
     * distinct reason so the UI can say something useful instead of spinning.
     */
    private suspend fun awaitPlugin(pluginId: String, timeoutMs: Long = CONNECT_WAIT_MS): ConnectedPlugin {
        _connectedPlugins.value[pluginId]?.let { return it }

        // Prefer the plugin's human name for any error shown to the user; fall back to the id.
        val name = _discoveredPlugins.value[pluginId]?.name ?: pluginId

        // Turned off by the user — nothing will connect it, so don't wait.
        if (pluginPreferences.isDisabledSync(pluginId)) {
            throw PluginException(PluginErrorCode.UNSUPPORTED, "$name is turned off")
        }
        // Not discovered — most likely uninstalled. Give discovery a brief chance first (a
        // package-added broadcast may still be in flight) before declaring it gone.
        if (pluginId !in _discoveredPlugins.value) {
            val appeared = withTimeoutOrNull(DISCOVERY_WAIT_MS) {
                _discoveredPlugins.map { pluginId in it }.first { it }
            }
            if (appeared != true) {
                throw PluginException(PluginErrorCode.NOT_FOUND, "$name is no longer installed")
            }
        }
        // Discovered and enabled — wait for the connection / handshake to complete.
        return withTimeoutOrNull(timeoutMs) { _connectedPlugins.mapNotNull { it[pluginId] }.first() }
            ?: throw PluginException(PluginErrorCode.INTERNAL, "Couldn't connect to $name")
    }

    /**
     * Resolve the source client for [pluginId], first enforcing user-toggled **offline mode**: when the
     * setting is on and [pluginId] is a REMOTE plugin (anything but the embedded local library), the
     * call is rejected with a typed [PluginException] ([PluginErrorCode.NETWORK]) BEFORE any binder
     * round-trip — so every read op that funnels through here (search/browse/home/library/detail) fails
     * gracefully into its `runCatching` wrapper as `Result.failure` instead of hitting the network. The
     * on-device local library is never gated (it's inherently offline). Stream resolution passes
     * [allowOffline] = true so already-downloaded content can still be played offline; a truly
     * remote-only track then fails at the network layer rather than being pre-empted here.
     */
    private suspend fun source(pluginId: String, allowOffline: Boolean = false): SourceClient {
        if (shouldBlockForOffline(pluginId, settingsRepository.offlineMode.first(), allowOffline)) {
            throw PluginException(PluginErrorCode.NETWORK, "Offline mode is on")
        }
        return awaitPlugin(pluginId).client.source
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
        // Offline mode: suggestions are a remote fetch — emit nothing rather than reaching out.
        if (settingsRepository.offlineMode.first()) {
            trySend(emptyList())
            return@channelFlow
        }
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
            source(pluginId).getLibraryArtists(PageRequest(cursor, limit)).toDomainNotNull { it.toDomainRef(pluginId) }
        }.onFailure { Timber.e(it, "library artists failed for $pluginId") }

    suspend fun getLibraryPlaylists(pluginId: String, cursor: String?, limit: Int): Result<PagedResult<Playlist>> =
        runCatching {
            source(pluginId).getLibraryPlaylists(PageRequest(cursor, limit)).toDomain { it.toDomain(pluginId) }
        }.onFailure { Timber.e(it, "library playlists failed for $pluginId") }

    // ---- Library WRITE (push) — the PUSH direction of two-way sync. Each call routes to the plugin's
    // optional write verb; a plugin that doesn't advertise `libraryWrite` (or predates the verb) throws
    // UNSUPPORTED, which the push sync manager records as a skipped (not failed) outbox entry. ----

    /**
     * The account-library write capability of [pluginId] right now. Reads the *already-connected*
     * plugin's manifest (no awaitPlugin/timeout): a plugin that isn't currently connected reports
     * [WriteCapability.UNAVAILABLE] (retry later), never UNSUPPORTED, so a transient disconnect can't
     * strand queued changes.
     */
    override suspend fun libraryWriteCapability(pluginId: String): WriteCapability {
        val connected = _connectedPlugins.value[pluginId] ?: return WriteCapability.UNAVAILABLE
        val source = connected.client.source ?: return WriteCapability.UNSUPPORTED
        return if (source.capabilities.libraryWrite) WriteCapability.SUPPORTED else WriteCapability.UNSUPPORTED
    }

    // Write/push ops pass allowOffline = true: the library-sync push outbox owns its own
    // queue-and-retry, so offline mode must not pre-empt it here (its calls fail on the real network
    // and are retried by the outbox as before, rather than being suppressed by the offline gate).
    override suspend fun setLiked(pluginId: String, trackId: String, liked: Boolean): Result<Unit> =
        runCatching { source(pluginId, allowOffline = true).setLiked(trackId, liked) }
            .onFailure { Timber.e(it, "setLiked failed for $pluginId/$trackId") }

    override suspend fun setFollowed(pluginId: String, artistId: String, followed: Boolean): Result<Unit> =
        runCatching { source(pluginId, allowOffline = true).setFollowed(artistId, followed) }
            .onFailure { Timber.e(it, "setFollowed failed for $pluginId/$artistId") }

    override suspend fun createPlaylist(pluginId: String, name: String): Result<String> =
        runCatching { source(pluginId, allowOffline = true).createPlaylist(name) }
            .onFailure { Timber.e(it, "createPlaylist failed for $pluginId") }

    override suspend fun renamePlaylist(pluginId: String, playlistId: String, name: String): Result<Unit> =
        runCatching { source(pluginId, allowOffline = true).renamePlaylist(playlistId, name) }
            .onFailure { Timber.e(it, "renamePlaylist failed for $pluginId/$playlistId") }

    override suspend fun deletePlaylist(pluginId: String, playlistId: String): Result<Unit> =
        runCatching { source(pluginId, allowOffline = true).deletePlaylist(playlistId) }
            .onFailure { Timber.e(it, "deletePlaylist failed for $pluginId/$playlistId") }

    override suspend fun addTrackToPlaylist(pluginId: String, playlistId: String, trackId: String): Result<Unit> =
        runCatching { source(pluginId, allowOffline = true).addTrackToPlaylist(playlistId, trackId) }
            .onFailure { Timber.e(it, "addTrackToPlaylist failed for $pluginId/$playlistId") }

    override suspend fun removeTrackFromPlaylist(pluginId: String, playlistId: String, trackId: String): Result<Unit> =
        runCatching { source(pluginId, allowOffline = true).removeTrackFromPlaylist(playlistId, trackId) }
            .onFailure { Timber.e(it, "removeTrackFromPlaylist failed for $pluginId/$playlistId") }

    /**
     * First page of the account's liked/saved track source ids, for push reconciliation, or null.
     *
     * PROXY / best-effort by design: the SDK has no distinct "liked-only" read, so we use the
     * account's library-saved songs ([getLibrarySongs]) as a proxy for the remote liked set. On some
     * services "saved to library" is not exactly the same as "liked", so this is a deliberate
     * approximation of the remote set the reconciler diffs against, given the SDK read surface.
     * Bounded consequence: a redundant like may be skipped as already-present, but real changes still
     * push — an unlike (a removal off this set) is never suppressed by an over-broad set. Never assume
     * this equals the true liked set exactly.
     */
    override suspend fun likedTrackIds(pluginId: String, limit: Int): RemoteSet? =
        runCatching {
            // allowOffline = true: this is a push-sync reconciliation read owned by the outbox, not a
            // user-facing browse — the offline gate must not suppress it (see write-op note above).
            val page = source(pluginId, allowOffline = true).getLibrarySongs(PageRequest(null, limit))
            // Complete only if there's no next page — otherwise an absent id is inconclusive.
            RemoteSet(page.items.map { it.id }.toSet(), complete = page.nextCursor == null)
        }.getOrNull()

    /**
     * First page of the account's followed artist source ids, for push reconciliation, or null.
     *
     * PROXY / best-effort by design: the SDK has no distinct "followed-only" read, so we use the
     * account's library-saved artists ([getLibraryArtists]) as a proxy for the remote followed set. On
     * some services "saved to library" is not exactly the same as "followed", so this is a deliberate
     * approximation of the remote set the reconciler diffs against, given the SDK read surface.
     * Bounded consequence: a redundant follow may be skipped as already-present, but real changes still
     * push — an unfollow (a removal off this set) is never suppressed by an over-broad set.
     */
    override suspend fun followedArtistIds(pluginId: String, limit: Int): RemoteSet? =
        runCatching {
            // allowOffline = true: push-sync reconciliation read (see likedTrackIds), not gated offline.
            val page = source(pluginId, allowOffline = true).getLibraryArtists(PageRequest(null, limit))
            RemoteSet(page.items.map { it.id }.toSet(), complete = page.nextCursor == null)
        }.getOrNull()

    suspend fun getSong(id: MediaId): Result<Song> = runCatching {
        source(id.routingPluginId).getSong(id.sourceId).toDomain(id.routingPluginId)
    }.onFailure { Timber.e(it, "getSong failed for $id") }

    suspend fun getAlbum(id: MediaId): Result<Album> = runCatching {
        source(id.routingPluginId).getAlbum(id.sourceId).toDomain(id.routingPluginId)
    }.onFailure { Timber.e(it, "getAlbum failed for $id") }

    suspend fun getArtist(id: MediaId): Result<ArtistDetail> = runCatching {
        source(id.routingPluginId).getArtist(id.sourceId).toDomainDetail(id.routingPluginId)
    }.onFailure { Timber.e(it, "getArtist failed for $id") }

    suspend fun getPlaylist(id: MediaId): Result<Playlist> = runCatching {
        source(id.routingPluginId).getPlaylist(id.sourceId).toDomain(id.routingPluginId)
    }.onFailure { Timber.e(it, "getPlaylist failed for $id") }

    suspend fun getLyrics(
        id: MediaId,
        title: String?,
        artist: String?,
        album: String?,
        durationMs: Long?
    ): Result<Lyrics?> = runCatching {
        // Lyrics is an optional capability — client.lyrics is null when the plugin doesn't advertise it.
        awaitPlugin(id.routingPluginId).client.lyrics?.getLyrics(
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
            source(artistId.routingPluginId).getArtistSongs(artistId.sourceId, PageRequest(cursor, limit))
                .toDomain { it.toDomain(artistId.routingPluginId) }
        }.onFailure { Timber.e(it, "artist songs failed for $artistId") }

    suspend fun getArtistAlbums(artistId: MediaId, cursor: String?, limit: Int): Result<PagedResult<Album>> =
        runCatching {
            source(artistId.routingPluginId).getArtistAlbums(artistId.sourceId, PageRequest(cursor, limit))
                .toDomain { it.toDomain(artistId.routingPluginId) }
        }.onFailure { Timber.e(it, "artist albums failed for $artistId") }

    suspend fun getPlaylistSongs(playlistId: MediaId, cursor: String?, limit: Int): Result<PagedResult<Song>> =
        runCatching {
            source(playlistId.routingPluginId).getPlaylistSongs(playlistId.sourceId, PageRequest(cursor, limit))
                .toDomain { it.toDomain(playlistId.routingPluginId) }
        }.onFailure { Timber.e(it, "playlist songs failed for $playlistId") }

    /** Radio/autoplay seed: songs related to [songId], used to keep the queue going. */
    suspend fun getRelatedSongs(songId: MediaId): Result<PagedResult<Song>> =
        runCatching {
            source(songId.routingPluginId).getRelatedSongs(songId.sourceId)
                .toDomain { it.toDomain(songId.routingPluginId) }
        }.onFailure { Timber.e(it, "related songs failed for $songId") }

    suspend fun getHomeContent(pluginId: String): Result<HomeContent> = runCatching {
        source(pluginId).getHome().toDomain(pluginId)
    }.onFailure { Timber.e(it, "home content failed for $pluginId") }

    /**
     * Home feed filtered by a top-level chip. Chip-filtering is an OPTIONAL SDK verb: a plugin (or an
     * older APK) that doesn't implement it answers [PluginErrorCode.UNSUPPORTED], which we catch and
     * fall back to the base [getHome] — so a chip tap on such a plugin gracefully shows the base feed
     * rather than erroring. [chipId] null resolves to the base feed directly.
     */
    suspend fun getHomeContent(pluginId: String, chipId: String?): Result<HomeContent> = runCatching {
        val client = source(pluginId)
        if (chipId == null) {
            client.getHome().toDomain(pluginId)
        } else {
            try {
                client.getHome(chipId).toDomain(pluginId)
            } catch (e: PluginException) {
                if (e.code == PluginErrorCode.UNSUPPORTED) client.getHome().toDomain(pluginId) else throw e
            }
        }
    }.onFailure { Timber.e(it, "filtered home content failed for $pluginId") }

    /**
     * Fetch the next page of Home sections for [continuation] (infinite scroll). Pagination is an
     * OPTIONAL SDK verb: a plugin (or older APK) without it answers [PluginErrorCode.UNSUPPORTED],
     * which we degrade to a null result (no more pages) rather than surfacing an error.
     */
    suspend fun getHomeContinuation(pluginId: String, continuation: String): Result<HomeContent?> = runCatching {
        try {
            source(pluginId).getHomeContinuation(continuation).toDomain(pluginId)
        } catch (e: PluginException) {
            if (e.code == PluginErrorCode.UNSUPPORTED) null else throw e
        }
    }.onFailure { Timber.e(it, "home continuation failed for $pluginId") }

    suspend fun filterSection(pluginId: String, sectionId: String, filterKey: String): Result<SearchResult> =
        runCatching {
            source(pluginId).filterSection(sectionId, filterKey).toDomain(pluginId)
        }.onFailure { Timber.e(it, "filter section failed for $pluginId") }

    /** Resolve a playable stream for [mediaId]. The returned [ResolvedStream] may carry a live FD. */
    suspend fun getStream(mediaId: MediaId, isVideo: Boolean): Result<ResolvedStream> = runCatching {
        val type = if (isVideo) MediaType.VIDEO else MediaType.SONG
        val maxBitrateKbps = maxBitrateKbpsFor(settingsRepository.audioQuality.first())
        // allowOffline: don't pre-empt stream resolution in offline mode — a downloaded/cached track may
        // still resolve, and a remote-only track fails at the network layer (graceful) rather than here.
        source(mediaId.routingPluginId, allowOffline = true).resolveStream(mediaId.sourceId, type, maxBitrateKbps)
    }.onFailure {
        Timber.e(it, "getStream failed for $mediaId")
        // Playback-time auth failures usually mean the plugin now has a pending action to show;
        // playback is interrupted, so also surface it as a notification.
        if ((it as? PluginException)?.code == PluginErrorCode.AUTH_REQUIRED) {
            scope.launch {
                refreshPluginStatus(mediaId.routingPluginId)
                _pluginActions.value[mediaId.routingPluginId]?.firstOrNull()?.let { action ->
                    actionNotifier.notify(mediaId.routingPluginId, action)
                }
            }
        }
    }

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

        override fun onStatusChanged(pluginId: String) {
            scope.launch { refreshPluginStatus(pluginId) }
        }

        override fun onAuthStateChanged(pluginId: String) {
            // Signing in/out usually adds or clears a pending LOGIN action.
            scope.launch { refreshPluginStatus(pluginId) }
        }

        override fun onError(pluginId: String, code: Int, message: String?) {
            Timber.e("Plugin $pluginId error: code=$code, message=$message")
            if (code == PluginErrorCode.AUTH_REQUIRED) {
                scope.launch { refreshPluginStatus(pluginId) }
            }
        }
    }

    private companion object {
        /** How long a plugin operation waits for a discovered, enabled plugin to (re)connect. */
        const val CONNECT_WAIT_MS = 8_000L
        /** Grace window for a just-installed plugin to show up in discovery before we call it uninstalled. */
        const val DISCOVERY_WAIT_MS = 2_000L
    }
}
