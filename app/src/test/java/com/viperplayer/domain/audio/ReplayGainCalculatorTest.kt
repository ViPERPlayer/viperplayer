package com.viperplayer.domain.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * JVM unit tests for the pure [ReplayGainCalculator]. Covers dB→linear conversion, tagged
 * track/album selection per mode, the untagged preamp path, smart-mode album-vs-track selection,
 * DRC clipping-prevention, post-amp ordering, clamping, and missing-tag fallbacks.
 */
class ReplayGainCalculatorTest {

    private val eps = 1e-4f

    private fun linear(db: Float): Float = 10f.pow(db / 20f)

    // --- dB -> linear -------------------------------------------------------

    @Test
    fun dbToLinear_zeroDb_isUnity() {
        assertEquals(1f, ReplayGainCalculator.dbToLinear(0f), eps)
    }

    @Test
    fun dbToLinear_minus6db_isAboutHalfAmplitude() {
        // -6.0206 dB ~ 0.5; -6 dB ~ 0.501
        assertEquals(0.5012f, ReplayGainCalculator.dbToLinear(-6f), eps)
    }

    @Test
    fun dbToLinear_plus6db_isAboutDoubleAmplitude() {
        assertEquals(1.9953f, ReplayGainCalculator.dbToLinear(6f), eps)
    }

    // --- disabled -----------------------------------------------------------

    @Test
    fun disabled_returnsUnity_evenWithGain() {
        val tags = ReplayGainTags(trackGainDb = -6f, trackPeak = 0.9f)
        val volume = ReplayGainCalculator.computeVolume(
            tags,
            ReplayGainSettings(enabled = false, preampDb = 3f)
        )
        assertEquals(1f, volume, eps)
    }

    // --- tagged track, TRACK mode ------------------------------------------

    @Test
    fun taggedTrack_trackMode_appliesTrackGainPlusPreamp() {
        // -6 dB track gain, -3 dB preamp => -9 dB, no peak limiting (DRC off).
        val tags = ReplayGainTags(trackGainDb = -6f, trackPeak = 0.5f)
        val volume = ReplayGainCalculator.computeVolume(
            tags,
            ReplayGainSettings(mode = ReplayGainMode.TRACK, preampDb = -3f)
        )
        assertEquals(linear(-9f), volume, eps)
    }

    @Test
    fun taggedTrack_trackMode_ignoresAlbumGainWhenTrackPresent() {
        val tags = ReplayGainTags(trackGainDb = -6f, albumGainDb = -3f)
        val volume = ReplayGainCalculator.computeVolume(
            tags,
            ReplayGainSettings(mode = ReplayGainMode.TRACK)
        )
        assertEquals(linear(-6f), volume, eps)
    }

    // --- tagged track, ALBUM mode ------------------------------------------

    @Test
    fun taggedTrack_albumMode_prefersAlbumGain() {
        val tags = ReplayGainTags(trackGainDb = -6f, albumGainDb = -3f)
        val volume = ReplayGainCalculator.computeVolume(
            tags,
            ReplayGainSettings(mode = ReplayGainMode.ALBUM)
        )
        assertEquals(linear(-3f), volume, eps)
    }

    @Test
    fun albumMode_fallsBackToTrackGainWhenAlbumMissing() {
        val tags = ReplayGainTags(trackGainDb = -6f, albumGainDb = null)
        val volume = ReplayGainCalculator.computeVolume(
            tags,
            ReplayGainSettings(mode = ReplayGainMode.ALBUM)
        )
        assertEquals(linear(-6f), volume, eps)
    }

    @Test
    fun trackMode_fallsBackToAlbumGainWhenTrackMissing() {
        val tags = ReplayGainTags(trackGainDb = null, albumGainDb = -4f)
        val volume = ReplayGainCalculator.computeVolume(
            tags,
            ReplayGainSettings(mode = ReplayGainMode.TRACK)
        )
        assertEquals(linear(-4f), volume, eps)
    }

    // --- untagged preamp ----------------------------------------------------

