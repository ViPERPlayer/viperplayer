package com.viperplayer.data.social

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A server→client event surfaced by a live [JamConnection]. Covers the frame types the shared-playback
 * engine consumes: authoritative membership state (with membership deltas already folded), the shared
 * playback [TimelineDto] (from the snapshot + `playback` deltas), clock-sync responses, command acks,
 * and a terminal [Disconnected].
 */
sealed interface JamServerEvent {
    /** The latest authoritative session state (snapshot + applied membership deltas). */
    data class Membership(val snapshot: SessionSnapshotDto) : JamServerEvent

    /** A new authoritative shared playback timeline (from a snapshot or a `playback` delta). */
    data class Playback(val timeline: TimelineDto) : JamServerEvent

    /** A clock-sync reply. Consumed by [SessionClock]. */
    data class TimeResp(val resp: TimeRespDto) : JamServerEvent

    /** Acknowledgement of a command this client sent (delivered to the sender only). */
    data class CommandAck(val ack: CommandAckDto) : JamServerEvent

    /** The socket closed or failed. Terminal — no further events follow on this connection. */
    data class Disconnected(val cause: String? = null) : JamServerEvent
}

/**
 * A client→server frame a [JamConnection] can send: a clock ping or a transport command. Kept as a
 * small sealed type (rather than raw [FrameDto]s) so callers can't accidentally send a malformed
 * envelope; the connection wraps each in the correct [FrameDto] with the right `type`.
 */
sealed interface JamClientFrame {
    data class TimeReq(val req: TimeReqDto) : JamClientFrame
    data class Command(val command: CommandDto) : JamClientFrame
}

/**
 * A single live, BIDIRECTIONAL Jam WebSocket connection. Unlike the old receive-only cold flow, the
 * caller can both observe [incoming] server events AND [send] client frames (clock pings, transport
 * commands) on the same socket. [close] tears it down (the server treats a closed socket as the member
 * leaving).
 *
 * [incoming] is a hot [SharedFlow]: it starts flowing once the connection is opened, replays the most
 * recent event to a late collector (so a subscriber that attaches just after a membership snapshot
 * still sees current state), and emits a terminal [JamServerEvent.Disconnected] when the socket ends.
 */
interface JamConnection {
    val incoming: SharedFlow<JamServerEvent>
    suspend fun send(frame: JamClientFrame)
    fun close()
}

/**
 * Opens Jam WebSocket connections.
 *
 * Implementations connect to `<wsBaseUrl>/ws` presenting the session `jwt` as an
 * `Authorization: Bearer` header (NEVER a query param — it leaks into logs), then drive the socket:
 * decoding `session_snapshot`/`session_delta`/`playback`/`time_resp`/`command_ack` inbound frames and
 * draining an outbound queue of [JamClientFrame]s. The [SessionStateApplier] keeps the membership
 * snapshot current across deltas.
 */
interface JamSocketClient {
    /**
     * Opens a connection and returns a live [JamConnection]. The socket is driven on an internal scope;
     * [JamConnection.close] (see the repository's leaveSession) tears it down. A drop surfaces as a
     * terminal [JamServerEvent.Disconnected] on [JamConnection.incoming].
     */
    fun connect(httpBaseUrl: String, jwt: String): JamConnection
}

/**
 * Ktor-backed [JamSocketClient] over the shared [HttpClient] (which has `install(WebSockets)`).
 *
 * There is no built-in auto-reconnect — a drop surfaces as a terminal [JamServerEvent.Disconnected]
 * and the caller decides what to do (the current UI just ends the session).
 */
@Singleton
class KtorJamSocketClient @Inject constructor(
    private val httpClient: HttpClient,
) : JamSocketClient {

    private val json = Json {
        ignoreUnknownKeys = true
        // Command payloads default fields (e.g. TrackPayloadDto.uid) must serialise so the backend reads them.
        encodeDefaults = true
    }

    override fun connect(httpBaseUrl: String, jwt: String): JamConnection {
        val wsUrl = toWebSocketUrl(httpBaseUrl)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        return KtorJamConnection(httpClient, json, wsUrl, jwt, scope)
    }
}

/**
 * The live Ktor connection. Runs the WS session on [scope]: an inbound loop drains `incoming` frames
 * into [_incoming], while [send] enqueues client frames onto [outbound] which the session's writer
 * loop drains onto the wire. Closing cancels [scope], which ends the WS block and the loops.
 */
