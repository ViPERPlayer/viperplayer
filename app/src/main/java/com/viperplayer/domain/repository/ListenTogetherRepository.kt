package com.viperplayer.domain.repository

import com.viperplayer.domain.model.ListenSession
import kotlinx.coroutines.flow.StateFlow

/**
 * "Listen together" / Jam sessions.
 *
 * Currently MOCK-backed (no realtime service) — see [com.viperplayer.data.repository.ListenTogetherRepositoryImpl].
 * This interface is the single seam a real backend plugs into: the UI (player social sheets, the
 * Join-a-session screen) only ever talks to these methods and observes [currentSession].
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
}
