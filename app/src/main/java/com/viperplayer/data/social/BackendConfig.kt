package com.viperplayer.data.social

import com.viperplayer.BuildConfig

/**
 * Single source of truth for whether the ViPER backend (`VIPER_BACKEND_URL`) is configured for this
 * build. Both the account/session transports ([SessionApi], [com.viperplayer.data.account.AccountApi])
 * and the social feature gate ([com.viperplayer.domain.social.SocialFeatures]) resolve "is the backend
 * built in?" the same way: a blank or placeholder URL means unconfigured, so the app degrades to its
 * offline/local behavior and social sections render nothing.
 *
 * Pure and Android-free (only reads [BuildConfig]) so it can be unit-tested directly, and the raw URL
 * source is overridable so tests can point it at a real or placeholder value without a device.
 */
object BackendConfig {

    /** The build-time placeholder that means "no backend URL was substituted in". */
    const val PLACEHOLDER = "REPLACE_WITH_REAL_VALUE"

    /**
     * Normalizes a raw backend URL to a usable base URL (no trailing slash), or `null` when the URL is
     * blank or the un-substituted [PLACEHOLDER]. Shared by every backend seam so the configured check is
     * identical everywhere.
     */
    fun baseUrlOf(rawUrl: String): String? =
        rawUrl.trimEnd('/').takeIf { it.isNotBlank() && it != PLACEHOLDER }

    /** `true` iff [rawUrl] resolves to a usable backend base URL. */
    fun isConfigured(rawUrl: String): Boolean = baseUrlOf(rawUrl) != null

    /** The configured backend base URL from [BuildConfig], or `null` when not built in. */
    val baseUrl: String? get() = baseUrlOf(BuildConfig.VIPER_BACKEND_URL)

    /** `true` iff a real backend URL was compiled into this build. */
    val isConfigured: Boolean get() = baseUrl != null
}
