package com.viperplayer.data.repository

import android.os.Build
import com.viperplayer.data.social.CMD_NEXT
import com.viperplayer.data.social.CMD_PAUSE
import com.viperplayer.data.social.CMD_PLAY
import com.viperplayer.data.social.CMD_PREV
import com.viperplayer.data.social.CMD_SEEK
import com.viperplayer.data.social.CMD_TRACK
import com.viperplayer.data.social.CommandDto
import com.viperplayer.data.social.DeviceIdProvider
import com.viperplayer.data.social.JamCodeCodec
import com.viperplayer.data.social.JamClientFrame
import com.viperplayer.data.social.JamConnection
import com.viperplayer.data.social.JamServerEvent
import com.viperplayer.data.social.JamSocketClient
import com.viperplayer.data.social.MediaRefDto
import com.viperplayer.data.social.MemberDto
import com.viperplayer.data.social.ROLE_CONTROLLER
import com.viperplayer.data.social.ROLE_HOST
import com.viperplayer.data.social.ROLE_MEMBER
import com.viperplayer.data.social.SeekPayloadDto
import com.viperplayer.data.social.SessionApi
import com.viperplayer.data.social.SessionApiResult
import com.viperplayer.data.social.SessionClock
import com.viperplayer.data.social.SessionSnapshotDto
import com.viperplayer.data.social.TimelineDto
import com.viperplayer.data.social.TrackPayloadDto
import com.viperplayer.domain.account.AccountRepository
import com.viperplayer.domain.model.ListenSession
import com.viperplayer.domain.model.SessionParticipant
import com.viperplayer.domain.model.SessionPlayback
import com.viperplayer.domain.model.SessionTrack
import com.viperplayer.domain.repository.ListenTogetherRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import timber.log.Timber

