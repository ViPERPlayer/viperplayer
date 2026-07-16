package com.viperplayer.domain.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for the pure [PlaybackTuning] decision helpers: gapless-vs-crossfade selection, the
 * crossfade fade curve, ReplayGain×crossfade volume composition, supported-format decisions,
 * buffer/prefetch selection, and the audio-focus / becoming-noisy policy.
 */
class PlaybackTuningTest {

    private val eps = 1e-4f

    // --- resolveTransition: gapless vs crossfade ---------------------------

    @Test
    fun resolveTransition_crossfadeOff_isGapless() {
        val policy = PlaybackTuning.resolveTransition(crossfadeSeconds = 0)
        assertEquals(TrackTransition.GAPLESS, policy.transition)
        assertEquals(0L, policy.crossfadeMs)
    }

    @Test
    fun resolveTransition_crossfadeOn_isCrossfadeWithDuration() {
        val policy = PlaybackTuning.resolveTransition(crossfadeSeconds = 6)
        assertEquals(TrackTransition.CROSSFADE, policy.transition)
        assertEquals(6_000L, policy.crossfadeMs)
    }

    @Test
    fun resolveTransition_negativeCrossfade_treatedAsOff() {
        val policy = PlaybackTuning.resolveTransition(crossfadeSeconds = -5)
        assertEquals(TrackTransition.GAPLESS, policy.transition)
        assertEquals(0L, policy.crossfadeMs)
    }

    // --- crossfadeFactor ----------------------------------------------------

    @Test
    fun crossfadeFactor_off_isUnity() {
        assertEquals(1f, PlaybackTuning.crossfadeFactor(1_000L, 200_000L, crossfadeMs = 0L), eps)
    }

    @Test
    fun crossfadeFactor_unknownDuration_isUnity() {
        assertEquals(1f, PlaybackTuning.crossfadeFactor(1_000L, durationMs = 0L, crossfadeMs = 5_000L), eps)
        assertEquals(1f, PlaybackTuning.crossfadeFactor(1_000L, durationMs = -1L, crossfadeMs = 5_000L), eps)
    }

    @Test
    fun crossfadeFactor_middleOfTrack_isUnity() {
        // 100s into a 200s track, 5s fade: neither near the start nor the end.
        assertEquals(1f, PlaybackTuning.crossfadeFactor(100_000L, 200_000L, 5_000L), eps)
    }

    @Test
    fun crossfadeFactor_fadeIn_rampsFromFloor() {
        // At position 0 of a track, fade-in floor keeps it audible (>= 0.05).
        assertEquals(0.05f, PlaybackTuning.crossfadeFactor(0L, 200_000L, 5_000L), eps)
        // Halfway through the 5s fade-in window.
        assertEquals(0.5f, PlaybackTuning.crossfadeFactor(2_500L, 200_000L, 5_000L), eps)
    }

    @Test
    fun crossfadeFactor_fadeOut_rampsToZero() {
        // 1ms remaining => ~0, and mid fade-out window => 0.5.
        val dur = 200_000L
        assertEquals(0.5f, PlaybackTuning.crossfadeFactor(dur - 2_500L, dur, 5_000L), eps)
        // Exactly at the end boundary (remaining == fade length) is treated as fade-out start = 1.0.
        assertEquals(1f, PlaybackTuning.crossfadeFactor(dur - 5_000L, dur, 5_000L), eps)
    }

    @Test
    fun crossfadeFactor_paused_isUnity_evenInsideFadeWindow() {
        // A paused track inside the fade-in window is not attenuated (would otherwise be 0.5) — the
        // isPlaying guard matches the original loop so pausing can't leave the volume stuck low.
        assertEquals(1f, PlaybackTuning.crossfadeFactor(2_500L, 200_000L, 5_000L, isPlaying = false), eps)
        // Same for the fade-out window.
        assertEquals(
            1f,
            PlaybackTuning.crossfadeFactor(197_500L, 200_000L, 5_000L, isPlaying = false),
            eps,
        )
    }

    // --- effectiveVolume: ReplayGain x crossfade --------------------------

    @Test
    fun effectiveVolume_composesBaseAndFade() {
        // ReplayGain attenuated to 0.6, mid fade at 0.5 => 0.3.
        assertEquals(0.3f, PlaybackTuning.effectiveVolume(0.6f, 0.5f), eps)
    }

    @Test
    fun effectiveVolume_noFade_passesBaseThrough() {
        assertEquals(0.6f, PlaybackTuning.effectiveVolume(0.6f, 1f), eps)
    }

    @Test
    fun effectiveVolume_clampsToRange() {
        assertEquals(1f, PlaybackTuning.effectiveVolume(2f, 1f), eps)
        assertEquals(0f, PlaybackTuning.effectiveVolume(-1f, 1f), eps)
    }

    // --- isSupportedFormat --------------------------------------------------

    @Test
    fun isSupportedFormat_nullOrBlank_isOptimisticallyTrue() {
        assertTrue(PlaybackTuning.isSupportedFormat(null))
        assertTrue(PlaybackTuning.isSupportedFormat(""))
        assertTrue(PlaybackTuning.isSupportedFormat("   "))
    }

