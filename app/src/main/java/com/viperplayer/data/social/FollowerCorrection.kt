package com.viperplayer.data.social

import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.SessionPlayback

/**
 * The pure, Android-free decision core of the Listen-together follower loop (layer 2, part A).
 *
 * Given the shared [SessionPlayback] timeline, the current server time, and the follower's local
 * player state, [decide] computes exactly what the follower should do this tick: whether to (re)load
 * a track, whether to hard-seek, what playback speed to run at, and whether to be playing. The
 * follower loop ([com.viperplayer.data.social.SessionPlaybackFollower]) is a thin shell that reads
 * inputs off the media controller, calls [decide], and applies the [Decision] — so all the timing /
 * drift / pre-roll / gating logic lives here where it can be unit-tested without a device.
 *
 * ## Model
 * - **Not synced yet** ([serverNowUs] null): [Decision.syncing] = true; nothing else is driven.
 * - **No track**: idle — do not touch the local player (the user may still be playing their own audio).
 * - **Track differs from what's loaded** (compared by [SessionPlayback.epoch] + mediaId): request a
 *   load via [Decision.loadTrack]. The caller guards actual loads to once-per-epoch.
 * - **Pre-roll** (server time before [SessionPlayback.effectiveAtServerTimeUs]): hold paused at the
 *   anchor — seek to the anchor and don't play.
 * - **Playing/paused**: match [SessionPlayback.rate] (>0 → play, 0 → pause). While playing and past
 *   the effective time, correct drift: a hard seek when |drift| exceeds [HARD_SEEK_THRESHOLD_US] (or
 *   the player isn't ready), otherwise a gentle Sonic time-stretch clamped to [MIN_SPEED]..[MAX_SPEED]
 *   (pitch-preserving), with a deadband so tiny drifts don't wobble the speed.
 */
object FollowerCorrection {

    /** Hard-seek threshold: a drift larger than 1.5s is corrected by seeking, not time-stretching. */
    const val HARD_SEEK_THRESHOLD_US = 1_500_000L

    /** Below this absolute drift, run at exactly 1.0× — a deadband so the speed doesn't hunt (40ms). */
    const val DEADBAND_US = 40_000L

    /** Proportional gain mapping drift-seconds → speed offset before clamping. */
    const val CORRECTION_GAIN = 0.5

    /** Clamp bounds for the gentle time-stretch correction (±3%). */
    const val MIN_SPEED = 0.97f
    const val MAX_SPEED = 1.03f

    /**
     * Identity of the track currently loaded in the follower's player, so [decide] can tell whether a
     * fresh load is needed. Keyed by the session [epoch] AND the [mediaId]: a track change bumps the
     * epoch, and re-seeding the same track in a NEW session epoch should still reload. Compared by the
     * [MediaId] value (structural equality) — not its string form — so this stays testable off-device.
     */
    data class LoadedTrack(val epoch: Long, val mediaId: MediaId)

    /** A request to load a remote track into the follower's player (metadata for the now-playing UI). */
    data class LoadTrack(
        val epoch: Long,
        val mediaId: MediaId,
        val title: String,
        val artist: String,
        val artworkUrl: String,
    )

    /**
     * What the follower should do this tick. The loop applies these in order: load (if any), seek (if
     * any), set speed, then play/pause. [syncing] surfaces the "still syncing" UI state.
     */
    data class Decision(
        /** Non-null when the follower must load a (new) track before it can drive position. */
        val loadTrack: LoadTrack?,
        /** Non-null when the follower must hard-seek to this position (milliseconds). */
        val seekMs: Long?,
        /** The playback speed to run at (1.0 unless a gentle drift correction is active). */
        val speed: Float,
        /** Whether the follower should be playing (true) or paused (false) right now. */
        val play: Boolean,
        /** True while the session clock hasn't synced — the UI shows "Syncing…" and position isn't driven. */
        val syncing: Boolean,
        /** True when there is a session + track but nothing to do to the player this tick (idle/no-track). */
        val idle: Boolean,
    ) {
        companion object {
            /** The clock hasn't synced yet: surface "syncing", drive nothing. */
            val Syncing = Decision(loadTrack = null, seekMs = null, speed = 1f, play = false, syncing = true, idle = false)

            /** In a session but nothing is loaded: leave the local player alone. */
            val Idle = Decision(loadTrack = null, seekMs = null, speed = 1f, play = false, syncing = false, idle = true)
        }
    }

