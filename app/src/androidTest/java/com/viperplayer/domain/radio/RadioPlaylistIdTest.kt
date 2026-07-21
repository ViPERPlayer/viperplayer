package com.viperplayer.domain.radio

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.viperplayer.domain.model.MediaId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented (real android.net.Uri) test for the "Song radio" synthetic-id round-trip (issue #7).
 * Radio is now the typed [MediaId.Radio] variant that holds the seed [MediaId] directly, so
 * [RadioPlaylist.buildMediaId] wraps the seed and [RadioPlaylist.parseSeedId] unwraps it — even when
 * the seed is a `local` `content://…` URI full of reserved characters. The [MediaId.encode]/[MediaId.decode]
 * round-trip through the wrapped seed is also verified.
 */
@RunWith(AndroidJUnit4::class)
class RadioPlaylistIdTest {

    @Test
    fun buildMediaId_isRadioPlaylist_andParsesSeedBack() {
        val seed = MediaId.Local("content://media/external/audio/media/123")

        val radioId = RadioPlaylist.buildMediaId(seed)

        assertTrue(radioId is MediaId.Radio)
        assertTrue(RadioPlaylist.isRadioPlaylist(radioId))
        // Round-trips exactly back to the original seed.
        assertEquals(seed, RadioPlaylist.parseSeedId(radioId))
    }

    @Test
    fun encodeDecode_roundTrips_radioWithSeedContainingReservedChars() {
        // A plugin seed whose sourceId itself contains '=' and '&' (the encode() separators).
        val seed = MediaId.Plugin("testsource", "track=42&region=US")
        val radioId = RadioPlaylist.buildMediaId(seed)

        // encode/decode round-trips the whole radio id (and thus the nested seed) intact.
        val decoded = MediaId.decode(radioId.encode())
        assertEquals(radioId, decoded)
        assertEquals(seed, RadioPlaylist.parseSeedId(decoded!!))
    }

    @Test
    fun parseSeedId_nonRadioId_isNull() {
        assertNull(RadioPlaylist.parseSeedId(MediaId.Plugin("auto", "recently_added")))
        assertNull(RadioPlaylist.parseSeedId(MediaId.Local("song123")))
    }

    @Test
    fun isRadioPlaylist_onlyForRadioVariant() {
        assertTrue(RadioPlaylist.isRadioPlaylist(MediaId.Radio(MediaId.Local("seed"))))
        assertFalse(RadioPlaylist.isRadioPlaylist(MediaId.Plugin("auto", "seed")))
        assertFalse(RadioPlaylist.isRadioPlaylist(MediaId.Local("seed")))
    }
}
