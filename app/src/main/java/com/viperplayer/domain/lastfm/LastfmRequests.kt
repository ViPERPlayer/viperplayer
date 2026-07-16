package com.viperplayer.domain.lastfm

/**
 * Pure builders for the parameter maps of the Last.fm methods this app calls. Kept Android-free and
 * side-effect free so the exact wire parameters (including the batched-scrobble `[i]` array notation)
 * can be asserted in unit tests. Signing is layered on separately by [LastfmSigner]; these builders
 * produce the UNSIGNED parameter map.
 *
 * All builders require an [apiKey] and a [sessionKey] (`sk`) — the caller must be authenticated.
 */
object LastfmRequests {

    const val METHOD_GET_MOBILE_SESSION = "auth.getMobileSession"
    const val METHOD_UPDATE_NOW_PLAYING = "track.updateNowPlaying"
    const val METHOD_SCROBBLE = "track.scrobble"

    /**
     * Parameters for `auth.getMobileSession`. Sends the username + password to exchange them for a
     * session key. SECURITY: the [password] appears ONLY in this transient request map and is never
     * stored; callers must sign, send, and discard it immediately.
     */
    fun getMobileSession(apiKey: String, username: String, password: String): Map<String, String> =
        linkedMapOf(
            "method" to METHOD_GET_MOBILE_SESSION,
            "api_key" to apiKey,
            "username" to username,
            "password" to password,
        )

    /**
     * Parameters for `track.updateNowPlaying` for [track]. Optional fields are omitted when
     * null/blank/non-positive so the signature only covers present parameters.
     */
    fun updateNowPlaying(
        apiKey: String,
        sessionKey: String,
        track: ScrobbleTrack,
    ): Map<String, String> {
        val params = linkedMapOf(
            "method" to METHOD_UPDATE_NOW_PLAYING,
            "api_key" to apiKey,
            "sk" to sessionKey,
            "artist" to track.artist,
            "track" to track.track,
        )
        putTrackOptionals(params, track, index = null)
        return params
    }

    /**
     * Parameters for a `track.scrobble` batch. Last.fm accepts up to 50 scrobbles per call, each field
     * suffixed with `[i]`. A single scrobble is just a batch of one (still `[0]`). Past [timestamps]
     * are honored, which is how the offline queue is drained.
     */
    fun scrobble(
        apiKey: String,
        sessionKey: String,
        scrobbles: List<PendingScrobble>,
    ): Map<String, String> {
        require(scrobbles.isNotEmpty()) { "scrobble batch must not be empty" }
        require(scrobbles.size <= MAX_BATCH) { "scrobble batch exceeds Last.fm max of $MAX_BATCH" }
        val params = linkedMapOf(
            "method" to METHOD_SCROBBLE,
            "api_key" to apiKey,
            "sk" to sessionKey,
        )
        scrobbles.forEachIndexed { index, pending ->
            val track = pending.track
            params["artist[$index]"] = track.artist
            params["track[$index]"] = track.track
            params["timestamp[$index]"] = pending.timestampSeconds.toString()
            putTrackOptionals(params, track, index)
        }
        return params
    }

    /** Maximum scrobbles Last.fm accepts in one `track.scrobble` call. */
    const val MAX_BATCH = 50

    /**
     * Adds the optional track fields (album, albumArtist, trackNumber, duration, mbid) to [params]
     * when present. [index] is the batch index for `track.scrobble` (`album[0]`), or null for the
     * single-parameter `track.updateNowPlaying` (`album`).
     */
    private fun putTrackOptionals(params: MutableMap<String, String>, track: ScrobbleTrack, index: Int?) {
        val suffix = if (index == null) "" else "[$index]"
        track.album?.takeIf { it.isNotBlank() }?.let { params["album$suffix"] = it }
        track.albumArtist?.takeIf { it.isNotBlank() }?.let { params["albumArtist$suffix"] = it }
        track.trackNumber?.takeIf { it > 0 }?.let { params["trackNumber$suffix"] = it.toString() }
        track.durationSeconds?.takeIf { it > 0 }?.let { params["duration$suffix"] = it.toString() }
        track.mbid?.takeIf { it.isNotBlank() }?.let { params["mbid$suffix"] = it }
    }
}