    /**
     * Compute the follower's action for this tick.
     *
     * @param pb the shared timeline, or null when not in a session / nothing broadcast yet.
     * @param serverNowUs server-monotonic microseconds, or null until the clock has synced.
     * @param localPosMs the follower player's current position in ms (only meaningful when [ready]).
     * @param loaded what track is currently loaded in the follower's player, or null when none.
     * @param ready whether the follower's player is prepared enough to trust [localPosMs] / play.
     */
    fun decide(
        pb: SessionPlayback?,
        serverNowUs: Long?,
        localPosMs: Long,
        loaded: LoadedTrack?,
        ready: Boolean,
    ): Decision {
        // Not synced (or no timeline yet): can't place the playhead — surface syncing, drive nothing.
        if (serverNowUs == null || pb == null) return Decision.Syncing

        val track = pb.track ?: return Decision.Idle
        val mediaId = track.mediaId ?: return Decision.Idle

        // Track load: reload when the (epoch, mediaId) differs from what's loaded. The caller guards so
        // a given epoch is attempted at most once (no reload-storm on resolve failure).
        val needsLoad = loaded == null || loaded.epoch != pb.epoch || loaded.mediaId != mediaId
        val loadTrack = if (needsLoad) {
            LoadTrack(
                epoch = pb.epoch,
                mediaId = mediaId,
                title = track.title,
                artist = track.artist,
                artworkUrl = track.artworkUrl,
            )
        } else null

        val target = pb.positionUsAt(serverNowUs)

        // Pre-roll: before the delta becomes effective, hold paused at the anchor so every device
        // converges to the same spot, then all resume together at effectiveAt.
        if (serverNowUs < pb.effectiveAtServerTimeUs) {
            return Decision(
                loadTrack = loadTrack,
                seekMs = pb.positionAnchorUs / 1000,
                speed = 1f,
                play = false,
                syncing = false,
                idle = false,
            )
        }

        val shouldPlay = pb.rate > 0f

        // Paused (rate 0): just hold paused at the target; no drift correction while not advancing.
        if (!shouldPlay) {
            return Decision(
                loadTrack = loadTrack,
                seekMs = target / 1000,
                speed = 1f,
                play = false,
                syncing = false,
                idle = false,
            )
        }

        // Playing. If we just requested a load, or the player isn't ready, hard-seek to the target and
        // reset speed — we can't trust localPosMs yet.
        if (loadTrack != null || !ready) {
            return Decision(
                loadTrack = loadTrack,
                seekMs = target / 1000,
                speed = 1f,
                play = true,
                syncing = false,
                idle = false,
            )
        }

        val driftUs = target - localPosMs * 1000

        // Large drift: hard seek and reset speed.
        if (kotlin.math.abs(driftUs) > HARD_SEEK_THRESHOLD_US) {
            return Decision(
                loadTrack = null,
                seekMs = target / 1000,
                speed = 1f,
                play = true,
                syncing = false,
                idle = false,
            )
        }

        // Small drift: gentle pitch-preserving time-stretch, with a deadband around zero.
        val speed = if (kotlin.math.abs(driftUs) < DEADBAND_US) {
            1f
        } else {
            val raw = 1.0 + (driftUs / 1_000_000.0) * CORRECTION_GAIN
            raw.coerceIn(MIN_SPEED.toDouble(), MAX_SPEED.toDouble()).toFloat()
        }

        return Decision(
            loadTrack = null,
            seekMs = null,
            speed = speed,
            play = true,
            syncing = false,
            idle = false,
        )
    }
}
