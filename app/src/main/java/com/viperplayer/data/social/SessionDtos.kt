package com.viperplayer.data.social

import kotlinx.serialization.Serializable

/**
 * JSON DTOs for the ViPER backend Listen-Together (Jam) session service (github.com/iscle/
 * viper-backend). Field names mirror the backend's Go structs exactly (camelCase `json` tags), so no
 * `@SerialName` remapping is needed. See internal/httpapi/api.go (REST) and internal/wire (the
 * WebSocket frame schema).
 *
 * Both membership AND synchronized-playback fields are modelled: membership (snapshot + deltas) keeps
 * the participant list live, while the playback [TimelineDto] (on the snapshot and on `playback`
 * deltas) plus the clock-sync ([TimeReqDto]/[TimeRespDto]) and transport [CommandDto] frames drive the
 * shared playback engine. Fields we still don't model (queue, volume, ...) are parsed leniently
 * (Json { ignoreUnknownKeys = true }) and ignored.
 */

// --- REST: POST /sessions, POST /sessions/join ---

@Serializable
data class CreateSessionRequestDto(
    val deviceId: String,
    val userId: String,
    val name: String,
    val mode: String = MODE_JAM,
)

@Serializable
data class CreateSessionResponseDto(
    val sessionId: String,
    val code: String,
    val inviteUrl: String = "",
    val jwt: String,
    val snapshot: SessionSnapshotDto,
)

@Serializable
data class JoinSessionRequestDto(
    val code: String,
    val deviceId: String,
    val userId: String,
    val name: String,
)

@Serializable
data class JoinSessionResponseDto(
    val sessionId: String,
    val jwt: String,
    val snapshot: SessionSnapshotDto,
)

/** Backend error envelope: {"error": "..."} on any non-2xx. */
@Serializable
internal data class SessionErrorDto(val error: String = "")

// --- WebSocket frames (internal/wire) ---

/**
 * The WS envelope. Exactly one typed payload is non-null, selected by [type]. The frame types this
 * feature cares about are modelled: membership (snapshot + delta), the shared playback timeline
 * (snapshot.playback + `playback` deltas), the clock-sync pair (timeReq/timeResp) and the transport
 * command pair (command/commandAck). The rest (status/resume/...) decode to null and are ignored.
 */
@Serializable
data class FrameDto(
    val type: String = "",
    val sessionSnapshot: SessionSnapshotDto? = null,
    val sessionDelta: SessionDeltaDto? = null,
    val timeReq: TimeReqDto? = null,
    val timeResp: TimeRespDto? = null,
    val command: CommandDto? = null,
    val commandAck: CommandAckDto? = null,
)

@Serializable
data class MemberDto(
    val userId: String = "",
    val deviceId: String = "",
    val name: String = "",
    val role: String = "",
    val presence: Boolean = true,
)

/** Host-toggled session-wide gates (internal/wire Permissions). Only the transport gate is modelled. */
@Serializable
data class PermissionsDto(
    val guestsCanControl: Boolean = false,
)

@Serializable
data class SessionSnapshotDto(
    val sessionId: String = "",
    val mode: String = "",
    val host: MemberDto = MemberDto(),
    val members: List<MemberDto> = emptyList(),
    val permissions: PermissionsDto = PermissionsDto(),
    /** The server-authoritative shared playback state at snapshot time. */
    val playback: TimelineDto = TimelineDto(),
)

@Serializable
data class SessionDeltaDto(
    val seq: Long = 0,
    val kind: String = "",
    val memberJoined: MemberDto? = null,
    /** deviceId of the member who left (kind == member_left). */
    val memberLeft: String? = null,
    /** Authoritative full presence snapshot (kind == presence). */
    val presence: List<MemberDto> = emptyList(),
    /** A full session snapshot re-sent as a delta (kind == snapshot). */
    val snapshot: SessionSnapshotDto? = null,
    /** New host (kind == host). */
    val host: MemberDto? = null,
    /** New authoritative playback timeline (kind == playback). */
    val playback: TimelineDto? = null,
    /** New permissions (kind == permissions). */
    val permissions: PermissionsDto? = null,
)

