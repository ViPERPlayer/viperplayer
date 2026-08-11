package com.viperplayer.data.player

import com.viperplayer.domain.model.ViperEffectsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

/**
 * The applier decides which of ~60 native setters to call for a given state change. Sending too
 * little leaves the engine stale; sending too much re-runs coefficient math for effects the user
 * never touched, which several native setters pay for by resetting filter state.
 *
 * [RecordingViperEngine] captures the calls so each test can assert on exactly that decision.
 */
class ViperEffectsStateApplierTest {

    private val engine = RecordingViperEngine()
    private val applier = ViperEffectsStateApplier(engine)

    @Test
    fun `sends the whole configuration when there is no previous state`() {
        applier.apply(previous = null, next = ViperEffectsState())

        // Every effect has to be told something on a cold start, or the engine keeps whatever the
        // last process left behind.
        assertTrue(engine.calls.size > 40)
        assertTrue(engine.called("setSpectrumExtensionEnabled"))
        assertTrue(engine.called("setReverberationDry"))
        assertTrue(engine.called("setFetCompressorNoClip"))
        assertTrue(engine.called("setPlaybackGainMaxGain"))
        assertTrue(engine.called("setDifferentialSurroundDelay"))
    }

    @Test
    fun `sends nothing when the state has not changed`() {
        val state = ViperEffectsState()
        applier.apply(previous = null, next = state)
        engine.calls.clear()

        applier.apply(previous = state, next = state)

        assertEquals(emptyList<String>(), engine.calls)
    }

    @Test
    fun `sends only the effect that changed`() {
        val before = ViperEffectsState()
        val after = before.copy(
            reverberation = before.reverberation.copy(roomSize = before.reverberation.roomSize + 7)
        )
        applier.apply(previous = null, next = before)
        engine.calls.clear()

        applier.apply(previous = before, next = after)

        assertEquals(listOf("setReverberationRoomSize"), engine.calls)
    }

    // REGRESSION: `previous = null` is how a caller re-applies everything after the engine has been
    // reset underneath it. onReset() used to express that by passing its own last-sent state into a
    // function that diffed against that same state, so every comparison was false and the
    // re-application silently pushed nothing.
    @Test
    fun `re-applies everything when previous is null even if the state is unchanged`() {
        val state = ViperEffectsState()
        applier.apply(previous = null, next = state)
        val coldStartCalls = engine.calls.toList()
        engine.calls.clear()

        applier.apply(previous = null, next = state)

        assertEquals(coldStartCalls, engine.calls)
    }

    // Several of these used to be sent unconditionally on every state emission, contradicting the
    // "only changed values" contract the class is built around. Playback gain and differential
    // surround were the worst: four and two native calls per emission, each recomputing gain
    // coefficients, while the user was dragging an unrelated slider.
    @Test
    fun `does not re-send playback gain or differential surround when they have not changed`() {
        val before = ViperEffectsState()
        val after = before.copy(
            tubeSimulator = before.tubeSimulator.copy(enabled = !before.tubeSimulator.enabled)
        )
        applier.apply(previous = null, next = before)
        engine.calls.clear()

        applier.apply(previous = before, next = after)

        assertEquals(listOf("setTubeSimulatorEnabled"), engine.calls)
    }

    @Test
    fun `sends the equalizer band count and gains in a single call`() {
        val before = ViperEffectsState()
        val after = before.copy(
            iirEqualizer = before.iirEqualizer.copy(
                bandGains = before.iirEqualizer.bandGains.mapIndexed { index, gain ->
                    if (index == 0) gain + 6f else gain
                }
            )
        )
        applier.apply(previous = null, next = before)
        engine.calls.clear()

        applier.apply(previous = before, next = after)

        // One call, not a band-count call followed by N per-band calls — the audio thread must never
        // see a half-applied curve.
        assertEquals(listOf("setIirEqualizerBands"), engine.calls)
    }

    @Test
    fun `re-sends output gain when only the pan changed`() {
        val before = ViperEffectsState()
        val after = before.copy(
            masterLimiter = before.masterLimiter.copy(
                outputPan = before.masterLimiter.outputPan + 3
            )
        )
        applier.apply(previous = null, next = before)
        engine.calls.clear()

        applier.apply(previous = before, next = after)

        // Pan and gain are combined into one L/R pair, so a pan move has to re-send the gain.
        assertEquals(listOf("setMasterLimiterOutputGain"), engine.calls)
    }
}

/** Records the name of every engine call, in order. */
private class RecordingViperEngine : ViperEngine {

    val calls = mutableListOf<String>()

    fun called(name: String): Boolean = name in calls

    private fun record(name: String) {
        calls += name
    }

