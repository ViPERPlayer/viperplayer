package com.viperplayer.data.social

import com.viperplayer.data.repository.toListenSession
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Frame decoding + membership folding + snapshot→[com.viperplayer.domain.model.ListenSession] mapping.
 *
 * Covers: a `session_snapshot` frame decodes and seeds state; a `member_joined` delta appends the
 * member; a `member_left` delta drops them; a `presence` delta replaces the whole list; the
 * snapshot→ListenSession mapping resolves isHost + isSelf from the local deviceId; and the ws:// URL
 * derivation.
 */
class SessionFrameMappingTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun member(deviceId: String, name: String, role: String = "MEMBER") =
        MemberDto(userId = "u-$deviceId", deviceId = deviceId, name = name, role = role, presence = true)

    private fun snapshotFrame(host: MemberDto, members: List<MemberDto>) = FrameDto(
        type = FRAME_SESSION_SNAPSHOT,
        sessionSnapshot = SessionSnapshotDto(sessionId = "s-1", mode = "JAM", host = host, members = members),
    )

    @Test
    fun decodesSnapshotFrame_fromBackendJson() {
        val raw = """
            {
              "type": "session_snapshot",
              "sessionSnapshot": {
                "sessionId": "s-1",
                "mode": "JAM",
                "host": {"userId":"u1","deviceId":"host-dev","name":"Alice","role":"HOST","presence":true},
                "members": [
                  {"userId":"u1","deviceId":"host-dev","name":"Alice","role":"HOST","presence":true}
                ],
                "activeDeviceIds": ["host-dev"],
                "permissions": {"guestsCanControl": true},
                "queue": [],
                "playback": {"rate": 0.0},
                "seq": 3
              }
            }
        """.trimIndent()

        val frame = json.decodeFromString<FrameDto>(raw)
        assertEquals(FRAME_SESSION_SNAPSHOT, frame.type)
        val snap = frame.sessionSnapshot!!
        assertEquals("s-1", snap.sessionId)
        assertEquals("host-dev", snap.host.deviceId)
        assertEquals(1, snap.members.size)
    }

    @Test
    fun unknownFrameType_decodesToNullPayloads_andIsIgnored() {
        val raw = """{"type":"timeline","timeline":{"rate":1.0}}"""
        val frame = json.decodeFromString<FrameDto>(raw)
        assertEquals("timeline", frame.type)
        assertNull(frame.sessionSnapshot)
        assertNull(frame.sessionDelta)
        // Applier ignores it (no membership effect).
        assertNull(SessionStateApplier().apply(frame))
    }

    @Test
    fun applier_snapshotThenMemberJoined_appendsMember() {
        val applier = SessionStateApplier()
        val host = member("host-dev", "Alice", role = "HOST")
        assertEquals(1, applier.apply(snapshotFrame(host, listOf(host)))!!.members.size)

        val joinDelta = FrameDto(
            type = FRAME_SESSION_DELTA,
            sessionDelta = SessionDeltaDto(seq = 1, kind = DELTA_MEMBER_JOINED, memberJoined = member("bob-dev", "Bob")),
        )
        val after = applier.apply(joinDelta)!!
        assertEquals(2, after.members.size)
        assertTrue(after.members.any { it.deviceId == "bob-dev" })
    }

    @Test
    fun applier_memberJoined_isIdempotentOnDuplicateDeviceId() {
        val applier = SessionStateApplier()
        val host = member("host-dev", "Alice", role = "HOST")
        applier.apply(snapshotFrame(host, listOf(host)))
        val join = FrameDto(type = FRAME_SESSION_DELTA, sessionDelta = SessionDeltaDto(kind = DELTA_MEMBER_JOINED, memberJoined = member("bob-dev", "Bob")))
        applier.apply(join)
        val after = applier.apply(join)!!
        assertEquals(2, after.members.size) // no duplicate bob-dev
    }

    @Test
    fun applier_memberLeft_removesMember() {
        val applier = SessionStateApplier()
        val host = member("host-dev", "Alice", role = "HOST")
        val bob = member("bob-dev", "Bob")
        applier.apply(snapshotFrame(host, listOf(host, bob)))

        val leftDelta = FrameDto(
            type = FRAME_SESSION_DELTA,
            sessionDelta = SessionDeltaDto(seq = 2, kind = DELTA_MEMBER_LEFT, memberLeft = "bob-dev"),
        )
        val after = applier.apply(leftDelta)!!
        assertEquals(1, after.members.size)
        assertFalse(after.members.any { it.deviceId == "bob-dev" })
    }

    @Test
    fun applier_presenceDelta_replacesWholeList() {
        val applier = SessionStateApplier()
        val host = member("host-dev", "Alice", role = "HOST")
        applier.apply(snapshotFrame(host, listOf(host, member("bob-dev", "Bob"))))

        val presence = FrameDto(
            type = FRAME_SESSION_DELTA,
            sessionDelta = SessionDeltaDto(
                kind = DELTA_PRESENCE,
                presence = listOf(host, member("carol-dev", "Carol")),
            ),
        )
        val after = applier.apply(presence)!!
        assertEquals(2, after.members.size)
        assertTrue(after.members.any { it.deviceId == "carol-dev" })
        assertFalse(after.members.any { it.deviceId == "bob-dev" })
    }

    @Test
    fun applier_deltaBeforeSnapshot_isNoOp() {
        // A delta arriving before any snapshot has no base state to mutate.
        val delta = FrameDto(type = FRAME_SESSION_DELTA, sessionDelta = SessionDeltaDto(kind = DELTA_MEMBER_JOINED, memberJoined = member("x", "X")))
        assertNull(SessionStateApplier().apply(delta))
    }

    @Test
    fun mapping_toListenSession_resolvesHostAndSelf() {
        val host = member("host-dev", "Alice", role = "HOST")
        val me = member("me-dev", "Bob")
        val snapshot = SessionSnapshotDto(sessionId = "s", mode = "JAM", host = host, members = listOf(host, me))

        val asHost = snapshot.toListenSession(localDeviceId = "host-dev", code = "ABCDEF", inviteUrl = "https://viper.player/jam/abcdef")
        assertTrue(asHost.isHost)
        assertEquals("Alice", asHost.hostName)
        assertEquals("ABCDEF", asHost.code)
        assertEquals(2, asHost.participants.size)
        assertTrue(asHost.participants.single { it.id == "host-dev" }.isSelf)

        val asGuest = snapshot.toListenSession(localDeviceId = "me-dev", code = "ABCDEF", inviteUrl = "u")
        assertFalse(asGuest.isHost)
        assertTrue(asGuest.participants.single { it.id == "me-dev" }.isSelf)
        assertFalse(asGuest.participants.single { it.id == "host-dev" }.isSelf)
        assertEquals(1, asGuest.othersCount)
    }

    @Test
    fun toWebSocketUrl_swapsSchemeAndAppendsWs() {
        assertEquals("wss://api.viper.player/ws", toWebSocketUrl("https://api.viper.player"))
        assertEquals("ws://10.0.2.2:8080/ws", toWebSocketUrl("http://10.0.2.2:8080/"))
        // Already ws — leave scheme, still append the /ws path.
        assertEquals("wss://x/ws", toWebSocketUrl("wss://x"))
    }
}
