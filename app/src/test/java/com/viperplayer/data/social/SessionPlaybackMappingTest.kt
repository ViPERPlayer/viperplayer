package com.viperplayer.data.social

import com.viperplayer.data.repository.toSessionPlayback
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Decoding a snapshot-with-playback and a `playback` delta into the domain
 * [com.viperplayer.domain.model.SessionPlayback], plus the position-extrapolation formula at boundaries
 * (before/after effectiveAt, paused) — asserted against the backend's `TargetPositionUs`
 * (internal/session/timeline.go). Also round-trips an outbound [CommandDto] to confirm the wire JSON
 * (encodeDefaults) carries payload fields.
 */
class SessionPlaybackMappingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun decodesSnapshotPlayback_intoSessionPlayback() {
        val raw = """
            {
              "type": "session_snapshot",
              "sessionSnapshot": {
                "sessionId": "s-1",
                "host": {"deviceId":"h","role":"HOST"},
                "members": [{"deviceId":"h","role":"HOST"}],
                "permissions": {"guestsCanControl": true},
                "playback": {
                  "epoch": 4,
                  "track": {"pluginId":"testsource","sourceId":"99","title":"T","artist":"A","artworkUrl":"http://x","durationMs":210000},
                  "p0Us": 3000000,
                  "t0Us": 8000000,
                  "rate": 1.0,
                  "effectiveAtUs": 8000000,
                  "controllerId": "h"
                }
              }
            }
        """.trimIndent()

        val frame = json.decodeFromString<FrameDto>(raw)
        val pb = frame.sessionSnapshot!!.playback.toSessionPlayback()

        assertEquals(4, pb.epoch)
        assertEquals(3_000_000, pb.positionAnchorUs)
        assertEquals(8_000_000, pb.anchorServerTimeUs)
        assertEquals(1.0f, pb.rate)
        assertEquals(8_000_000, pb.effectiveAtServerTimeUs)
        assertEquals("h", pb.controllerId)
        val track = pb.track!!
        assertEquals("testsource", track.pluginId)
        assertEquals("99", track.sourceId)
        assertEquals("T", track.title)
        assertEquals("A", track.artist)
        assertEquals("http://x", track.artworkUrl)
        assertEquals(210_000, track.durationMs)
        // mediaId is derived from plugin+source.
        assertEquals("testsource", track.mediaId!!.pluginId)
    }

    @Test
    fun decodesPlaybackDelta_intoSessionPlayback() {
        val raw = """
            {
              "type": "session_delta",
              "sessionDelta": {
                "seq": 12,
                "kind": "playback",
                "playback": {"epoch": 5, "track": {"pluginId":"p","sourceId":"s"}, "p0Us": 0, "t0Us": 1000000, "rate": 0.0, "effectiveAtUs": 1000000}
              }
            }
        """.trimIndent()

        val frame = json.decodeFromString<FrameDto>(raw)
        val timeline = SessionStateApplier().playbackFrom(frame)!!
        val pb = timeline.toSessionPlayback()
        assertEquals(5, pb.epoch)
        assertEquals(0f, pb.rate)
        assertEquals("p", pb.track!!.pluginId)
    }

    @Test
    fun blankTrackRef_mapsToNullTrack() {
        val pb = TimelineDto(epoch = 1, track = MediaRefDto(), rate = 0f).toSessionPlayback()
        assertNull("empty plugin+source means nothing loaded", pb.track)
    }

    @Test
    fun positionExtrapolation_matchesBackendFormula() {
        // Playing: p0=1_000_000 at t0=5_000_000, effectiveAt=5_000_000, rate=1.0.
        val playing = TimelineDto(p0Us = 1_000_000, t0Us = 5_000_000, rate = 1.0f, effectiveAtUs = 5_000_000)
            .toSessionPlayback()
        // Before effectiveAt: holds at the anchor.
        assertEquals(1_000_000, playing.positionUsAt(4_000_000))
        // Exactly at effectiveAt: at the anchor (elapsed 0).
        assertEquals(1_000_000, playing.positionUsAt(5_000_000))
        // 2s after t0: +2s of media.
        assertEquals(3_000_000, playing.positionUsAt(7_000_000))

        // Paused: rate 0 freezes at p0 regardless of serverNow.
        val paused = TimelineDto(p0Us = 2_500_000, t0Us = 5_000_000, rate = 0f, effectiveAtUs = 5_000_000)
            .toSessionPlayback()
        assertEquals(2_500_000, paused.positionUsAt(4_000_000))
        assertEquals(2_500_000, paused.positionUsAt(9_000_000))
    }

    @Test
    fun commandDto_roundTrips_withPayloadFields() {
        val seek = CommandDto(forSeq = 3, kind = CMD_SEEK, seek = SeekPayloadDto(positionUs = 123456))
        val encoded = json.encodeToString(seek)
        val decoded = json.decodeFromString<CommandDto>(encoded)
        assertEquals(CMD_SEEK, decoded.kind)
        assertEquals(3, decoded.forSeq)
        assertEquals(123456, decoded.seek!!.positionUs)

        val track = CommandDto(kind = CMD_TRACK, track = TrackPayloadDto(mediaRef = MediaRefDto(pluginId = "testsource", sourceId = "1")))
        val trackJson = json.encodeToString(track)
        // encodeDefaults must emit the nested mediaRef so the backend reads it.
        assertEquals("testsource", json.decodeFromString<CommandDto>(trackJson).track!!.mediaRef.pluginId)
    }
}
