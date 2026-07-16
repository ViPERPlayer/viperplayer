package com.viperplayer.domain.lastfm

/**
 * The user-facing Last.fm configuration, surfaced to the settings screen. Pure data.
 *
 * @param username the logged-in Last.fm username, or null when not authenticated. The session key
 *   itself is intentionally NOT exposed here — the UI never needs it and it must not leak into state.
 * @param isAuthenticated whether a session key is stored (i.e. login succeeded).
 * @param scrobblingEnabled master switch for submitting `track.scrobble`.
 * @param nowPlayingEnabled whether to send `track.updateNowPlaying` on track start.
 * @param scrobbleOnWifiOnly only submit scrobbles / now-playing on an unmetered (Wi-Fi) network.
 */
data class LastfmSettings(
    val username: String? = null,
    val isAuthenticated: Boolean = false,
    val scrobblingEnabled: Boolean = true,
    val nowPlayingEnabled: Boolean = true,
    val scrobbleOnWifiOnly: Boolean = false,
)
