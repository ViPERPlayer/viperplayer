package com.viperplayer.domain.lastfm

import java.security.MessageDigest

/**
 * Pure, Android-free signing for the Last.fm web API. Every write/auth call must carry an `api_sig`
 * parameter computed as documented by Last.fm:
 *
 *  1. sort all request parameters (except `format` and `callback`) by name, ascending;
 *  2. concatenate them as `name` + `value` with NO separators, in that order;
 *  3. append the shared secret;
 *  4. take the UTF-8 MD5 of that string, lower-case hex.
 *
 * This object is deterministic and side-effect free so it can be unit-tested against Last.fm's own
 * worked examples. It never logs and never sees the user's password — only the parameters the caller
 * passes in.
 */
object LastfmSigner {

    /** Parameters excluded from the signature per the Last.fm spec. */
    private val UNSIGNED_PARAMS = setOf("format", "callback", "api_sig")

    /**
     * Computes the `api_sig` for [params]. Keys in [UNSIGNED_PARAMS] are ignored; the rest are sorted
     * by name and concatenated as name+value with the [sharedSecret] appended, then MD5-hashed.
     */
    fun sign(params: Map<String, String>, sharedSecret: String): String {
        val builder = StringBuilder()
        params.entries
            .filter { it.key !in UNSIGNED_PARAMS }
            .sortedBy { it.key }
            .forEach { (key, value) ->
                builder.append(key).append(value)
            }
        builder.append(sharedSecret)
        return md5Hex(builder.toString())
    }

    /**
     * Returns [params] plus the computed `api_sig`. Convenience for callers that immediately turn the
     * map into a form/query. The input map is not mutated.
     */
    fun signed(params: Map<String, String>, sharedSecret: String): Map<String, String> =
        params + ("api_sig" to sign(params, sharedSecret))

    /** Lower-case hex MD5 of [input]'s UTF-8 bytes. */
    fun md5Hex(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        val hex = StringBuilder(digest.size * 2)
        for (byte in digest) {
            val v = byte.toInt() and 0xFF
            hex.append(HEX_CHARS[v ushr 4])
            hex.append(HEX_CHARS[v and 0x0F])
        }
        return hex.toString()
    }

    private const val HEX_CHARS = "0123456789abcdef"
}
