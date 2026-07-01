package com.viperplayer.data.repository

import com.viperplayer.domain.model.ListenSession
import com.viperplayer.domain.model.SessionParticipant
import com.viperplayer.domain.repository.ListenTogetherRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MOCK implementation of [ListenTogetherRepository].
 *
 * There is no realtime jam backend yet, so this keeps a single in-memory session: starting hosts a
 * fresh session locally, and joining accepts any well-formed code/URL and drops you in as a guest.
 * Everything a real service would own — presence, queue sync, participant list — is faked here.
 *
 * To make it real, replace the bodies below with calls to the backend (create/join/leave + a presence
 * stream feeding [_currentSession]); no caller changes required.
 */
@Singleton
class ListenTogetherRepositoryImpl @Inject constructor() : ListenTogetherRepository {

    private val _currentSession = MutableStateFlow<ListenSession?>(null)
    override val currentSession = _currentSession.asStateFlow()

    override val codeLength: Int = CODE_LENGTH

    override suspend fun startSession(): Result<ListenSession> {
        val code = generateCode()
        val session = ListenSession(
            code = code,
            inviteUrl = inviteUrlFor(code),
            hostName = "You",
            isHost = true,
            participants = listOf(SessionParticipant(id = "self", name = "You", isSelf = true)),
        )
        _currentSession.value = session
        return Result.success(session)
    }

    override suspend fun joinSession(codeOrUrl: String): Result<ListenSession> {
        val code = parseCode(codeOrUrl)
            ?: return Result.failure(IllegalArgumentException("Not a valid session code"))
        // MOCK: no backend to validate against — accept any well-formed code and join as a guest.
        val session = ListenSession(
            code = code,
            inviteUrl = inviteUrlFor(code),
            hostName = "Host",
            isHost = false,
            participants = listOf(
                SessionParticipant(id = "host", name = "Host"),
                SessionParticipant(id = "self", name = "You", isSelf = true),
            ),
        )
        _currentSession.value = session
        return Result.success(session)
    }

    override suspend fun leaveSession() {
        _currentSession.value = null
    }

    override fun inviteUrlFor(code: String): String = "$INVITE_BASE/${code.lowercase()}"

    override fun parseCode(input: String): String? {
        // Accept either a raw code ("8KX29QT" / "8KX2-9QT") or an invite URL (…/jam/8kx29qt).
        val tail = input.substringAfterLast('/').substringBefore('?').trim()
        val cleaned = tail.uppercase().filter { it in CODE_ALPHABET }
        return if (cleaned.length >= CODE_LENGTH) cleaned.take(CODE_LENGTH) else null
    }

    private fun generateCode(): String {
        fun block(n: Int) = (1..n).map { CODE_ALPHABET.random() }.joinToString("")
        return "${block(4)}-${block(3)}"
    }

    companion object {
        // Unambiguous alphabet (no O/0, I/1) — matches the codes shared by the QR/invite sheets.
        private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private const val CODE_LENGTH = 6
        private const val INVITE_BASE = "https://viper.player/jam"
    }
}
