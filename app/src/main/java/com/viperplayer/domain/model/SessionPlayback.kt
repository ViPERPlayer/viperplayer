package com.viperplayer.domain.model

/**
 * The server-authoritative shared playback state of a Listen-together (Jam) session, folded from the
 * backend `Timeline` wire message (internal/wire Timeline). One controller's transport command mutates
 * this on the server, which broadcasts the resulting state to ALL clients (including the sender); every
 * client renders its playhead from it. This is the load-bearing contract layer 2 (the player driver +
 * UI) extrapolates the local position from.
 *
 * All times are SERVER-monotonic microseconds. To render the current position, combine this with
 * [com.viperplayer.domain.repository.ListenTogetherRepository.serverNowUs] via [positionUsAt]:
 *
 *     pos = if (serverNow < effectiveAt || rate == 0) positionAnchorUs
 *           else positionAnchorUs + (serverNow - anchorServerTimeUs) * rate
 */
data class SessionPlayback(
    /** The current track, or null when nothing is loaded (blank plugin/source ref). */
    val track: SessionTrack?,
    /** Monotonically increases each time the shared playback state is superseded (internal/wire epoch). */
    val epoch: Long,
    /** Media position (µs) valid at [anchorServerTimeUs] — the backend's `p0Us`. */
    val positionAnchorUs: Long,
    /** Server time (µs) at which [positionAnchorUs] is valid — the backend's `t0Us`. */
    val anchorServerTimeUs: Long,
    /** 1.0 while playing, 0.0 while paused — the backend's `rate`. */
    val rate: Float,
    /** Server time (µs) at which this state becomes valid; before it, the position holds at the anchor. */
    val effectiveAtServerTimeUs: Long,
    /** The steering target's deviceId in Connect mode; empty in pure Jam. */
    val controllerId: String,
) {
    /**
     * Extrapolates the media position (µs) at [serverNowUs], matching the backend formula
     * (internal/session/timeline.go `TargetPositionUs`): before [effectiveAtServerTimeUs] or while
     * paused it holds at [positionAnchorUs]; otherwise it advances from the anchor at [rate].
     */
    fun positionUsAt(serverNowUs: Long): Long {
        if (serverNowUs < effectiveAtServerTimeUs || rate == 0f) return positionAnchorUs
        val elapsed = serverNowUs - anchorServerTimeUs
        return positionAnchorUs + (elapsed * rate).toLong()
    }
}

/**
 * A track referenced by the shared session playback (folded from internal/wire MediaRef). Carries the
 * portable identity ([mediaId]) plus display metadata so a member lacking that plugin can still render
 * a labelled (greyed) entry rather than stalling. The backend MediaRef has no album field, so [album]
 * is always empty from the wire — kept in the model so layer 2 can enrich it locally if desired.
 */
data class SessionTrack(
    val pluginId: String,
    val sourceId: String,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUrl: String,
    val durationMs: Long,
) {
    /** The portable [MediaId], or null when this ref is unlinked (blank source id). */
    val mediaId: MediaId? get() = MediaId.fromOrNull(pluginId, sourceId)
}
