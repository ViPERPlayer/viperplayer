package com.viperplayer.domain.audio

import com.viperplayer.domain.audio.BluetoothCodecInfo.Companion.LDAC_QUALITY_ABR
import com.viperplayer.domain.audio.BluetoothCodecInfo.Companion.LDAC_QUALITY_HIGH
import com.viperplayer.domain.audio.BluetoothCodecInfo.Companion.LDAC_QUALITY_LOW
import com.viperplayer.domain.audio.BluetoothCodecInfo.Companion.LDAC_QUALITY_MID
import com.viperplayer.domain.audio.BluetoothCodecInfo.Companion.SOURCE_CODEC_TYPE_AAC
import com.viperplayer.domain.audio.BluetoothCodecInfo.Companion.SOURCE_CODEC_TYPE_APTX
import com.viperplayer.domain.audio.BluetoothCodecInfo.Companion.SOURCE_CODEC_TYPE_APTX_ADAPTIVE
import com.viperplayer.domain.audio.BluetoothCodecInfo.Companion.SOURCE_CODEC_TYPE_APTX_HD
import com.viperplayer.domain.audio.BluetoothCodecInfo.Companion.SOURCE_CODEC_TYPE_INVALID
import com.viperplayer.domain.audio.BluetoothCodecInfo.Companion.SOURCE_CODEC_TYPE_LC3
import com.viperplayer.domain.audio.BluetoothCodecInfo.Companion.SOURCE_CODEC_TYPE_LDAC
import com.viperplayer.domain.audio.BluetoothCodecInfo.Companion.SOURCE_CODEC_TYPE_LHDC_V2
import com.viperplayer.domain.audio.BluetoothCodecInfo.Companion.SOURCE_CODEC_TYPE_LHDC_V3
import com.viperplayer.domain.audio.BluetoothCodecInfo.Companion.SOURCE_CODEC_TYPE_LHDC_V5
import com.viperplayer.domain.audio.BluetoothCodecInfo.Companion.SOURCE_CODEC_TYPE_OPUS
import com.viperplayer.domain.audio.BluetoothCodecInfo.Companion.SOURCE_CODEC_TYPE_SBC
import com.viperplayer.domain.audio.BluetoothCodecInfo.Companion.codecTypeToName
import com.viperplayer.domain.audio.BluetoothCodecInfo.Companion.from
import com.viperplayer.domain.audio.BluetoothCodecInfo.Companion.ldacBitrateLabel
import com.viperplayer.domain.audio.BluetoothCodecInfo.Companion.shouldPromptForBluetoothPermission
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure Bluetooth-codec int → display-name mapping in [BluetoothCodecInfo]. This is
 * the JVM-testable half of the codec feature; the reflective platform access in
 * `BluetoothCodecReader` is not unit-tested (it needs a real device / framework).
 */
class BluetoothCodecInfoTest {

    // --- codecTypeToName: table-driven over every known codec ---

    @Test
    fun codecTypeToName_mapsEveryKnownCodec() {
        val expected = mapOf(
            SOURCE_CODEC_TYPE_SBC to "SBC",
            SOURCE_CODEC_TYPE_AAC to "AAC",
            SOURCE_CODEC_TYPE_APTX to "aptX",
            SOURCE_CODEC_TYPE_APTX_HD to "aptX HD",
            SOURCE_CODEC_TYPE_LDAC to "LDAC",
            SOURCE_CODEC_TYPE_LC3 to "LC3",
            SOURCE_CODEC_TYPE_APTX_ADAPTIVE to "aptX Adaptive",
            SOURCE_CODEC_TYPE_OPUS to "Opus",
            SOURCE_CODEC_TYPE_LHDC_V2 to "LHDC",
            SOURCE_CODEC_TYPE_LHDC_V3 to "LHDC",
            SOURCE_CODEC_TYPE_LHDC_V5 to "LHDC",
        )
        expected.forEach { (type, name) ->
            assertEquals("codec type $type", name, codecTypeToName(type))
        }
    }

    @Test
    fun codecTypeToName_unknownAndInvalid_returnsNull() {
        assertNull(codecTypeToName(SOURCE_CODEC_TYPE_INVALID))
        assertNull(codecTypeToName(-1))
        assertNull(codecTypeToName(9999))
    }

