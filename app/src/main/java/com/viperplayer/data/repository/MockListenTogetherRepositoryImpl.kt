package com.viperplayer.data.repository

import com.viperplayer.data.social.JamCodeCodec
import com.viperplayer.domain.model.ListenSession
import com.viperplayer.domain.model.SessionParticipant
import com.viperplayer.domain.model.SessionPlayback
import com.viperplayer.domain.repository.ListenTogetherRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MOCK implementation of [ListenTogetherRepository], used when no backend URL is configured.
 *
 * There is no realtime jam backend reachable, so this keeps a single in-memory session: starting hosts
 * a fresh session locally, and joining accepts any well-formed code/URL and drops you in as a guest.
 * Everything a real service would own — presence, queue sync, participant list — is faked here.
 *
 * Membership-only: the sync engine ([playback], [serverNowUs], the `control*` actions) is inert — there
 * is no server to be authoritative, so playback stays null, the clock never syncs, and controls no-op
 * (all inherited from the interface's defaults / the stable inert flows below). The real backend-backed
 * implementation is [RealListenTogetherRepositoryImpl]; the DI module picks between them based on
 * whether `VIPER_BACKEND_URL` is configured.
 */
@Singleton
class MockListenTogetherRepositoryImpl @Inject constructor() : ListenTogetherRepository {

    private val _currentSession = MutableStateFlow<ListenSession?>(null)
    override val currentSession = _currentSession.asStateFlow()

    // Sync engine is inert: stable never-changing flows (not the interface's per-access defaults).
    override val playback: StateFlow<SessionPlayback?> = MutableStateFlow(null)
    override val synced: StateFlow<Boolean> = MutableStateFlow(false)
    override fun serverNowUs(): Long? = null

    override val codeLength: Int = JamCodeCodec.CODE_LENGTH

    override suspend fun startSession(): Result<ListenSession> {
        val code = JamCodeCodec.generateCode()
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

    override fun inviteUrlFor(code: String): String = JamCodeCodec.inviteUrlFor(code)

    override fun parseCode(input: String): String? = JamCodeCodec.parseCode(input)
}