private class KtorJamConnection(
    private val httpClient: HttpClient,
    private val json: Json,
    private val wsUrl: String,
    private val jwt: String,
    private val scope: CoroutineScope,
) : JamConnection {

    // replay=1 so a collector attaching just after the first snapshot still sees current state.
    private val _incoming = MutableSharedFlow<JamServerEvent>(replay = 1, extraBufferCapacity = 64)
    override val incoming: SharedFlow<JamServerEvent> = _incoming.asSharedFlow()

    /** Outbound client frames, drained by the writer loop onto the socket. Unlimited so send never blocks. */
    private val outbound = Channel<JamClientFrame>(Channel.UNLIMITED)

    init {
        // Drive the socket on the connection's own scope; close() cancels it.
        scope.launch { run() }
    }

    override suspend fun send(frame: JamClientFrame) {
        // trySend never suspends; the channel is UNLIMITED so it only fails once closed (post-teardown).
        outbound.trySend(frame)
    }

    override fun close() {
        outbound.close()
        scope.cancel()
    }

    private suspend fun run() {
        val applier = SessionStateApplier()
        try {
            httpClient.webSocket(
                urlString = wsUrl,
                request = { header(HttpHeaders.Authorization, "Bearer $jwt") },
            ) {
                // Writer loop: drain outbound client frames onto the wire. It must live inside the WS
                // block for access to this session's `send`; it ends when `outbound` closes (on close())
                // or the block is cancelled.
                val writerJob = launch {
                    for (frame in outbound) {
                        send(json.encodeToString(frame.toFrameDto()))
                    }
                }
                try {
                    for (frame in incoming) {
                        if (frame !is Frame.Text) continue
                        val text = frame.readText()
                        val decoded = runCatching { json.decodeFromString<FrameDto>(text) }.getOrNull()
                            ?: continue
                        emitEvents(applier, decoded)
                    }
                } finally {
                    writerJob.cancel()
                }
            }
            // incoming closed normally (server ended the session or dropped us).
            _incoming.emit(JamServerEvent.Disconnected())
        } catch (e: CancellationException) {
            throw e // close()/leave cancelled the scope — not an error; no terminal emission needed.
        } catch (e: Exception) {
            Timber.w("Jam socket disconnected: ${e.javaClass.simpleName}")
            _incoming.emit(JamServerEvent.Disconnected(e.message))
        }
    }

    /** Decodes one inbound frame into zero or more [JamServerEvent]s and emits them. */
    private suspend fun emitEvents(applier: SessionStateApplier, frame: FrameDto) {
        when (frame.type) {
            FRAME_SESSION_SNAPSHOT, FRAME_SESSION_DELTA -> {
                applier.apply(frame)?.let { _incoming.emit(JamServerEvent.Membership(it)) }
                // A snapshot also carries the initial playback; a `playback` delta carries a new one.
                applier.playbackFrom(frame)?.let { _incoming.emit(JamServerEvent.Playback(it)) }
            }
            FRAME_TIME_RESP -> frame.timeResp?.let { _incoming.emit(JamServerEvent.TimeResp(it)) }
            FRAME_COMMAND_ACK -> frame.commandAck?.let { _incoming.emit(JamServerEvent.CommandAck(it)) }
            else -> Unit // status/resume/... — not consumed here.
        }
    }
}

/** Wraps a [JamClientFrame] in the correctly-typed WS envelope. */
private fun JamClientFrame.toFrameDto(): FrameDto = when (this) {
    is JamClientFrame.TimeReq -> FrameDto(type = FRAME_TIME_REQ, timeReq = req)
    is JamClientFrame.Command -> FrameDto(type = FRAME_COMMAND, command = command)
}

/**
 * Folds incoming frames into the current authoritative [SessionSnapshotDto]: a snapshot resets it,
 * membership deltas mutate the member list. Returns the new snapshot when it changed, or null for a
 * frame that doesn't affect membership (so callers can skip a no-op emission).
 *
 * Not thread-safe — drive it from a single collecting coroutine.
 */
class SessionStateApplier {
    private var current: SessionSnapshotDto? = null

    fun apply(frame: FrameDto): SessionSnapshotDto? = when (frame.type) {
        FRAME_SESSION_SNAPSHOT -> frame.sessionSnapshot?.let { snap ->
            current = snap
            snap
        }
        FRAME_SESSION_DELTA -> frame.sessionDelta?.let { applyDelta(it) }
        else -> null
    }

    /**
     * Extracts the shared playback [TimelineDto] carried by [frame], if any: a snapshot's `playback`,
     * or a `playback`-kind delta's timeline. Independent of membership folding so both can fire off one
     * snapshot frame. Returns null for frames with no playback state.
     */
    fun playbackFrom(frame: FrameDto): TimelineDto? = when (frame.type) {
        FRAME_SESSION_SNAPSHOT -> frame.sessionSnapshot?.playback
        FRAME_SESSION_DELTA -> frame.sessionDelta
            ?.takeIf { it.kind == DELTA_PLAYBACK }
            ?.playback
        else -> null
    }

