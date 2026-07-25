package com.viperplayer.data.player.cast

import com.viperplayer.plugin.model.DashStream
import com.viperplayer.plugin.model.HlsStream
import com.viperplayer.plugin.model.PcmStream
import com.viperplayer.plugin.model.StreamSource
import com.viperplayer.plugin.model.UnknownStream
import com.viperplayer.plugin.model.UrlStream

/**
 * Pure decisions about whether a track can be sent to a Google Cast receiver, and if so with which
 * URL + MIME type. This holds no Android/framework state so it can be unit-tested in isolation.
 *
 * The default Cast media receiver can only fetch a public progressive HTTP(S) URL and play it back:
 *  - It runs on the Cast device, so it cannot reach a `file://`/`content://` URI on the phone
 *    (downloaded/local tracks) nor a raw PCM stream delivered over an in-process pipe FD.
 *  - It cannot decrypt our DRM/ClearKey DASH or HLS streams (those need the host's local key path).
 *
 * So only a plain progressive [UrlStream] over http(s) — with no DRM — is castable. Everything else
 * is honestly reported as not castable so the caller can skip/notify instead of hanging.
 */
object CastEligibility {

    /** The outcome of asking "can this resolved stream be cast, and how?". */
    sealed interface Result {
        /** The stream can be cast: send [url] (with [mimeType] when known) to the receiver. */
        data class Castable(val url: String, val mimeType: String?) : Result

        /** The stream cannot be cast; [reason] explains why (for logging, not for the user). */
        data class NotCastable(val reason: Reason) : Result
    }

    /** Why a stream is not castable. */
    enum class Reason {
        /** A downloaded/on-device file — the receiver can't reach the phone. */
        LOCAL_FILE,

        /** A `content://`/`file://` (or otherwise non-http) URL — the receiver can't reach it. */
        NON_HTTP_URL,

        /**
         * A progressive URL that requires custom request headers (e.g. an Origin-allowlisted proxy
         * like a proxied source). The default Cast receiver fetches the raw URL with no custom
         * headers, so such a stream would 403/fail on the device — treat it as not castable.
         */
        HEADER_AUTH,

        /** A DRM/ClearKey-protected stream — the default receiver can't decrypt it. */
        DRM_PROTECTED,

        /** A DASH stream — the default receiver has no manifest support in this app's setup. */
        DASH,

        /** An HLS stream — not sent to the default receiver here (may be DRM / segment-relative). */
        HLS,

        /** A raw PCM stream delivered over an FD — cannot leave the device. */
        PCM,

        /** A stream kind this host version doesn't understand. */
        UNKNOWN,
    }

    /**
     * Decide whether [source] resolved for a track is castable.
     *
     * @param isDownloadedLocalFile whether the host resolved this track to an on-disk downloaded
     *   file (which short-circuits stream resolution). When true the track is never castable
     *   regardless of [source].
     */
    fun evaluate(source: StreamSource?, isDownloadedLocalFile: Boolean): Result {
        if (isDownloadedLocalFile) return Result.NotCastable(Reason.LOCAL_FILE)
        return when (source) {
            is UrlStream -> {
                if (source.drmProtected()) {
                    Result.NotCastable(Reason.DRM_PROTECTED)
                } else if (!isHttpUrl(source.url)) {
                    Result.NotCastable(Reason.NON_HTTP_URL)
                } else if (source.headers.isNotEmpty()) {
                    // The receiver fetches the raw URL with no custom headers, so a header-authed
                    // proxy stream (e.g. Origin-allowlisted a proxied source) would 403 on-device.
                    Result.NotCastable(Reason.HEADER_AUTH)
                } else {
                    Result.Castable(source.url, source.mimeType)
                }
            }

            is DashStream -> Result.NotCastable(
                if (source.drm != null) Reason.DRM_PROTECTED else Reason.DASH
            )

            is HlsStream -> Result.NotCastable(
                if (source.drm != null) Reason.DRM_PROTECTED else Reason.HLS
            )

            is PcmStream -> Result.NotCastable(Reason.PCM)
            is UnknownStream -> Result.NotCastable(Reason.UNKNOWN)
            null -> Result.NotCastable(Reason.UNKNOWN)
        }
    }

    /** Whether [url] is a plain http(s) URL the receiver can fetch (not file/content/etc). */
    fun isHttpUrl(url: String): Boolean {
        val lower = url.trim().lowercase()
        return lower.startsWith("http://") || lower.startsWith("https://")
    }

    /** A [UrlStream] never carries DRM in this SDK; kept as a hook so the check reads uniformly. */
    private fun UrlStream.drmProtected(): Boolean = false
}
