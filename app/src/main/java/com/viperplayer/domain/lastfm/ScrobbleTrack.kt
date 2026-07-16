package com.viperplayer.domain.lastfm

/**
 * The minimal, source-agnostic description of a track for Last.fm's `track.scrobble` and
 * `track.updateNowPlaying` calls. Pure data — no Android types — so request building and the offline
 * queue can be exercised in JVM unit tests.
 *
 * @param artist artist name (required by Last.fm; a blank artist is not scrobbleable).
 * @param track track title (required).
 * @param album album name, or null/blank if unknown.
 * @param durationSeconds track length in whole seconds, or null if unknown. Sent as the `duration`
 *   parameter, which helps Last.fm apply its own duplicate/threshold heuristics.
 * @param albumArtist album artist, when it differs from [artist]; usually null.
 * @param trackNumber the track's position on its album, or null.
 * @param mbid MusicBrainz id, if available; usually null for streaming sources.
 */
data class ScrobbleTrack(
    val artist: String,
    val track: String,
    val album: String? = null,
    val durationSeconds: Long? = null,
    val albumArtist: String? = null,
    val trackNumber: Int? = null,
    val mbid: String? = null,
) {
    /** A track can only be sent to Last.fm when it has both an artist and a title. */
    val isValid: Boolean
        get() = artist.isNotBlank() && track.isNotBlank()
}