    private fun applyDelta(delta: SessionDeltaDto): SessionSnapshotDto? {
        val base = current ?: return null
        val updated = when (delta.kind) {
            DELTA_SNAPSHOT -> delta.snapshot
            DELTA_MEMBER_JOINED -> delta.memberJoined?.let { joined ->
                val without = base.members.filterNot { it.deviceId == joined.deviceId }
                base.copy(members = without + joined)
            }
            DELTA_MEMBER_LEFT -> delta.memberLeft?.let { deviceId ->
                base.copy(members = base.members.filterNot { it.deviceId == deviceId })
            }
            // Authoritative full presence snapshot — the whole member list is re-sent.
            DELTA_PRESENCE -> if (delta.presence.isNotEmpty()) {
                base.copy(members = delta.presence)
            } else {
                null
            }
            DELTA_HOST -> delta.host?.let { base.copy(host = it) }
            // Permissions gate canControl; fold them so the mapped session reflects a live toggle.
            DELTA_PERMISSIONS -> delta.permissions?.let { base.copy(permissions = it) }
            else -> null // playback/queue/etc. — not membership-relevant (playback handled separately).
        }
        if (updated != null) current = updated
        return updated
    }
}

/**
 * Derives the ws:// or wss:// gateway URL from the http(s) base origin: swaps the scheme and appends
 * `/ws`. e.g. `https://api.viper.player` -> `wss://api.viper.player/ws`.
 */
internal fun toWebSocketUrl(httpBaseUrl: String): String {
    val trimmed = httpBaseUrl.trimEnd('/')
    val swapped = when {
        trimmed.startsWith("https://", ignoreCase = true) -> "wss://" + trimmed.substring("https://".length)
        trimmed.startsWith("http://", ignoreCase = true) -> "ws://" + trimmed.substring("http://".length)
        else -> trimmed // already ws:// / wss:// (or scheme-less) — leave as-is.
    }
    return "$swapped/ws"
}

/**
 * A fake [JamConnection] for tests: replays a scripted list of inbound frames as server events (folding
 * membership via a [SessionStateApplier], surfacing playback/time_resp/command_ack), and records every
 * [JamClientFrame] sent. Optionally emits a terminal [JamServerEvent.Disconnected].
 *
 * The scripted frames are emitted lazily on first collection of [incoming] so tests can subscribe, then
 * assert; [sent] captures outbound frames for command/clock assertions.
 */
class FakeJamConnection(
    private val frames: List<FrameDto> = emptyList(),
    private val disconnectAfter: Boolean = false,
) : JamConnection {

    private val _incoming = MutableSharedFlow<JamServerEvent>(replay = 64, extraBufferCapacity = 64)
    override val incoming: SharedFlow<JamServerEvent> = _incoming.asSharedFlow()

    /** Every client frame the code under test sent, in order. */
    val sent = mutableListOf<JamClientFrame>()
    var closed = false
        private set

    /** Replays the scripted frames into [incoming] now. Call from the test after construction. */
    suspend fun emitScripted() {
        val applier = SessionStateApplier()
        for (frame in frames) {
            when (frame.type) {
                FRAME_SESSION_SNAPSHOT, FRAME_SESSION_DELTA -> {
                    applier.apply(frame)?.let { _incoming.emit(JamServerEvent.Membership(it)) }
                    applier.playbackFrom(frame)?.let { _incoming.emit(JamServerEvent.Playback(it)) }
                }
                FRAME_TIME_RESP -> frame.timeResp?.let { _incoming.emit(JamServerEvent.TimeResp(it)) }
                FRAME_COMMAND_ACK -> frame.commandAck?.let { _incoming.emit(JamServerEvent.CommandAck(it)) }
            }
        }
        if (disconnectAfter) _incoming.emit(JamServerEvent.Disconnected())
    }

    /** Pushes a single server event into [incoming] (for tests driving events one at a time). */
    suspend fun emit(event: JamServerEvent) = _incoming.emit(event)

    override suspend fun send(frame: JamClientFrame) {
        sent += frame
    }

    override fun close() {
        closed = true
    }
}

/**
 * A [JamSocketClient] that hands out a single pre-built [FakeJamConnection], recording the URL + jwt it
 * was asked to connect with. Lets a repository test control the connection directly.
 */
class FakeJamSocketClient(
    private val connection: FakeJamConnection = FakeJamConnection(),
) : JamSocketClient {
    var connectedUrl: String? = null
        private set
    var connectedJwt: String? = null
        private set

    override fun connect(httpBaseUrl: String, jwt: String): JamConnection {
        connectedUrl = toWebSocketUrl(httpBaseUrl)
        connectedJwt = jwt
        return connection
    }
}
