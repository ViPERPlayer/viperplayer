package com.viperplayer.data.social

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The live state a [JamSocketClient] subscription emits: an authoritative session snapshot (with the
 * member list kept current by applying membership deltas), or a terminal [Disconnected] once the
 * socket closes/fails.
 */
sealed interface JamSocketState {
    /** The latest authoritative session state (snapshot + applied membership deltas). */
    data class State(val snapshot: SessionSnapshotDto) : JamSocketState

    /** The socket closed or failed. Terminal — no further frames follow on this connection. */
    data class Disconnected(val cause: String? = null) : JamSocketState
}

/**
 * A single Jam WebSocket connection.
 *
 * Implementations connect to `<wsBaseUrl>/ws` presenting the session `jwt` as an
 * `Authorization: Bearer` header (NEVER a query param — it leaks into logs), consume the
 * `session_snapshot` + `session_delta` frames, and expose the evolving session state as a [Flow].
 * Frame types outside membership scope (timeline/status/command/...) are ignored.
 */
interface JamSocketClient {
    /**
     * Opens a connection and returns a cold [Flow] of [JamSocketState]. Collecting drives the socket;
     * cancelling the collector (see the repository's leaveSession) tears it down — the server treats a
     * closed socket as the member leaving. The flow completes after emitting [JamSocketState.Disconnected].
     */
    fun connect(httpBaseUrl: String, jwt: String): Flow<JamSocketState>
}

/**
 * Ktor-backed [JamSocketClient] over the shared [HttpClient] (which has `install(WebSockets)`).
 *
 * The returned flow is cold: the socket opens on collection and closes when the collector is
 * cancelled. There is no built-in auto-reconnect — a drop surfaces as [JamSocketState.Disconnected]
 * and the caller decides what to do (the current UI just ends the session).
 */
@Singleton
class KtorJamSocketClient @Inject constructor(
    private val httpClient: HttpClient,
) : JamSocketClient {

    private val json = Json { ignoreUnknownKeys = true }

    override fun connect(httpBaseUrl: String, jwt: String): Flow<JamSocketState> = flow {
        val wsUrl = toWebSocketUrl(httpBaseUrl)
        val applier = SessionStateApplier()
        try {
            httpClient.webSocket(
                urlString = wsUrl,
                request = { header(HttpHeaders.Authorization, "Bearer $jwt") },
            ) {
                for (frame in incoming) {
                    if (frame !is Frame.Text) continue
                    val text = frame.readText()
                    val decoded = runCatching { json.decodeFromString<FrameDto>(text) }.getOrNull()
                        ?: continue
                    val next = applier.apply(decoded) ?: continue
                    emit(JamSocketState.State(next))
                }
            }
            // incoming closed normally (server ended the session or dropped us).
            emit(JamSocketState.Disconnected())
        } catch (e: CancellationException) {
            throw e // collector cancelled (leaveSession) — not an error; no terminal emission needed.
        } catch (e: Exception) {
            Timber.w("Jam socket disconnected: ${e.javaClass.simpleName}")
            emit(JamSocketState.Disconnected(e.message))
        }
    }
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
            else -> null // playback/queue/permissions/etc. — not membership-relevant.
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
 * A minimal fake [JamSocketClient] for tests: replays a scripted list of frames as membership state,
 * then optionally a disconnect. Records the URL + jwt it was asked to connect with.
 */
class FakeJamSocketClient(
    private val frames: List<FrameDto>,
    private val disconnectAfter: Boolean = false,
) : JamSocketClient {
    var connectedUrl: String? = null
        private set
    var connectedJwt: String? = null
        private set

    override fun connect(httpBaseUrl: String, jwt: String): Flow<JamSocketState> {
        connectedUrl = toWebSocketUrl(httpBaseUrl)
        connectedJwt = jwt
        return flow {
            val applier = SessionStateApplier()
            for (frame in frames) {
                applier.apply(frame)?.let { emit(JamSocketState.State(it)) }
            }
            if (disconnectAfter) emit(JamSocketState.Disconnected())
        }
    }
}