    @Test
    fun untaggedTrack_usesUntaggedPreamp_notTaggedPreamp() {
        val tags = ReplayGainTags() // no gains at all
        assertTrue(tags.isUntagged)
        val volume = ReplayGainCalculator.computeVolume(
            tags,
            ReplayGainSettings(preampDb = 5f, untaggedPreampDb = -4f)
        )
        // Tagged preamp (+5) must NOT apply; only the untagged preamp (-4) does.
        assertEquals(linear(-4f), volume, eps)
    }

    @Test
    fun untaggedTrack_zeroUntaggedPreamp_isUnity() {
        val volume = ReplayGainCalculator.computeVolume(
            ReplayGainTags(),
            ReplayGainSettings(preampDb = 3f, untaggedPreampDb = 0f)
        )
        assertEquals(1f, volume, eps)
    }

    @Test
    fun taggedFlag_isFalseWhenAnyGainPresent() {
        assertFalse(ReplayGainTags(trackGainDb = -6f).isUntagged)
        assertFalse(ReplayGainTags(albumGainDb = -6f).isUntagged)
    }

    // --- smart mode ---------------------------------------------------------

    @Test
    fun smartMode_sequentialAlbum_usesAlbumGain() {
        val tags = ReplayGainTags(trackGainDb = -6f, albumGainDb = -3f)
        val volume = ReplayGainCalculator.computeVolume(
            tags,
            ReplayGainSettings(mode = ReplayGainMode.SMART),
            sequentialAlbum = true
        )
        assertEquals(linear(-3f), volume, eps)
    }

    @Test
    fun smartMode_shuffle_usesTrackGain() {
        val tags = ReplayGainTags(trackGainDb = -6f, albumGainDb = -3f)
        val volume = ReplayGainCalculator.computeVolume(
            tags,
            ReplayGainSettings(mode = ReplayGainMode.SMART),
            sequentialAlbum = false
        )
        assertEquals(linear(-6f), volume, eps)
    }

    @Test
    fun pickSmartSource_reflectsSequentialFlag() {
        assertTrue(ReplayGainCalculator.pickSmartSource(sequentialAlbum = true))
        assertFalse(ReplayGainCalculator.pickSmartSource(sequentialAlbum = false))
    }

    @Test
    fun smartMode_sequentialAlbum_fallsBackToTrackWhenAlbumMissing() {
        val tags = ReplayGainTags(trackGainDb = -6f, albumGainDb = null)
        val volume = ReplayGainCalculator.computeVolume(
            tags,
            ReplayGainSettings(mode = ReplayGainMode.SMART),
            sequentialAlbum = true
        )
        assertEquals(linear(-6f), volume, eps)
    }

    // --- DRC / clipping prevention -----------------------------------------

    @Test
    fun drc_largeBoostWithHighPeak_isLimitedToPreventClipping() {
        // A track that already peaks over full scale (peak 1.25, common after lossy re-encoding)
        // with a +6 dB boost would clip hard. DRC caps gain at 1/1.25 = 0.8, which is < 1 so it is
        // observable (not masked by the [0,1] clamp).
        val tags = ReplayGainTags(trackGainDb = 6f, trackPeak = 1.25f)
        val volume = ReplayGainCalculator.computeVolume(
            tags,
            ReplayGainSettings(mode = ReplayGainMode.TRACK, drcEnabled = true)
        )
        assertEquals(1f / 1.25f, volume, eps)
        // Guarantee: peak * gain <= 1.0 (no clipping).
        assertTrue(1.25f * volume <= 1f + eps)
    }

    @Test
    fun drc_capExceedingUnity_isJustClampedToOne() {
        // peak 0.9 -> DRC cap 1/0.9 = 1.111, above unity, so the final volume simply clamps to 1.0.
        val tags = ReplayGainTags(trackGainDb = 12f, trackPeak = 0.9f)
        val volume = ReplayGainCalculator.computeVolume(
            tags,
            ReplayGainSettings(mode = ReplayGainMode.TRACK, drcEnabled = true)
        )
        assertEquals(1f, volume, eps)
    }

