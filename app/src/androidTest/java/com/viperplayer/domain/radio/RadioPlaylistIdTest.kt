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
 * Instrumented (real android.net.Uri) test for the "Song radio" synthetic-id encode/parse round-trip
 * (issue #7). [RadioPlaylist.buildMediaId] packs the seed [MediaId] into the radio id's `sourceId`
 * (URL-encoded) so that [RadioPlaylist.parseSeedId] recovers it exactly — even when the seed is a
 * `local` `content://…` URI full of reserved characters that would otherwise corrupt the id string.
 */
@RunWith(AndroidJUnit4::class)
class RadioPlaylistIdTest {

    @Test
    fun buildMediaId_isRadioPlaylist_andParsesSeedBack() {
        val seed = MediaId("local", "content://media/external/audio/media/123")

        val radioId = RadioPlaylist.buildMediaId(seed)

        assertEquals(RadioPlaylist.PLUGIN_ID, radioId.pluginId)
        assertTrue(RadioPlaylist.isRadioPlaylist(radioId))
        // Round-trips exactly back to the original seed — not a percent-mangled form.
        assertEquals(seed, RadioPlaylist.parseSeedId(radioId))
    }

    @Test
    fun buildMediaId_roundTrips_pluginSeedWithReservedChars() {
        // A plugin seed whose sourceId itself contains '=' and '&' (the MediaId string separators).
        val seed = MediaId("testsource", "track=42&region=US")

        val radioId = RadioPlaylist.buildMediaId(seed)
        // The nested separators are encoded away, so the radio id's own parsing is not confused.
        assertFalse(radioId.sourceId.contains("&"))

        assertEquals(seed, RadioPlaylist.parseSeedId(radioId))
    }

    @Test
    fun parseSeedId_nonRadioId_isNull() {
        assertNull(RadioPlaylist.parseSeedId(MediaId("auto", "recently_added")))
        assertNull(RadioPlaylist.parseSeedId(MediaId("local", "song123")))
    }

    @Test
    fun isRadioPlaylist_onlyForRadioPlugin() {
        assertTrue(RadioPlaylist.isRadioPlaylist(MediaId("radio", "seed")))
        assertFalse(RadioPlaylist.isRadioPlaylist(MediaId("auto", "seed")))
        assertFalse(RadioPlaylist.isRadioPlaylist(MediaId("local", "seed")))
    }
}
