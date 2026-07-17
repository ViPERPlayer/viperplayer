package com.viperplayer.data.social

import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.SessionPlayback
import com.viperplayer.domain.model.SessionTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [FollowerCorrection.decide] — the pure decision core of the Listen-together follower
 * loop. Covers unsynced, no-track, track-load, pre-roll hold, paused, playing with large/small/zero
 * drift, the deadband, the speed clamp, and track change bumping the epoch.
 */
class FollowerCorrectionTest {

    private fun track(plugin: String = "testsource", source: String = "42") = SessionTrack(
        pluginId = plugin,
        sourceId = source,
        title = "Song",
        artist = "Artist",
        album = "",
        artworkUrl = "http://art",
        durationMs = 200_000,
    )

    private fun playback(
        track: SessionTrack? = track(),
        epoch: Long = 1,
        anchorUs: Long = 0,
        anchorServerUs: Long = 0,
        rate: Float = 1f,
        effectiveAtUs: Long = 0,
    ) = SessionPlayback(
        track = track,
        epoch = epoch,
        positionAnchorUs = anchorUs,
        anchorServerTimeUs = anchorServerUs,
        rate = rate,
        effectiveAtServerTimeUs = effectiveAtUs,
        controllerId = "",
    )

    private val mediaId = MediaId("testsource", "42")

    @Test
    fun unsynced_serverNowNull_surfacesSyncing() {
        val d = FollowerCorrection.decide(playback(), serverNowUs = null, localPosMs = 0, loaded = null, ready = false)
        assertTrue(d.syncing)
        assertNull(d.loadTrack)
        assertNull(d.seekMs)
    }

    @Test
    fun nullPlayback_surfacesSyncing() {
        val d = FollowerCorrection.decide(null, serverNowUs = 1_000, localPosMs = 0, loaded = null, ready = false)
        assertTrue(d.syncing)
    }

    @Test
    fun noTrack_isIdle_doesNotTouchPlayer() {
        val d = FollowerCorrection.decide(playback(track = null), serverNowUs = 1_000, localPosMs = 0, loaded = null, ready = false)
        assertTrue(d.idle)
        assertNull(d.loadTrack)
        assertNull(d.seekMs)
    }

    @Test
    fun freshTrack_notLoaded_requestsLoad() {
        val d = FollowerCorrection.decide(
            playback(epoch = 3, effectiveAtUs = 0, rate = 1f),
            serverNowUs = 10_000, localPosMs = 0, loaded = null, ready = false,
        )
        val load = d.loadTrack
        assertNotNull(load)
        assertEquals(3, load!!.epoch)
        assertEquals(mediaId, load.mediaId)
        assertEquals("Song", load.title)
        assertTrue(d.play) // rate>0 so playWhenReady true
    }

    @Test
    fun sameTrackSameEpoch_alreadyLoaded_noReload() {
        val loaded = FollowerCorrection.LoadedTrack(epoch = 1, mediaId = mediaId)
        val d = FollowerCorrection.decide(
            playback(epoch = 1, rate = 1f, effectiveAtUs = 0),
            serverNowUs = 10_000, localPosMs = 10, loaded = loaded, ready = true,
        )
        assertNull(d.loadTrack)
    }

    @Test
    fun trackChange_bumpsEpoch_reloads() {
        val loaded = FollowerCorrection.LoadedTrack(epoch = 1, mediaId = mediaId)
        val d = FollowerCorrection.decide(
            playback(track = track(source = "99"), epoch = 2, rate = 1f, effectiveAtUs = 0),
            serverNowUs = 10_000, localPosMs = 10, loaded = loaded, ready = true,
        )
        val load = d.loadTrack
        assertNotNull(load)
        assertEquals(2, load!!.epoch)
        assertEquals(MediaId("testsource", "99"), load.mediaId)
    }

    @Test
    fun sameMediaId_newEpoch_reloads() {
        // Re-seeding the same track in a new epoch (e.g. rejoining) must still reload.
        val loaded = FollowerCorrection.LoadedTrack(epoch = 1, mediaId = mediaId)
        val d = FollowerCorrection.decide(
            playback(epoch = 5, rate = 1f, effectiveAtUs = 0),
            serverNowUs = 10_000, localPosMs = 10, loaded = loaded, ready = true,
        )
        assertNotNull(d.loadTrack)
        assertEquals(5, d.loadTrack!!.epoch)
    }

    @Test
    fun preRoll_beforeEffectiveAt_holdsPausedAtAnchor() {
        val loaded = FollowerCorrection.LoadedTrack(epoch = 1, mediaId = mediaId)
        val d = FollowerCorrection.decide(
            playback(epoch = 1, anchorUs = 5_000_000, anchorServerUs = 0, rate = 1f, effectiveAtUs = 2_000_000),
            serverNowUs = 1_000_000, // before effectiveAt
            localPosMs = 0, loaded = loaded, ready = true,
        )
        assertFalse(d.play)
        assertEquals(5_000L, d.seekMs) // anchor 5_000_000us -> 5000ms
        assertEquals(1f, d.speed)
    }