    @Test
    fun drc_off_allowsGainThatWouldClip_untilClamp() {
        // With DRC off, a +6 dB boost (1.995x) is allowed but the final volume clamps at 1.0.
        val tags = ReplayGainTags(trackGainDb = 6f, trackPeak = 0.9f)
        val volume = ReplayGainCalculator.computeVolume(
            tags,
            ReplayGainSettings(mode = ReplayGainMode.TRACK, drcEnabled = false)
        )
        assertEquals(1f, volume, eps) // clamped at unity
    }

    @Test
    fun drc_doesNotReduceGainWhenPeakLeavesHeadroom() {
        // -6 dB with peak 0.5: gain 0.501, peak*gain ~0.25 < 1, so DRC leaves it untouched.
        val tags = ReplayGainTags(trackGainDb = -6f, trackPeak = 0.5f)
        val volume = ReplayGainCalculator.computeVolume(
            tags,
            ReplayGainSettings(mode = ReplayGainMode.TRACK, drcEnabled = true)
        )
        assertEquals(linear(-6f), volume, eps)
    }

    @Test
    fun drc_usesAlbumPeakInAlbumMode() {
        // Album mode must clip-guard on the ALBUM peak (1.6), not the track peak. DRC caps gain at
        // 1/1.6 = 0.625; using the track peak (1.0) would wrongly yield 1.0.
        val tags = ReplayGainTags(
            trackGainDb = 0f, trackPeak = 1.0f,
            albumGainDb = 12f, albumPeak = 1.6f
        )
        val volume = ReplayGainCalculator.computeVolume(
            tags,
            ReplayGainSettings(mode = ReplayGainMode.ALBUM, drcEnabled = true)
        )
        assertEquals(1f / 1.6f, volume, eps)
    }

    // --- post-amp -----------------------------------------------------------

    @Test
    fun postAmp_appliedAfterReplayGain() {
        // -6 dB RG then -3 dB post-amp => -9 dB linear.
        val tags = ReplayGainTags(trackGainDb = -6f, trackPeak = 0.5f)
        val volume = ReplayGainCalculator.computeVolume(
            tags,
            ReplayGainSettings(mode = ReplayGainMode.TRACK, postAmpDb = -3f)
        )
        assertEquals(linear(-9f), volume, eps)
    }

    @Test
    fun postAmp_appliedAfterDrcLimiting() {
        // DRC caps at 1/0.9, then post-amp -6 dB scales it down further.
        val tags = ReplayGainTags(trackGainDb = 12f, trackPeak = 0.9f)
        val volume = ReplayGainCalculator.computeVolume(
            tags,
            ReplayGainSettings(mode = ReplayGainMode.TRACK, drcEnabled = true, postAmpDb = -6f)
        )
        assertEquals((1f / 0.9f) * linear(-6f), volume, eps)
    }

    @Test
    fun postAmp_appliesToUntaggedTracks() {
        val volume = ReplayGainCalculator.computeVolume(
            ReplayGainTags(),
            ReplayGainSettings(untaggedPreampDb = -3f, postAmpDb = -3f)
        )
        assertEquals(linear(-6f), volume, eps)
    }

    // --- clamping -----------------------------------------------------------

    @Test
    fun result_clampedToOne() {
        val tags = ReplayGainTags(trackGainDb = 24f)
        val volume = ReplayGainCalculator.computeVolume(
            tags,
            ReplayGainSettings(mode = ReplayGainMode.TRACK, postAmpDb = 12f)
        )
        assertEquals(1f, volume, eps)
    }

    @Test
    fun result_clampedToZeroFloor_isNonNegative() {
        val tags = ReplayGainTags(trackGainDb = -120f)
        val volume = ReplayGainCalculator.computeVolume(
            tags,
            ReplayGainSettings(mode = ReplayGainMode.TRACK, preampDb = -12f, postAmpDb = -12f)
        )
        assertTrue(volume >= 0f)
    }
}
