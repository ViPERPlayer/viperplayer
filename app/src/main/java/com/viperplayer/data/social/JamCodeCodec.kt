package com.viperplayer.data.social

/**
 * Share-code + invite-URL conventions for Jam sessions, shared by every
 * [com.viperplayer.domain.repository.ListenTogetherRepository] implementation (the mock and the real
 * backend-backed one) so the UI-facing code parsing/formatting is identical regardless of backend.
 *
 * The codes use an unambiguous alphabet (no O/0, I/1) and the invite URL mirrors the backend's
 * `inviteBase` deep-link prefix. [parseCode] accepts either a raw code ("8KX29QT" / "8KX2-9QT") or a
 * pasted invite URL (…/jam/8kx29qt).
 */
object JamCodeCodec {

    /** Number of character cells shown in the UI for a manual-entry code. */
    const val CODE_LENGTH = 6

    /** Build the shareable invite URL for a code. */
    fun inviteUrlFor(code: String): String = "$INVITE_BASE/${code.lowercase()}"

    /** Extract a normalised session code from a pasted invite URL or raw code, or null if unrecognised. */
    fun parseCode(input: String): String? {
        val tail = input.substringAfterLast('/').substringBefore('?').trim()
        val cleaned = tail.uppercase().filter { it in CODE_ALPHABET }
        return if (cleaned.length >= CODE_LENGTH) cleaned.take(CODE_LENGTH) else null
    }

    /** Generate a fresh, human-shareable code ("ABCD-EFG"). Used by the mock host path. */
    fun generateCode(): String {
        fun block(n: Int) = (1..n).map { CODE_ALPHABET.random() }.joinToString("")
        return "${block(4)}-${block(3)}"
    }

    // Unambiguous alphabet (no O/0, I/1) — matches the codes shared by the QR/invite sheets.
    private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private const val INVITE_BASE = "https://viper.player/jam"
}
