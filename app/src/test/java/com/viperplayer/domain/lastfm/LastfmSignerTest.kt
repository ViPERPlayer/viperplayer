package com.viperplayer.domain.lastfm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [LastfmSigner]: the api_sig is the lower-case MD5 of the parameters sorted by name,
 * concatenated as name+value, with the shared secret appended. `format`/`callback`/`api_sig` are
 * excluded from the signature.
 */
class LastfmSignerTest {

    @Test
    fun md5Hex_matchesKnownVector() {
        // Standard MD5 test vector.
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", LastfmSigner.md5Hex(""))
        assertEquals("900150983cd24fb0d6963f7d28e17f72", LastfmSigner.md5Hex("abc"))
    }

    @Test
    fun sign_sortsParamsConcatenatesAndAppendsSecret() {
        // Worked by hand: sorted params are api_key=KEY, method=auth.getMobileSession, username=alice
        // → "api_keyKEYmethodauth.getMobileSessionusernamealice" + secret "SECRET".
        val params = mapOf(
            "method" to "auth.getMobileSession",
            "username" to "alice",
            "api_key" to "KEY",
        )
        val expected = LastfmSigner.md5Hex("api_keyKEYmethodauth.getMobileSessionusernamealiceSECRET")
        assertEquals(expected, LastfmSigner.sign(params, "SECRET"))
    }

    @Test
    fun sign_isOrderIndependent() {
        val a = mapOf("b" to "2", "a" to "1", "c" to "3")
        val b = mapOf("c" to "3", "a" to "1", "b" to "2")
        assertEquals(LastfmSigner.sign(a, "s"), LastfmSigner.sign(b, "s"))
    }

    @Test
    fun sign_excludesFormatCallbackAndApiSig() {
        val withExtras = mapOf(
            "api_key" to "KEY",
            "method" to "track.scrobble",
            "format" to "json",
            "callback" to "cb",
            "api_sig" to "should-be-ignored",
        )
        val withoutExtras = mapOf(
            "api_key" to "KEY",
            "method" to "track.scrobble",
        )
        assertEquals(
            LastfmSigner.sign(withoutExtras, "SECRET"),
            LastfmSigner.sign(withExtras, "SECRET"),
        )
    }

    @Test
    fun signed_addsApiSigWithoutMutatingInput() {
        val params = mapOf("api_key" to "KEY", "method" to "m")
        val signed = LastfmSigner.signed(params, "SECRET")
        assertEquals(LastfmSigner.sign(params, "SECRET"), signed["api_sig"])
        assertFalse(params.containsKey("api_sig"))
        assertTrue(signed.containsKey("api_key"))
        assertTrue(signed.containsKey("method"))
    }
}
