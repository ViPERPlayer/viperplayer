package com.viperplayer.domain.lastfm

/**
 * A scrobble that has been decided-eligible but not yet accepted by Last.fm - either because it
 * failed (offline/error) or is waiting to be batched. Pure data so the queue's
 * add/dedupe/drain logic ([PendingScrobbleQueue]) is unit-testable.
 *
 * @param track the track being scrobbled.
 * @param timestampSeconds UNIX time (seconds) the track STARTED playing. Last.fm keys a scrobble by
 *   (artist, track, timestamp), so this is also the dedupe key.
 */
data class PendingScrobble(
    val track: ScrobbleTrack,
    val timestampSeconds: Long,
) {
    /**
     * Stable identity for dedupe: same artist + title + start-timestamp is the same scrobble. Uses a
     * unit-separator delimiter (never present in track metadata) so e.g. "A|B"/"C" cannot collide with
     * "A"/"B|C" at the same timestamp.
     */
    val dedupeKey: String
        get() = track.artist + DELIMITER + track.track + DELIMITER + timestampSeconds

    private companion object {
        /** ASCII unit separator (0x1F): a control char that never appears in artist/track names. */
        const val DELIMITER = "\u001F"
    }
}
