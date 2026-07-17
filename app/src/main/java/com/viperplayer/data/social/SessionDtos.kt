package com.viperplayer.data.social

import kotlinx.serialization.Serializable

/**
 * JSON DTOs for the ViPER backend Listen-Together (Jam) session service (github.com/iscle/
 * viper-backend). Field names mirror the backend's Go structs exactly (camelCase `json` tags), so no
 * `@SerialName` remapping is needed. See internal/httpapi/api.go (REST) and internal/wire (the
 * WebSocket frame schema).
 *
 * Only the membership-relevant fields are modelled — playback timeline, queue, permissions and the
 * various command frames are parsed leniently (Json { ignoreUnknownKeys = true }) and otherwise
 * ignored. Session lifecycle + the live participant list is the whole scope here.
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
 * The WS envelope. Exactly one typed payload is non-null, selected by [type]. Only the frame types
 * this feature cares about (snapshot + delta) are modelled; the rest (timeline/status/command/...)
 * decode to null and are ignored.
 */
@Serializable
data class FrameDto(
    val type: String = "",
    val sessionSnapshot: SessionSnapshotDto? = null,
    val sessionDelta: SessionDeltaDto? = null,
)

@Serializable
data class MemberDto(
    val userId: String = "",
    val deviceId: String = "",
    val name: String = "",
    val role: String = "",
    val presence: Boolean = true,
)

@Serializable
data class SessionSnapshotDto(
    val sessionId: String = "",
    val mode: String = "",
    val host: MemberDto = MemberDto(),
    val members: List<MemberDto> = emptyList(),
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
)

// Frame + delta discriminators (mirror internal/wire).
const val FRAME_SESSION_SNAPSHOT = "session_snapshot"
const val FRAME_SESSION_DELTA = "session_delta"

const val DELTA_MEMBER_JOINED = "member_joined"
const val DELTA_MEMBER_LEFT = "member_left"
const val DELTA_PRESENCE = "presence"
const val DELTA_SNAPSHOT = "snapshot"
const val DELTA_HOST = "host"

/** Session mode sent on create. The backend defaults an empty mode to JAM; we send it explicitly. */
const val MODE_JAM = "JAM"

/** Member roles (internal/wire Role). */
const val ROLE_HOST = "HOST"
