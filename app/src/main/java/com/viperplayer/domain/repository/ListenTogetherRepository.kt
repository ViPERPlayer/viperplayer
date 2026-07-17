package com.viperplayer.domain.repository

import com.viperplayer.domain.model.ListenSession
import com.viperplayer.domain.model.SessionPlayback
import com.viperplayer.domain.model.SessionTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * "Listen together" / Jam sessions.
 *
 * Two implementations back this: [com.viperplayer.data.repository.RealListenTogetherRepositoryImpl]
 * (the ViPER backend session service — REST create/join + a bidirectional WebSocket carrying membership,
 * the shared playback timeline, clock sync and transport commands) when a backend URL is configured,
 * otherwise the in-memory [com.viperplayer.data.repository.MockListenTogetherRepositoryImpl].
 * `DataModule` picks between them.
 *
 * ## Layers
 * - **Membership** ([currentSession]): who's in the session, the share code, and whether the local
 *   member [ListenSession.canControl]s transport. Owned by both impls.
 * - **Sync engine** ([playback] + [serverNowUs]/[synced] + the `control*` actions): the server-
 *   authoritative shared playback state, the client→server monotonic clock offset, and the transport
 *   commands that mutate the shared state. Owned only by the REAL impl; the mock leaves [playback] null
 *   and the controls as no-ops.
 *
 * A LATER layer (the player driver + UI) builds on the sync engine: it renders the local playhead by
 * extrapolating [SessionPlayback.positionUsAt] with [serverNowUs], and — when [ListenSession.canControl]
 * — forwards local transport events through the `control*` actions instead of driving the player
 * directly. Everything on this interface below [codeLength] is that load-bearing contract.
 */
interface ListenTogetherRepository {
    /** The active session, or null when not in one. */
    val currentSession: StateFlow<ListenSession?>

    /** Start hosting a new session. */
    suspend fun startSession(): Result<ListenSession>

    /** Join an existing session by its share code (e.g. "8KX2-9QT") or a pasted invite URL. */
    suspend fun joinSession(codeOrUrl: String): Result<ListenSession>

    /** Leave / end the current session. */
    suspend fun leaveSession()

    /** Build the shareable invite URL for a code. */
    fun inviteUrlFor(code: String): String

    /** Extract a normalised session code from a pasted invite URL or raw code, or null if unrecognised. */
    fun parseCode(input: String): String?

    /** Length of a manual-entry code (number of character cells shown in the UI). */
    val codeLength: Int

    // --- Sync engine (default: inert; the REAL impl overrides these) ---

    /**
     * The server-authoritative shared playback state, or null when not in a session / nothing loaded.
     * Updated from every `playback` delta the server broadcasts. Layer 2 extrapolates the current
     * position from this + [serverNowUs] via [SessionPlayback.positionUsAt].
     *
     * Defaults to a permanently-null flow so the mock impl needs no playback machinery.
     */
    val playback: StateFlow<SessionPlayback?> get() = MutableStateFlow(null)

    /**
     * Server-monotonic microseconds now, or null until the clock has synced (or when not in a session).
     * The anchor times in [playback] are in this timebase, so layer 2 needs this to place the playhead.
     * Defaults to null (unsynced) for the mock.
     */
    fun serverNowUs(): Long? = null

    /** True once the session clock has synced at least once. Defaults to a permanently-false flow. */
    val synced: StateFlow<Boolean> get() = MutableStateFlow(false)

    // --- Transport control actions ---
    //
    // Each sends a backend Command; the server authorizes (by the connection's authenticated deviceId),
    // applies it, and broadcasts the resulting `playback` delta to everyone (incl. this client). These
    // are UX-gated by [ListenSession.canControl] but the server is the real authority. All are no-ops
    // when not in a session or not permitted. Default implementations do nothing (the mock).

    /** Resume shared playback from the current paused position. */
    suspend fun controlPlay() = Unit

    /** Pause shared playback. */
    suspend fun controlPause() = Unit

    /** Seek the shared playback to [positionUs] (server-timebase-agnostic media position, microseconds). */
    suspend fun controlSeek(positionUs: Long) = Unit

    /** Skip to the next queue entry. */
    suspend fun controlSkipNext() = Unit

    /** Skip to the previous queue entry. */
    suspend fun controlSkipPrevious() = Unit

    /**
     * Set the shared current track to [track], starting at [positionUs] (default 0). Used by layer 2 for
     * host seeding (publishing the locally-playing track into a fresh session) and for track changes.
     */
    suspend fun controlSetTrack(track: SessionTrack, positionUs: Long = 0) = Unit
}