/**
 * REAL [ListenTogetherRepository] backed by the ViPER backend Jam session service
 * (github.com/iscle/viper-backend): REST create/join over [SessionApi] plus a live, BIDIRECTIONAL
 * WebSocket via [JamSocketClient].
 *
 * ## Lifecycle
 * [startSession]/[joinSession] hit the REST route, map the returned snapshot into a [ListenSession]
 * published immediately on [currentSession], then open the [JamConnection] presenting the returned
 * session JWT. On that connection:
 * - membership events keep [currentSession] (and its [ListenSession.canControl]) live;
 * - `playback` events fold the server [TimelineDto] into [SessionPlayback] on [playback];
 * - a [SessionClock] runs its four-timestamp sync so [serverNowUs] maps local→server monotonic time.
 *
 * The transport `control*` actions send a `command` frame on the same connection; the server authorizes,
 * applies, and broadcasts the resulting `playback` delta back (which this client renders like any other).
 *
 * [leaveSession] closes the connection (the server treats a closed socket as the member leaving), stops
 * the clock, and clears [currentSession] + [playback].
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
    // Test-only: caps the clock's ping loop so a virtual-time test's advanceUntilIdle doesn't spin on
    // the otherwise-infinite sync loop. null (prod) runs the clock until the connection is torn down.
    private val clockMaxPings: Int? = null,
) : ListenTogetherRepository {

    private val _currentSession = MutableStateFlow<ListenSession?>(null)
    override val currentSession = _currentSession.asStateFlow()

    private val _playback = MutableStateFlow<SessionPlayback?>(null)
    override val playback: StateFlow<SessionPlayback?> = _playback.asStateFlow()

    private val _synced = MutableStateFlow(false)
    override val synced: StateFlow<Boolean> = _synced.asStateFlow()

    override val codeLength: Int = JamCodeCodec.CODE_LENGTH

    /** The active connection driver (membership + playback + clock), cancelled on leave/re-join. */
    private var connectionJob: Job? = null

    /** The live connection; the control actions send commands on it. Null when not in a session. */
    @Volatile
    private var connection: JamConnection? = null

    /** The running clock for the live connection. */
    @Volatile
    private var clock: SessionClock? = null

    /** Client-chosen command correlation id (forSeq), monotonically increasing. */
    private val commandSeq = AtomicLong(0)

    /** This device's stable id, cached once resolved (used for isHost / isSelf comparisons). */
    private var cachedDeviceId: String? = null

    override fun serverNowUs(): Long? = clock?.serverNowUs()

    override suspend fun startSession(): Result<ListenSession> {
        val identity = resolveIdentity()
        return when (val result = sessionApi.createSession(identity.deviceId, identity.userId, identity.name)) {
            is SessionApiResult.Success -> {
                val body = result.value
                val inviteUrl = body.inviteUrl.ifBlank { inviteUrlFor(body.code) }
                val session = body.snapshot.toListenSession(identity.deviceId, body.code, inviteUrl)
                _currentSession.value = session
                openConnection(body.jwt, body.code, inviteUrl, identity.deviceId)
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
                val session = body.snapshot.toListenSession(identity.deviceId, code, inviteUrl)
                _currentSession.value = session
                openConnection(body.jwt, code, inviteUrl, identity.deviceId)
                Result.success(session)
            }
            is SessionApiResult.Rejected -> Result.failure(IllegalArgumentException(result.message))
            SessionApiResult.NetworkError -> Result.failure(IllegalStateException("Couldn't reach the session server"))
            SessionApiResult.NotConfigured -> Result.failure(IllegalStateException("Listen together isn't available"))
        }
    }

    override suspend fun leaveSession() {
        teardown()
        _currentSession.value = null
    }

    override fun inviteUrlFor(code: String): String = JamCodeCodec.inviteUrlFor(code)

    override fun parseCode(input: String): String? = JamCodeCodec.parseCode(input)

    // --- Transport controls ---

    override suspend fun controlPlay() = sendCommand(CommandDto(kind = CMD_PLAY))

    override suspend fun controlPause() = sendCommand(CommandDto(kind = CMD_PAUSE))

    override suspend fun controlSeek(positionUs: Long) =
        sendCommand(CommandDto(kind = CMD_SEEK, seek = SeekPayloadDto(positionUs = positionUs)))

    override suspend fun controlSkipNext() = sendCommand(CommandDto(kind = CMD_NEXT))

    override suspend fun controlSkipPrevious() = sendCommand(CommandDto(kind = CMD_PREV))

    override suspend fun controlSetTrack(track: SessionTrack, positionUs: Long) {
        // NOTE: the wire Command carries only the track ref; the backend schedules P0=0 for a track
        // change (see internal/session/timeline.go ScheduleTrack). A non-zero [positionUs] is followed
        // by a seek so layer 2 can seed a mid-track position; play state is driven separately.
        sendCommand(CommandDto(kind = CMD_TRACK, track = TrackPayloadDto(mediaRef = track.toMediaRefDto())))
        if (positionUs > 0) controlSeek(positionUs)
    }

    /**
     * Sends a [CommandDto] on the live connection, stamping a fresh `forSeq`. A no-op when there is no
     * session or the local member can't control (UX gate — the server re-checks authoritatively). Acks
     * arrive on the connection's event stream and are logged by the driver.
     */
    private suspend fun sendCommand(command: CommandDto) {
        val conn = connection ?: return
        if (_currentSession.value?.canControl != true) {
            Timber.d("Ignoring transport command '${command.kind}' — local member can't control")
            return
        }
        conn.send(JamClientFrame.Command(command.copy(forSeq = commandSeq.incrementAndGet())))
    }

    /**
     * Opens (or replaces) the connection and drives it: folds membership → [currentSession], playback →
     * [playback], acks → logs, and disconnect → clear. Runs a [SessionClock] off the same connection.
     * [code]/[inviteUrl] are re-applied to every mapped snapshot (the WS snapshot doesn't carry them).
     */
    private fun openConnection(jwt: String, code: String, inviteUrl: String, localDeviceId: String) {
        teardown()
        val base = sessionApi.baseUrl
        if (base == null) {
            Timber.w("Jam connection not opened: backend URL missing")
            return
        }
        val conn = socketClient.connect(base, jwt)
        connection = conn

        val sessionClock = SessionClock(
            send = { req -> conn.send(JamClientFrame.TimeReq(req)) },
            responses = conn.incoming.filterIsInstance<JamServerEvent.TimeResp>().map { it.resp },
            maxPings = clockMaxPings,
        )
        clock = sessionClock

        connectionJob = scope.launch {
            // Mirror the clock's synced flag onto the repository's.
            launch { sessionClock.synced.collect { _synced.value = it } }
            sessionClock.start(this)

            conn.incoming.collect { event ->
                when (event) {
                    is JamServerEvent.Membership ->
                        _currentSession.value = event.snapshot.toListenSession(localDeviceId, code, inviteUrl)
                    is JamServerEvent.Playback ->
                        _playback.value = event.timeline.toSessionPlayback()
                    is JamServerEvent.CommandAck ->
                        if (!event.ack.ok) Timber.w("Command ${event.ack.forSeq} rejected: ${event.ack.error}")
                    is JamServerEvent.TimeResp -> Unit // consumed by the clock via the filtered flow.
                    is JamServerEvent.Disconnected -> {
                        Timber.i("Jam session ended: ${event.cause ?: "socket closed"}")
                        _currentSession.value = null
                        // Route through the same cleanup as leaving: a server-initiated drop must also
                        // stop the clock and clear `synced`, or serverNowUs() would keep returning stale
                        // values from a dead clock (which the player-follower layer would trust).
                        teardown()
                    }
                }
            }
        }
    }

    /** Cancels the connection driver, stops the clock, closes the socket, and clears playback + sync. */
    private fun teardown() {
        clock?.stop()
        clock = null
        connectionJob?.cancel()
        connectionJob = null
        connection?.close()
        connection = null
        _playback.value = null
        _synced.value = false
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
 * decides `isHost`, which participant is `isSelf`, and [ListenSession.canControl] (from the local
 * member's role + the session permissions).
 */
internal fun SessionSnapshotDto.toListenSession(
    localDeviceId: String,
    code: String,
    inviteUrl: String,
): ListenSession {
    val isHost = host.deviceId == localDeviceId
    val localMember = members.firstOrNull { it.deviceId == localDeviceId }
    return ListenSession(
        code = code,
        inviteUrl = inviteUrl,
        hostName = host.displayName(),
        isHost = isHost,
        participants = members.map { it.toParticipant(localDeviceId) },
        canControl = canControl(isHost, localMember, permissions.guestsCanControl),
    )
}

/**
 * Whether the local member may control transport (mirrors the backend's `canControlTransport`):
 * HOST/CONTROLLER always; MEMBER iff [guestsCanControl]; LISTENER never. The host flag short-circuits
 * true (covers a snapshot where the local member row hasn't arrived yet).
 */
private fun canControl(isHost: Boolean, localMember: MemberDto?, guestsCanControl: Boolean): Boolean {
    if (isHost) return true
    return when (localMember?.role) {
        ROLE_HOST, ROLE_CONTROLLER -> true
        ROLE_MEMBER -> guestsCanControl
        else -> false
    }
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

/**
 * Folds a server [TimelineDto] into the domain [SessionPlayback]. A blank track ref (no plugin/source)
 * means nothing is loaded, so [SessionPlayback.track] is null.
 */
internal fun TimelineDto.toSessionPlayback(): SessionPlayback = SessionPlayback(
    track = track.toSessionTrackOrNull(),
    epoch = epoch,
    positionAnchorUs = p0Us,
    anchorServerTimeUs = t0Us,
    rate = rate,
    effectiveAtServerTimeUs = effectiveAtUs,
    controllerId = controllerId,
)

private fun MediaRefDto.toSessionTrackOrNull(): SessionTrack? {
    if (pluginId.isBlank() && sourceId.isBlank()) return null
    return SessionTrack(
        pluginId = pluginId,
        sourceId = sourceId,
        title = title,
        artist = artist,
        album = "", // backend MediaRef has no album field; layer 2 may enrich locally.
        artworkUrl = artworkUrl,
        durationMs = durationMs,
    )
}

/** Builds the outbound [MediaRefDto] for a track-change command from a domain [SessionTrack]. */
internal fun SessionTrack.toMediaRefDto(): MediaRefDto = MediaRefDto(
    pluginId = pluginId,
    sourceId = sourceId,
    title = title,
    artist = artist,
    artworkUrl = artworkUrl,
    durationMs = durationMs,
)