    @Test
    fun isSupportedFormat_commonAudio_isTrue() {
        assertTrue(PlaybackTuning.isSupportedFormat("audio/mpeg"))
        assertTrue(PlaybackTuning.isSupportedFormat("audio/flac"))
        assertTrue(PlaybackTuning.isSupportedFormat("audio/opus"))
        assertTrue(PlaybackTuning.isSupportedFormat("audio/mp4"))       // AAC / ALAC
        assertTrue(PlaybackTuning.isSupportedFormat("audio/ogg"))
        assertTrue(PlaybackTuning.isSupportedFormat("audio/wav"))
        assertTrue(PlaybackTuning.isSupportedFormat("application/dash+xml"))
        assertTrue(PlaybackTuning.isSupportedFormat("application/x-mpegurl"))
    }

    @Test
    fun isSupportedFormat_caseInsensitive_andIgnoresCodecsParam() {
        assertTrue(PlaybackTuning.isSupportedFormat("AUDIO/FLAC"))
        assertTrue(PlaybackTuning.isSupportedFormat("audio/mp4; codecs=\"mp4a.40.2\""))
    }

    @Test
    fun isSupportedFormat_knownUnsupported_isFalse() {
        assertFalse(PlaybackTuning.isSupportedFormat("audio/x-ape"))
        assertFalse(PlaybackTuning.isSupportedFormat("audio/x-wavpack"))
        assertFalse(PlaybackTuning.isSupportedFormat("audio/x-musepack"))
        assertFalse(PlaybackTuning.isSupportedFormat("audio/dsd"))
    }

    @Test
    fun isSupportedFormat_unknownType_isOptimisticallyTrue() {
        // Not in either list: let ExoPlayer sniff the container rather than hard-rejecting.
        assertTrue(PlaybackTuning.isSupportedFormat("audio/some-future-codec"))
    }

    // --- selectBuffer -------------------------------------------------------

    @Test
    fun selectBuffer_metered_buffersLessThanUnmetered() {
        val metered = PlaybackTuning.selectBuffer(NetworkType.METERED)
        val unmetered = PlaybackTuning.selectBuffer(NetworkType.UNMETERED)
        assertTrue(metered.maxBufferMs < unmetered.maxBufferMs)
        assertTrue(metered.minBufferMs < unmetered.minBufferMs)
    }

    @Test
    fun selectBuffer_unknown_isBetweenMeteredAndUnmetered() {
        val metered = PlaybackTuning.selectBuffer(NetworkType.METERED)
        val unknown = PlaybackTuning.selectBuffer(NetworkType.UNKNOWN)
        val unmetered = PlaybackTuning.selectBuffer(NetworkType.UNMETERED)
        assertTrue(unknown.maxBufferMs in metered.maxBufferMs..unmetered.maxBufferMs)
    }

    @Test
    fun selectBuffer_allProfiles_keepExoPlayerInvariants() {
        for (type in NetworkType.values()) {
            val p = PlaybackTuning.selectBuffer(type)
            assertTrue("minBuffer <= maxBuffer for $type", p.minBufferMs <= p.maxBufferMs)
            assertTrue("bufferForPlayback <= minBuffer for $type", p.bufferForPlaybackMs <= p.minBufferMs)
            assertTrue(
                "bufferForPlaybackAfterRebuffer <= minBuffer for $type",
                p.bufferForPlaybackAfterRebufferMs <= p.minBufferMs,
            )
            assertTrue("positive buffers for $type", p.bufferForPlaybackMs > 0)
        }
    }

    // --- focusReaction ------------------------------------------------------

    @Test
    fun focusReaction_gain_plays() {
        assertEquals(PlaybackTuning.FocusReaction.PLAY, PlaybackTuning.focusReaction(1))
    }

    @Test
    fun focusReaction_permanentLoss_pausesPermanently() {
        assertEquals(PlaybackTuning.FocusReaction.PAUSE_PERMANENT, PlaybackTuning.focusReaction(-1))
    }

    @Test
    fun focusReaction_transientLoss_pausesTransiently() {
        assertEquals(PlaybackTuning.FocusReaction.PAUSE_TRANSIENT, PlaybackTuning.focusReaction(-2))
    }

    @Test
    fun focusReaction_duckableLoss_ducksByDefault() {
        assertEquals(PlaybackTuning.FocusReaction.DUCK, PlaybackTuning.focusReaction(-3))
    }

    @Test
    fun focusReaction_duckableLoss_pausesWhenUserOptsOutOfDucking() {
        assertEquals(
            PlaybackTuning.FocusReaction.PAUSE_TRANSIENT,
            PlaybackTuning.focusReaction(-3, willPauseWhenDucked = true),
        )
    }

    @Test
    fun focusReaction_unknownCode_plays() {
        assertEquals(PlaybackTuning.FocusReaction.PLAY, PlaybackTuning.focusReaction(999))
    }

    // --- shouldPauseOnBecomingNoisy ----------------------------------------

    @Test
    fun becomingNoisy_pausesOnlyWhilePlaying() {
        assertTrue(PlaybackTuning.shouldPauseOnBecomingNoisy(isPlaying = true))
        assertFalse(PlaybackTuning.shouldPauseOnBecomingNoisy(isPlaying = false))
    }
}