    // --- LDAC bitrate label ---

    @Test
    fun ldacBitrateLabel_knownQualities() {
        assertEquals("990kbps", ldacBitrateLabel(LDAC_QUALITY_HIGH))
        assertEquals("660kbps", ldacBitrateLabel(LDAC_QUALITY_MID))
        assertEquals("330kbps", ldacBitrateLabel(LDAC_QUALITY_LOW))
        assertEquals("Adaptive", ldacBitrateLabel(LDAC_QUALITY_ABR))
    }

    @Test
    fun ldacBitrateLabel_unknownQuality_returnsNull() {
        assertNull(ldacBitrateLabel(0L))
        assertNull(ldacBitrateLabel(42L))
    }

    // --- from(): full construction incl. label composition ---

    @Test
    fun from_ldacWithHighQuality_composesBitrateIntoLabel() {
        val info = from(SOURCE_CODEC_TYPE_LDAC, LDAC_QUALITY_HIGH)!!
        assertEquals("LDAC", info.displayName)
        assertEquals("990kbps", info.ldacBitrateLabel)
        assertEquals("LDAC 990kbps", info.label)
    }

    @Test
    fun from_ldacWithoutQuality_hasNoBitrateLabel() {
        val info = from(SOURCE_CODEC_TYPE_LDAC, codecSpecific1 = null)!!
        assertEquals("LDAC", info.displayName)
        assertNull(info.ldacBitrateLabel)
        assertEquals("LDAC", info.label)
    }

    @Test
    fun from_ldacWithUnknownQuality_hasNoBitrateLabel() {
        val info = from(SOURCE_CODEC_TYPE_LDAC, codecSpecific1 = 123L)!!
        assertNull(info.ldacBitrateLabel)
        assertEquals("LDAC", info.label)
    }

    @Test
    fun from_nonLdac_ignoresCodecSpecific1() {
        // A codecSpecific1 value must not leak a bitrate label onto a non-LDAC codec.
        val info = from(SOURCE_CODEC_TYPE_APTX_HD, LDAC_QUALITY_HIGH)!!
        assertEquals("aptX HD", info.displayName)
        assertNull(info.ldacBitrateLabel)
        assertEquals("aptX HD", info.label)
    }

    @Test
    fun from_unknownCodec_returnsNull() {
        assertNull(from(SOURCE_CODEC_TYPE_INVALID, null))
        assertNull(from(-7, LDAC_QUALITY_HIGH))
    }

    @Test
    fun from_aacNoQuality_labelIsPlainName() {
        val info = from(SOURCE_CODEC_TYPE_AAC)!!
        assertEquals("AAC", info.label)
        assertNull(info.ldacBitrateLabel)
    }

    // --- BLUETOOTH_CONNECT permission gating (drives the runtime prompt in SongInfoScreen) ---

    @Test
    fun shouldPrompt_onlyWhenCurrentAndMissingAndOnBtRoute() {
        assertTrue(
            shouldPromptForBluetoothPermission(
                isCurrentSong = true,
                permissionMissing = true,
                onBluetoothRoute = true,
            )
        )
    }

    @Test
    fun shouldNotPrompt_whenPermissionAlreadyGranted() {
        assertFalse(
            shouldPromptForBluetoothPermission(
                isCurrentSong = true,
                permissionMissing = false,
                onBluetoothRoute = true,
            )
        )
    }

    @Test
    fun shouldNotPrompt_whenNotOnBluetoothRoute() {
        // Wired/speaker playback: requesting the permission would gain nothing, so we don't ask.
        assertFalse(
            shouldPromptForBluetoothPermission(
                isCurrentSong = true,
                permissionMissing = true,
                onBluetoothRoute = false,
            )
        )
    }

    @Test
    fun shouldNotPrompt_whenTrackIsNotPlaying() {
        // Viewing info for a non-playing track: no runtime format to read, so no prompt.
        assertFalse(
            shouldPromptForBluetoothPermission(
                isCurrentSong = false,
                permissionMissing = true,
                onBluetoothRoute = true,
            )
        )
    }
}
