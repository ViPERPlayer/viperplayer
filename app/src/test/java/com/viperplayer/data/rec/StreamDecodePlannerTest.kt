package com.viperplayer.data.rec

import com.viperplayer.plugin.model.AudioFormat
import com.viperplayer.plugin.model.DashStream
import com.viperplayer.plugin.model.DrmConfig
import com.viperplayer.plugin.model.HlsStream
import com.viperplayer.plugin.model.PcmStream
import com.viperplayer.plugin.model.UnknownStream
import com.viperplayer.plugin.model.UrlStream
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure-JVM tests for [StreamDecodePlanner]: the per-[StreamSource]-type dispatch + DRM-skip policy that
 * drives [StreamingAudioSourceResolver] (Path B). The actual decode execution is device-only; this pins
 * the routing decisions (URL/PCM/HLS/DASH) and the crucial SKIP rules (DRM streams are covered by Path A,
 * not the background worker) without a device or plugin binder.
 */
class StreamDecodePlannerTest {

    @Test
    fun urlStreamPlansUrlDecode() {
        assertEquals(StreamDecodePlan.URL, StreamDecodePlanner.plan(UrlStream(url = "https://x/a.mp3")))
    }

    @Test
    fun pcmStreamPlansPcmDecode() {
        val pcm = PcmStream(streamId = "s", format = AudioFormat())
        assertEquals(StreamDecodePlan.PCM, StreamDecodePlanner.plan(pcm))
    }

    @Test
    fun nonDrmHlsPlansHls() {
        assertEquals(StreamDecodePlan.HLS, StreamDecodePlanner.plan(HlsStream(url = "https://x/a.m3u8")))
    }

    @Test
    fun drmHlsIsSkipped() {
        val hls = HlsStream(url = "https://x/a.m3u8", drm = DrmConfig(scheme = "widevine", licenseUrl = "https://lic"))
        assertEquals(StreamDecodePlan.SKIP_DRM, StreamDecodePlanner.plan(hls))
    }

    @Test
    fun nonDrmDashWithManifestUrlPlansDash() {
        assertEquals(
            StreamDecodePlan.DASH,
            StreamDecodePlanner.plan(DashStream(manifestUrl = "https://x/a.mpd")),
        )
    }

    @Test
    fun nonDrmDashWithInlineManifestPlansDash() {
        assertEquals(
            StreamDecodePlan.DASH,
            StreamDecodePlanner.plan(DashStream(manifest = "<MPD/>")),
        )
    }

    @Test
    fun dashWithClearKeysIsSkippedAsDrm() {
        val dash = DashStream(
            manifest = "<MPD/>",
            drm = DrmConfig(scheme = "clearkey", clearKeys = mapOf("kid" to "key")),
        )
        assertEquals(StreamDecodePlan.SKIP_DRM, StreamDecodePlanner.plan(dash))
    }

    @Test
    fun dashWithLicenseServerIsSkippedAsDrm() {
        val dash = DashStream(manifestUrl = "https://x/a.mpd", drm = DrmConfig(scheme = "widevine", licenseUrl = "https://lic"))
        assertEquals(StreamDecodePlan.SKIP_DRM, StreamDecodePlanner.plan(dash))
    }

    @Test
    fun dashWithNoManifestOrUrlIsUnsupported() {
        assertEquals(StreamDecodePlan.SKIP_UNSUPPORTED, StreamDecodePlanner.plan(DashStream()))
    }

    @Test
    fun unknownStreamIsUnsupported() {
        assertEquals(StreamDecodePlan.SKIP_UNSUPPORTED, StreamDecodePlanner.plan(UnknownStream()))
    }
}