    @Test
    fun paused_rateZero_holdsAtTarget_noSpeedCorrection() {
        val loaded = FollowerCorrection.LoadedTrack(epoch = 1, mediaId = mediaId)
        val d = FollowerCorrection.decide(
            playback(epoch = 1, anchorUs = 8_000_000, rate = 0f, effectiveAtUs = 0),
            serverNowUs = 10_000_000,
            localPosMs = 3000, loaded = loaded, ready = true,
        )
        assertFalse(d.play)
        assertEquals(8_000L, d.seekMs) // holds at anchor (rate 0 -> positionUsAt returns anchor)
        assertEquals(1f, d.speed)
    }

    @Test
    fun playing_notReady_hardSeeksToTarget_speed1() {
        val loaded = FollowerCorrection.LoadedTrack(epoch = 1, mediaId = mediaId)
        val d = FollowerCorrection.decide(
            playback(epoch = 1, anchorUs = 0, anchorServerUs = 0, rate = 1f, effectiveAtUs = 0),
            serverNowUs = 4_000_000, // target ~4000ms
            localPosMs = 0, loaded = loaded, ready = false,
        )
        assertTrue(d.play)
        assertEquals(4_000L, d.seekMs)
        assertEquals(1f, d.speed)
    }

    @Test
    fun playing_largeDrift_hardSeeks() {
        val loaded = FollowerCorrection.LoadedTrack(epoch = 1, mediaId = mediaId)
        // target = 10s; local = 2s -> drift 8s > 1.5s -> hard seek.
        val d = FollowerCorrection.decide(
            playback(epoch = 1, anchorUs = 0, anchorServerUs = 0, rate = 1f, effectiveAtUs = 0),
            serverNowUs = 10_000_000,
            localPosMs = 2_000, loaded = loaded, ready = true,
        )
        assertNull(d.loadTrack)
        assertEquals(10_000L, d.seekMs)
        assertEquals(1f, d.speed)
        assertTrue(d.play)
    }

    @Test
    fun playing_smallDrift_gentleSpeedUp_noSeek() {
        val loaded = FollowerCorrection.LoadedTrack(epoch = 1, mediaId = mediaId)
        // target = 10s; local = 9.5s -> drift +0.5s (behind) -> speed up slightly, no seek.
        val d = FollowerCorrection.decide(
            playback(epoch = 1, anchorUs = 0, anchorServerUs = 0, rate = 1f, effectiveAtUs = 0),
            serverNowUs = 10_000_000,
            localPosMs = 9_500, loaded = loaded, ready = true,
        )
        assertNull(d.seekMs)
        // 1 + 0.5*0.5 = 1.25 -> clamped to 1.03
        assertEquals(1.03f, d.speed, 0.0001f)
        assertTrue(d.play)
    }

    @Test
    fun playing_smallDriftAhead_gentleSlowDown() {
        val loaded = FollowerCorrection.LoadedTrack(epoch = 1, mediaId = mediaId)
        // target 10s; local 10.1s -> drift -0.1s (ahead) -> slow down: 1 + (-0.1*0.5)= 0.95 -> clamp 0.97
        val d = FollowerCorrection.decide(
            playback(epoch = 1, anchorUs = 0, anchorServerUs = 0, rate = 1f, effectiveAtUs = 0),
            serverNowUs = 10_000_000,
            localPosMs = 10_100, loaded = loaded, ready = true,
        )
        assertNull(d.seekMs)
        assertEquals(0.97f, d.speed, 0.0001f)
    }

    @Test
    fun playing_withinDeadband_speedExactlyOne() {
        val loaded = FollowerCorrection.LoadedTrack(epoch = 1, mediaId = mediaId)
        // target 10.00s; local 9.98s -> drift +20ms < 40ms deadband -> speed 1.0
        val d = FollowerCorrection.decide(
            playback(epoch = 1, anchorUs = 0, anchorServerUs = 0, rate = 1f, effectiveAtUs = 0),
            serverNowUs = 10_000_000,
            localPosMs = 9_980, loaded = loaded, ready = true,
        )
        assertNull(d.seekMs)
        assertEquals(1f, d.speed)
    }

    @Test
    fun playing_moderateDrift_unclampedProportional() {
        val loaded = FollowerCorrection.LoadedTrack(epoch = 1, mediaId = mediaId)
        // drift +0.05s -> 1 + 0.05*0.5 = 1.025 (inside clamp, outside deadband).
        val d = FollowerCorrection.decide(
            playback(epoch = 1, anchorUs = 0, anchorServerUs = 0, rate = 1f, effectiveAtUs = 0),
            serverNowUs = 10_000_000,
            localPosMs = 9_950, loaded = loaded, ready = true,
        )
        assertEquals(1.025f, d.speed, 0.0001f)
    }
}