    override fun setSamplingRate(samplingRate: Int) = record("setSamplingRate")
    override fun setMasterLimiterOutputGain(gainL: Float, gainR: Float) = record("setMasterLimiterOutputGain")
    override fun setMasterLimiterThresholdLimit(threshold: Float) = record("setMasterLimiterThresholdLimit")
    override fun setSpectrumExtensionEnabled(enabled: Boolean) = record("setSpectrumExtensionEnabled")
    override fun setSpectrumExtensionStrength(strength: Int) = record("setSpectrumExtensionStrength")
    override fun setFieldSurroundEnabled(enabled: Boolean) = record("setFieldSurroundEnabled")
    override fun setFieldSurroundStrength(strength: Int) = record("setFieldSurroundStrength")
    override fun setFieldSurroundMidImageStrength(strength: Int) = record("setFieldSurroundMidImageStrength")
    override fun setDifferentialSurroundEnabled(enabled: Boolean) = record("setDifferentialSurroundEnabled")
    override fun setDifferentialSurroundDelay(delay: Int) = record("setDifferentialSurroundDelay")
    override fun setDynamicSystemEnabled(enabled: Boolean) = record("setDynamicSystemEnabled")
    override fun setDynamicSystemDeviceType(deviceTypeOrdinal: Int) = record("setDynamicSystemDeviceType")
    override fun setDynamicSystemBassStrength(strength: Int) = record("setDynamicSystemBassStrength")
    override fun setTubeSimulatorEnabled(enabled: Boolean) = record("setTubeSimulatorEnabled")
    override fun setViperBassEnabled(enabled: Boolean) = record("setViperBassEnabled")
    override fun setViperBassMode(mode: Int) = record("setViperBassMode")
    override fun setViperBassFrequency(frequency: Int) = record("setViperBassFrequency")
    override fun setViperBassGain(gain: Int) = record("setViperBassGain")
    override fun setViperClarityEnabled(enabled: Boolean) = record("setViperClarityEnabled")
    override fun setViperClarityMode(mode: Int) = record("setViperClarityMode")
    override fun setViperClarityGain(gain: Int) = record("setViperClarityGain")
    override fun setAuditorySystemProtectionEnabled(enabled: Boolean) = record("setAuditorySystemProtectionEnabled")
    override fun setAuditorySystemProtectionLevel(level: Int) = record("setAuditorySystemProtectionLevel")
    override fun setAnalogXEnabled(enabled: Boolean) = record("setAnalogXEnabled")
    override fun setAnalogXLevel(level: Int) = record("setAnalogXLevel")
    override fun setSpeakerOptimizationEnabled(enabled: Boolean) = record("setSpeakerOptimizationEnabled")
    override fun setIirEqualizerEnabled(enabled: Boolean) = record("setIirEqualizerEnabled")
    override fun setIirEqualizerBandLevel(bandIndex: Int, level: Float) = record("setIirEqualizerBandLevel")
    override fun setIirEqualizerBands(bandCountOrdinal: Int, gains: FloatArray) = record("setIirEqualizerBands")
    override fun setIirEqualizerBandCount(bandCountOrdinal: Int) = record("setIirEqualizerBandCount")
    override fun setViperDdcEnabled(enabled: Boolean) = record("setViperDdcEnabled")
    override fun viperDdcClearCoeffs() = record("viperDdcClearCoeffs")
    override fun viperDdcAddCoeffs(samplingRate: Int, coeffs: FloatArray) = record("viperDdcAddCoeffs")
    override fun setConvolverEnabled(enabled: Boolean) = record("setConvolverEnabled")
    override fun setConvolverImpulseResponse(channels: Int, kernel: FloatArray) = record("setConvolverImpulseResponse")
    override fun setConvolverCrossChannel(crossChannel: Int) = record("setConvolverCrossChannel")
    override fun setHeadphoneSurroundEnabled(enabled: Boolean) = record("setHeadphoneSurroundEnabled")
    override fun setHeadphoneSurroundLevel(level: Int) = record("setHeadphoneSurroundLevel")
    override fun setReverberationEnabled(enabled: Boolean) = record("setReverberationEnabled")
    override fun setReverberationRoomSize(value: Int) = record("setReverberationRoomSize")
    override fun setReverberationWidth(value: Int) = record("setReverberationWidth")
    override fun setReverberationDamp(value: Int) = record("setReverberationDamp")
    override fun setReverberationWet(value: Int) = record("setReverberationWet")
    override fun setReverberationDry(value: Int) = record("setReverberationDry")
    override fun setFetCompressorEnabled(enabled: Boolean) = record("setFetCompressorEnabled")
    override fun setFetCompressorThreshold(value: Int) = record("setFetCompressorThreshold")
    override fun setFetCompressorRatio(value: Int) = record("setFetCompressorRatio")
    override fun setFetCompressorKnee(value: Int) = record("setFetCompressorKnee")
    override fun setFetCompressorAutoKnee(enabled: Boolean) = record("setFetCompressorAutoKnee")
    override fun setFetCompressorGain(value: Int) = record("setFetCompressorGain")
    override fun setFetCompressorAutoGain(enabled: Boolean) = record("setFetCompressorAutoGain")
    override fun setFetCompressorAttack(value: Int) = record("setFetCompressorAttack")
    override fun setFetCompressorAutoAttack(enabled: Boolean) = record("setFetCompressorAutoAttack")
    override fun setFetCompressorRelease(value: Int) = record("setFetCompressorRelease")
    override fun setFetCompressorAutoRelease(enabled: Boolean) = record("setFetCompressorAutoRelease")
    override fun setFetCompressorKneeMulti(value: Int) = record("setFetCompressorKneeMulti")
    override fun setFetCompressorMaxAttack(value: Int) = record("setFetCompressorMaxAttack")
    override fun setFetCompressorMaxRelease(value: Int) = record("setFetCompressorMaxRelease")
    override fun setFetCompressorCrest(value: Int) = record("setFetCompressorCrest")
    override fun setFetCompressorAdapt(value: Int) = record("setFetCompressorAdapt")
    override fun setFetCompressorNoClip(enabled: Boolean) = record("setFetCompressorNoClip")
    override fun setPlaybackGainEnabled(enabled: Boolean) = record("setPlaybackGainEnabled")
    override fun setPlaybackGainStrength(strength: Int) = record("setPlaybackGainStrength")
    override fun setPlaybackGainMaxGain(maxGain: Int) = record("setPlaybackGainMaxGain")
    override fun setPlaybackGainOutputThreshold(threshold: Float) = record("setPlaybackGainOutputThreshold")
    override fun process(buffer: ByteBuffer, offset: Int, size: Int) = record("process")
    override fun reset() = record("reset")
}