/**
 * The portable identity of a track plus display metadata (internal/wire MediaRef). PluginId+SourceId
 * form the portable MediaId; the display fields let a member without that plugin render a greyed entry.
 * Note the backend has NO `album` field on MediaRef.
 */
@Serializable
data class MediaRefDto(
    val pluginId: String = "",
    val sourceId: String = "",
    val title: String = "",
    val artist: String = "",
    val artworkUrl: String = "",
    val durationMs: Long = 0,
)

/**
 * The server-authoritative playback state (internal/wire Timeline). All times are server monotonic
 * MICROSECONDS. These are the four load-bearing numbers (p0/t0/rate + effectiveAt) plus the current
 * track and controller. See internal/session/timeline.go for the extrapolation formula.
 */
@Serializable
data class TimelineDto(
    val epoch: Long = 0,
    val track: MediaRefDto = MediaRefDto(),
    val p0Us: Long = 0,
    val t0Us: Long = 0,
    val rate: Float = 0f,
    val effectiveAtUs: Long = 0,
    val controllerId: String = "",
)

// --- Clock sync (internal/wire clock.go): the NTP-style four-timestamp burst ---

/** Client-initiated clock ping. [t0] is the client's monotonic send time (ns). */
@Serializable
data class TimeReqDto(val t0: Long)

/**
 * Server reply to a [TimeReqDto]: echoes [t0] and adds the server monotonic receive ([t1]) and send
 * ([t2]) times (ns). The client stamps `t3` on receipt to complete the four-timestamp set.
 */
@Serializable
data class TimeRespDto(
    val t0: Long = 0,
    val t1: Long = 0,
    val t2: Long = 0,
)

// --- Transport commands (internal/wire command.go): client→server RPC ---

/**
 * A transport command the sender issues; the server authorizes by the connection's authenticated
 * deviceId, applies it, and broadcasts the resulting `playback` delta to everyone (incl. the sender)
 * plus a [CommandAckDto] to the sender only. Only the payload matching [kind] is read.
 *
 * [forSeq] is a client-chosen correlation id echoed back in the ack (distinct from the session seq).
 */
@Serializable
data class CommandDto(
    val forSeq: Long = 0,
    val kind: String = "",
    val seek: SeekPayloadDto? = null,
    val track: TrackPayloadDto? = null,
)

@Serializable
data class SeekPayloadDto(val positionUs: Long)

@Serializable
data class TrackPayloadDto(
    val mediaRef: MediaRefDto,
    /** Optionally names the exact queue entry to play; empty anchors to the first matching entry. */
    val uid: String = "",
)

/** Server confirmation (or rejection) of a [CommandDto], back to the sender only. */
@Serializable
data class CommandAckDto(
    val forSeq: Long = 0,
    val ok: Boolean = false,
    val error: String = "",
)

// Frame + delta discriminators (mirror internal/wire).
const val FRAME_SESSION_SNAPSHOT = "session_snapshot"
const val FRAME_SESSION_DELTA = "session_delta"
const val FRAME_TIME_REQ = "time_req"
const val FRAME_TIME_RESP = "time_resp"
const val FRAME_COMMAND = "command"
const val FRAME_COMMAND_ACK = "command_ack"

const val DELTA_MEMBER_JOINED = "member_joined"
const val DELTA_MEMBER_LEFT = "member_left"
const val DELTA_PRESENCE = "presence"
const val DELTA_SNAPSHOT = "snapshot"
const val DELTA_HOST = "host"
const val DELTA_PLAYBACK = "playback"
const val DELTA_PERMISSIONS = "permissions"

// Command kinds (internal/wire CommandKind).
const val CMD_PLAY = "play"
const val CMD_PAUSE = "pause"
const val CMD_SEEK = "seek"
const val CMD_TRACK = "track"
const val CMD_NEXT = "next"
const val CMD_PREV = "prev"

/** Session mode sent on create. The backend defaults an empty mode to JAM; we send it explicitly. */
const val MODE_JAM = "JAM"

/** Member roles (internal/wire Role). */
const val ROLE_HOST = "HOST"
const val ROLE_CONTROLLER = "CONTROLLER"
const val ROLE_MEMBER = "MEMBER"
const val ROLE_LISTENER = "LISTENER"
