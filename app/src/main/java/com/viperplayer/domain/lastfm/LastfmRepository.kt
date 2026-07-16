package com.viperplayer.domain.lastfm

import kotlinx.coroutines.flow.Flow

/**
 * Coordinates Last.fm scrobbling: authentication, now-playing updates, scrobble submission, and the
 * offline retry queue. All network + persistence lives behind this interface; the UI (a ViewModel)
 * only observes [settings] and forwards login/logout/toggle events.
 */
interface LastfmRepository {

    /** The current Last.fm settings + auth state. The raw session key is never exposed. */
    val settings: Flow<LastfmSettings>

    /** Whether a real API key/secret was built in. When false, the UI must explain it won't function. */
    val isApiConfigured: Boolean

    /**
     * Logs in with [username] + [password] via `auth.getMobileSession`. On success the returned
     * session key (and username) are persisted; the password is discarded and never stored. Returns a
     * [LoginResult] describing the outcome for the UI.
     */
    suspend fun login(username: String, password: String): LoginResult

    /** Clears the stored session (logout). */
    suspend fun logout()

    fun setScrobblingEnabled(enabled: Boolean)
    fun setNowPlayingEnabled(enabled: Boolean)
    fun setScrobbleOnWifiOnly(enabled: Boolean)

    /**
     * Called when a track starts: sends `track.updateNowPlaying` if enabled + authed + allowed by the
     * Wi-Fi-only policy. Best-effort; failures are swallowed (now-playing is ephemeral, not queued).
     */
    suspend fun updateNowPlaying(track: ScrobbleTrack)

    /**
     * Called when a play qualifies as a scrobble. Attempts to submit it (draining any queued scrobbles
     * with it); on a network failure or when offline/Wi-Fi-gated it is added to the offline queue and
     * retried later. Eligibility is decided by the caller via [ScrobbleEligibility].
     *
     * @param track the played track.
     * @param startTimestampSeconds UNIX seconds the track started (the scrobble timestamp).
     */
    suspend fun submitScrobble(track: ScrobbleTrack, startTimestampSeconds: Long)

    /**
     * Attempts to drain the offline queue (e.g. on reconnect). No-op when not authed / not configured /
     * offline / Wi-Fi-gated.
     */
    suspend fun flushPendingScrobbles()
}

/** Result of a login attempt, surfaced to the settings screen. */
sealed interface LoginResult {
    data class Success(val username: String) : LoginResult
    /** Bad username/password or another Last.fm-side rejection. */
    data class Failed(val message: String) : LoginResult
    /** Could not reach Last.fm (offline / transport error) — the user can retry. */
    data object NetworkError : LoginResult
    /** No real API key/secret is built in; login is impossible until one is supplied. */
    data object NotConfigured : LoginResult
}
