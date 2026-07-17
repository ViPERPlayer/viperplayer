package com.viperplayer.data.repository

import android.os.Build
import com.viperplayer.data.social.DeviceIdProvider
import com.viperplayer.data.social.JamCodeCodec
import com.viperplayer.data.social.JamSocketClient
import com.viperplayer.data.social.JamSocketState
import com.viperplayer.data.social.MemberDto
import com.viperplayer.data.social.ROLE_HOST
import com.viperplayer.data.social.SessionApi
import com.viperplayer.data.social.SessionApiResult
import com.viperplayer.data.social.SessionSnapshotDto
import com.viperplayer.domain.account.AccountRepository
import com.viperplayer.domain.model.ListenSession
import com.viperplayer.domain.model.SessionParticipant
import com.viperplayer.domain.repository.ListenTogetherRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * REAL [ListenTogetherRepository] backed by the ViPER backend Jam session service
 * (github.com/iscle/viper-backend): REST create/join over [SessionApi] plus a live membership
 * WebSocket via [JamSocketClient].
 *
 * Lifecycle: [startSession]/[joinSession] hit the REST route, map the returned snapshot into a
 * [ListenSession] published immediately on [currentSession], then open the socket presenting the
 * returned session JWT. Incoming `session_snapshot`/`session_delta` frames keep the participant list
 * live. [leaveSession] cancels the socket collector — the server treats the closed socket as the
 * member leaving — and clears [currentSession].
 *
 * Scope is session lifecycle + membership only. Synchronized playback / driving the local player from
 * the session timeline is a separate future feature; the interface has no hook for it.
 * Follow-up: when that lands, consume the `timeline` frames (currently ignored) to steer the player.
 *
 * Not injected directly — [com.viperplayer.di.DataModule] chooses this via `@Provides` when a backend
 * URL is configured, otherwise the [MockListenTogetherRepositoryImpl].
 */
