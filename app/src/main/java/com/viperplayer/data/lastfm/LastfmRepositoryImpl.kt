package com.viperplayer.data.lastfm

import com.viperplayer.data.repository.NetworkConnectivityChecker
import com.viperplayer.domain.lastfm.LastfmRepository
import com.viperplayer.domain.lastfm.LastfmSettings
import com.viperplayer.domain.lastfm.LastfmRequests
import com.viperplayer.domain.lastfm.LoginResult
import com.viperplayer.domain.lastfm.PendingScrobble
import com.viperplayer.domain.lastfm.ScrobbleTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default [LastfmRepository]: wires the [LastfmCredentialStore] (session key + toggles), the
 * [LastfmApi] (signed network calls), the offline [PendingScrobbleDao] queue, and the
 * [NetworkConnectivityChecker] (for the Wi-Fi-only policy).
 *
 * SECURITY: the password only flows through [login] into [LastfmApi.getMobileSession] and is never
 * stored/logged; only the returned session key is persisted (by the credential store).
 */
@Singleton
class LastfmRepositoryImpl @Inject constructor(
    private val credentialStore: LastfmCredentialStore,
    private val api: LastfmApi,
    private val pendingScrobbleDao: PendingScrobbleDao,
    private val connectivity: NetworkConnectivityChecker,
) : LastfmRepository {

    // Serializes queue drains so a scrobble submission and a flush can't double-submit the same rows.
    private val flushMutex = Mutex()

    // Off-main scope for the fire-and-forget setting writes + the connectivity-triggered auto-flush.
    // The suspend network paths run on the caller's coroutine (the scrobbler's IO scope).
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // Retry the offline queue when connectivity is (re)gained — the "next connectivity" trigger.
        // (Scrobbles also drain on the next successful submission.) drainQueue itself re-checks the
        // Wi-Fi-only policy and auth, so this is a no-op when not allowed.
        scope.launch {
            // isInternetAvailable is a StateFlow (already conflated/de-duped); collect the true edges.
            connectivity.isInternetAvailable
                .filter { it }
                .collect { flushPendingScrobbles() }
        }
    }

    override val settings: Flow<LastfmSettings> = credentialStore.settings

    override val isApiConfigured: Boolean get() = api.isConfigured

    override suspend fun login(username: String, password: String): LoginResult {
        val trimmed = username.trim()
        if (trimmed.isEmpty() || password.isEmpty()) {
            return LoginResult.Failed("Enter your username and password")
        }
        // The password lives only for this call; getMobileSession discards it after signing/sending.
        return when (val result = api.getMobileSession(trimmed, password)) {
            is LastfmResult.Success -> {
                credentialStore.saveSession(result.value.name, result.value.key)
                Timber.i("Last.fm login succeeded for user")
                // Send anything that queued before we were authed (rare, but cheap and correct).
                scope.launch { runCatching { flushPendingScrobbles() } }
                LoginResult.Success(result.value.name)
            }

            is LastfmResult.ApiError -> LoginResult.Failed(
                result.message.ifBlank { "Login failed (error ${result.code})" }
            )

            is LastfmResult.NetworkError -> LoginResult.NetworkError
            LastfmResult.NotConfigured -> LoginResult.NotConfigured
        }
    }

    override suspend fun logout() {
        credentialStore.clearSession()
    }

    override fun setScrobblingEnabled(enabled: Boolean) = fireAndForget {
        credentialStore.setScrobblingEnabled(enabled)
    }

    override fun setNowPlayingEnabled(enabled: Boolean) = fireAndForget {
        credentialStore.setNowPlayingEnabled(enabled)
    }

    override fun setScrobbleOnWifiOnly(enabled: Boolean) = fireAndForget {
        credentialStore.setScrobbleOnWifiOnly(enabled)
    }

    override suspend fun updateNowPlaying(track: ScrobbleTrack) {
        if (!track.isValid) return
        val (settings, sessionKey) = credentialStore.snapshot()
        if (!settings.nowPlayingEnabled) return
        if (sessionKey == null) return
        if (!isNetworkAllowed(settings)) return
        // Now-playing is ephemeral; a failure is not queued.
        api.updateNowPlaying(sessionKey, track)
    }

    override suspend fun submitScrobble(track: ScrobbleTrack, startTimestampSeconds: Long) {
        if (!track.isValid) return
        val (settings, sessionKey) = credentialStore.snapshot()
        if (!settings.scrobblingEnabled) return
        // Not logged in: nothing to attribute the scrobble to; drop it.
        if (sessionKey == null) return

        val scrobble = PendingScrobble(track = track, timestampSeconds = startTimestampSeconds)

        // Always persist first so the scrobble survives a crash/kill mid-submission; the drain removes
        // it on success. INSERT OR IGNORE de-dupes against the unique (artist, track, timestamp) index.
        pendingScrobbleDao.insert(PendingScrobbleEntity.fromDomain(scrobble))

        if (!isNetworkAllowed(settings)) {
            Timber.d("Last.fm: network not allowed now — scrobble queued for later")
            return
        }
        drainQueue(sessionKey)
    }

    override suspend fun flushPendingScrobbles() {
        val (settings, sessionKey) = credentialStore.snapshot()
        if (!settings.scrobblingEnabled) return
        if (sessionKey == null) return
        if (!isNetworkAllowed(settings)) return
        drainQueue(sessionKey)
    }

    /**
     * Submits every queued scrobble in Last.fm-sized, oldest-first batches, deleting each batch that is
     * accepted. Stops early on the first network error so the remaining rows stay queued for the next
     * attempt. An API-level rejection of a batch still removes it (it will never succeed on retry).
     *
     * The [flushMutex] serializes drains so a scrobble submission and a background flush can't submit
     * or delete the same rows concurrently. Rows are read once (oldest-first) and the exact entity
     * objects are carried through, so a successful batch deletes precisely the rows it submitted.
     */
    private suspend fun drainQueue(sessionKey: String) = flushMutex.withLock {
        // DAO already returns rows oldest-first; chunk into Last.fm's max batch size, carrying the
        // entity rows so we delete exactly what we submit.
        val rows = pendingScrobbleDao.getAll()
        if (rows.isEmpty()) return@withLock

        for (entityBatch in rows.chunked(LastfmRequests.MAX_BATCH)) {
            val batch = entityBatch.map { it.toDomain() }
            when (api.scrobble(sessionKey, batch)) {
                is LastfmResult.Success -> pendingScrobbleDao.delete(entityBatch)
                is LastfmResult.ApiError -> {
                    // The server rejected these (e.g. malformed); retrying won't help — drop them.
                    Timber.w("Last.fm: dropping ${entityBatch.size} rejected scrobble(s)")
                    pendingScrobbleDao.delete(entityBatch)
                }

                is LastfmResult.NetworkError -> {
                    Timber.d("Last.fm: network error draining queue — ${entityBatch.size} kept for retry")
                    return@withLock
                }

                LastfmResult.NotConfigured -> return@withLock
            }
        }
    }

    /** Whether a call is allowed by connectivity + the Wi-Fi-only policy right now. */
    private fun isNetworkAllowed(settings: LastfmSettings): Boolean {
        if (!connectivity.isConnected()) return false
        if (settings.scrobbleOnWifiOnly && !connectivity.isUnmetered()) return false
        return true
    }

    private inline fun fireAndForget(crossinline block: suspend () -> Unit) {
        scope.launch { block() }
    }
}
