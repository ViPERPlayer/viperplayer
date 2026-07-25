package com.viperplayer.data.player.cast

import com.viperplayer.plugin.model.AudioFormat
import com.viperplayer.plugin.model.DashStream
import com.viperplayer.plugin.model.DrmConfig
import com.viperplayer.plugin.model.HlsStream
import com.viperplayer.plugin.model.PcmStream
import com.viperplayer.plugin.model.UnknownStream
import com.viperplayer.plugin.model.UrlStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure [CastEligibility] decider: a track is castable to the default Cast
 * receiver only when it resolves to a plain progressive http(s) [UrlStream] — never a local file,
 * DRM/DASH/HLS, or raw PCM.
 */
class CastEligibilityTest {

    @Test
    fun `progressive https url is castable with its mime`() {
        val source = UrlStream(url = "https://cdn.example.com/track.mp3", mimeType = "audio/mpeg")
        val result = CastEligibility.evaluate(source, isDownloadedLocalFile = false)
        assertEquals(
            CastEligibility.Result.Castable("https://cdn.example.com/track.mp3", "audio/mpeg"),
            result,
        )
    }

    @Test
    fun `progressive http url is castable`() {
        val source = UrlStream(url = "http://example.com/a.flac", mimeType = null)
        val result = CastEligibility.evaluate(source, isDownloadedLocalFile = false)
        assertTrue(result is CastEligibility.Result.Castable)
        assertEquals("http://example.com/a.flac", (result as CastEligibility.Result.Castable).url)
        assertEquals(null, result.mimeType)
    }

    @Test
    fun `content uri url is not castable`() {
        val source = UrlStream(url = "content://media/audio/42")
        val result = CastEligibility.evaluate(source, isDownloadedLocalFile = false)
        assertEquals(
            CastEligibility.Result.NotCastable(CastEligibility.Reason.NON_HTTP_URL),
            result,
        )
    }

    @Test
    fun `file uri url is not castable`() {
        val source = UrlStream(url = "file:///storage/emulated/0/Music/x.mp3")
        val result = CastEligibility.evaluate(source, isDownloadedLocalFile = false)
        assertEquals(
            CastEligibility.Result.NotCastable(CastEligibility.Reason.NON_HTTP_URL),
            result,
        )
    }

    @Test
    fun `downloaded local file is never castable even with an http url`() {
        val source = UrlStream(url = "https://cdn.example.com/track.mp3")
        val result = CastEligibility.evaluate(source, isDownloadedLocalFile = true)
        assertEquals(
            CastEligibility.Result.NotCastable(CastEligibility.Reason.LOCAL_FILE),
            result,
        )
    }

    @Test
    fun `dash stream without drm is not castable`() {
        val source = DashStream(manifestUrl = "https://example.com/m.mpd")
        val result = CastEligibility.evaluate(source, isDownloadedLocalFile = false)
        assertEquals(CastEligibility.Result.NotCastable(CastEligibility.Reason.DASH), result)
    }

    @Test
    fun `dash stream with clearkey drm is reported as drm`() {
        val source = DashStream(
            manifest = "<MPD/>",
            drm = DrmConfig(scheme = "clearkey", clearKeys = mapOf("aa" to "bb")),
        )
        val result = CastEligibility.evaluate(source, isDownloadedLocalFile = false)
        assertEquals(
            CastEligibility.Result.NotCastable(CastEligibility.Reason.DRM_PROTECTED),
            result,
        )
    }

    @Test
    fun `hls stream without drm is not castable`() {
        val source = HlsStream(url = "https://example.com/p.m3u8")
        val result = CastEligibility.evaluate(source, isDownloadedLocalFile = false)
        assertEquals(CastEligibility.Result.NotCastable(CastEligibility.Reason.HLS), result)
    }

    @Test
    fun `hls stream with widevine drm is reported as drm`() {
        val source = HlsStream(
            url = "https://example.com/p.m3u8",
            drm = DrmConfig(scheme = "widevine", licenseUrl = "https://lic.example.com"),
        )
        val result = CastEligibility.evaluate(source, isDownloadedLocalFile = false)
        assertEquals(
            CastEligibility.Result.NotCastable(CastEligibility.Reason.DRM_PROTECTED),
            result,
        )
    }

    @Test
    fun `pcm stream is not castable`() {
        val source = PcmStream(streamId = "s1", format = AudioFormat())
        val result = CastEligibility.evaluate(source, isDownloadedLocalFile = false)
        assertEquals(CastEligibility.Result.NotCastable(CastEligibility.Reason.PCM), result)
    }

    @Test
    fun `unknown stream is not castable`() {
        val result = CastEligibility.evaluate(UnknownStream(), isDownloadedLocalFile = false)
        assertEquals(CastEligibility.Result.NotCastable(CastEligibility.Reason.UNKNOWN), result)
    }

    @Test
    fun `null source is not castable`() {
        val result = CastEligibility.evaluate(null, isDownloadedLocalFile = false)
        assertEquals(CastEligibility.Result.NotCastable(CastEligibility.Reason.UNKNOWN), result)
    }

    @Test
    fun `isHttpUrl accepts http and https case-insensitively and trims`() {
        assertTrue(CastEligibility.isHttpUrl("https://x"))
        assertTrue(CastEligibility.isHttpUrl("HTTP://x"))
        assertTrue(CastEligibility.isHttpUrl("  https://x  "))
        assertFalse(CastEligibility.isHttpUrl("content://x"))
        assertFalse(CastEligibility.isHttpUrl("ftp://x"))
        assertFalse(CastEligibility.isHttpUrl(""))
    }
}