class RealListenTogetherRepositoryImpl(
    private val sessionApi: SessionApi,
    private val socketClient: JamSocketClient,
    private val deviceIdStore: DeviceIdProvider,
    private val accountRepository: AccountRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : ListenTogetherRepository {

    private val _currentSession = MutableStateFlow<ListenSession?>(null)
    override val currentSession = _currentSession.asStateFlow()

    override val codeLength: Int = JamCodeCodec.CODE_LENGTH

    /** The active socket collector, cancelled on leave/re-join. */
    private var socketJob: Job? = null

    /** This device's stable id, cached once resolved (used for isHost / isSelf comparisons). */
    private var cachedDeviceId: String? = null

    override suspend fun startSession(): Result<ListenSession> {
        val identity = resolveIdentity()
        return when (val result = sessionApi.createSession(identity.deviceId, identity.userId, identity.name)) {
            is SessionApiResult.Success -> {
                val body = result.value
                val session = body.snapshot.toListenSession(
                    localDeviceId = identity.deviceId,
                    code = body.code,
                    inviteUrl = body.inviteUrl.ifBlank { inviteUrlFor(body.code) },
                )
                _currentSession.value = session
                openSocket(jwt = body.jwt, code = body.code, inviteUrl = session.inviteUrl, localDeviceId = identity.deviceId)
                Result.success(session)
            }
            is SessionApiResult.Rejected -> Result.failure(IllegalStateException(result.message))
            SessionApiResult.NetworkError -> Result.failure(IllegalStateException("Couldn't reach the session server"))
            SessionApiResult.NotConfigured -> Result.failure(IllegalStateException("Listen together isn't available"))
        }
    }

    override suspend fun joinSession(codeOrUrl: String): Result<ListenSession> {
        val code = parseCode(codeOrUrl)
            ?: return Result.failure(IllegalArgumentException("Not a valid session code"))
        val identity = resolveIdentity()
        return when (val result = sessionApi.joinSession(code, identity.deviceId, identity.userId, identity.name)) {
            is SessionApiResult.Success -> {
                val body = result.value
                val inviteUrl = inviteUrlFor(code)
                val session = body.snapshot.toListenSession(
                    localDeviceId = identity.deviceId,
                    code = code,
                    inviteUrl = inviteUrl,
                )
                _currentSession.value = session
                openSocket(jwt = body.jwt, code = code, inviteUrl = inviteUrl, localDeviceId = identity.deviceId)
                Result.success(session)
            }
            is SessionApiResult.Rejected -> Result.failure(IllegalArgumentException(result.message))
            SessionApiResult.NetworkError -> Result.failure(IllegalStateException("Couldn't reach the session server"))
            SessionApiResult.NotConfigured -> Result.failure(IllegalStateException("Listen together isn't available"))
        }
    }

    override suspend fun leaveSession() {
        // Cancelling the collector cancels the webSocket block → closes the socket. The server treats
        // a closed socket as the member leaving; no explicit "leave" REST call exists.
        socketJob?.cancel()
        socketJob = null
        _currentSession.value = null
    }

    override fun inviteUrlFor(code: String): String = JamCodeCodec.inviteUrlFor(code)

    override fun parseCode(input: String): String? = JamCodeCodec.parseCode(input)

    /**
     * Opens (or replaces) the membership socket and keeps [currentSession] in step with the incoming
     * frames. [code]/[inviteUrl] are re-applied to every mapped snapshot (the WS snapshot doesn't carry
     * them). A disconnect clears the session — the current UI has no reconnect affordance.
     */
    private fun openSocket(jwt: String, code: String, inviteUrl: String, localDeviceId: String) {
        socketJob?.cancel()
        val base = sessionApi.baseUrl
        if (base == null) {
            Timber.w("Jam socket not opened: backend URL missing")
            return
        }
        socketJob = scope.launch {
            socketClient.connect(base, jwt).collect { state ->
                when (state) {
                    is JamSocketState.State -> {
                        _currentSession.value = state.snapshot.toListenSession(localDeviceId, code, inviteUrl)
                    }
                    is JamSocketState.Disconnected -> {
                        Timber.i("Jam session ended: ${state.cause ?: "socket closed"}")
                        _currentSession.value = null
                    }
                }
            }
        }
    }

    /** Resolves the identity to present to the backend: stable device id + account user/name (or guest). */
    private suspend fun resolveIdentity(): Identity {
        val deviceId = cachedDeviceId ?: deviceIdStore.deviceId().also { cachedDeviceId = it }
        val account = accountRepository.state.first()
        val user = account.user
        val name = user?.displayName?.takeIf { it.isNotBlank() } ?: defaultGuestName()
        return Identity(deviceId = deviceId, userId = user?.id.orEmpty(), name = name)
    }

    private fun defaultGuestName(): String =
        Build.MODEL?.takeIf { it.isNotBlank() } ?: "Guest"

    private data class Identity(val deviceId: String, val userId: String, val name: String)
}

/**
 * Maps an authoritative [SessionSnapshotDto] into the UI-facing [ListenSession]. The share [code] and
 * [inviteUrl] come from the REST response (the WS snapshot doesn't repeat them); [localDeviceId]
 * decides `isHost` and which participant is `isSelf`.
 */
internal fun SessionSnapshotDto.toListenSession(
    localDeviceId: String,
    code: String,
    inviteUrl: String,
): ListenSession {
    val isHost = host.deviceId == localDeviceId
    return ListenSession(
        code = code,
        inviteUrl = inviteUrl,
        hostName = host.displayName(),
        isHost = isHost,
        participants = members.map { it.toParticipant(localDeviceId) },
    )
}

private fun MemberDto.toParticipant(localDeviceId: String): SessionParticipant = SessionParticipant(
    // deviceId is the stable per-member key; fall back to userId if a member somehow lacks one.
    id = deviceId.ifBlank { userId },
    name = displayName(),
    isSelf = deviceId == localDeviceId,
)

/** A member's shown name, defaulting a blank one so the UI never renders an empty label. */
private fun MemberDto.displayName(): String =
    name.takeIf { it.isNotBlank() } ?: if (role == ROLE_HOST) "Host" else "Guest"
