package com.viperplayer.domain.config

/**
 * Whether this build has a ViPER backend to talk to.
 *
 * Domain-owned so feature gates like [com.viperplayer.domain.social.SocialFeatures] can ask the
 * question without reaching into the data layer for it. The answer comes from a build-time constant,
 * which is infrastructure: `data.social.BuildConfigBackendAvailability` supplies it, and tests supply
 * a fake instead of relying on whatever `BuildConfig.VIPER_BACKEND_URL` happens to hold.
 */
fun interface BackendAvailability {

    /** `true` iff a real (non-blank, non-placeholder) backend URL was compiled into this build. */
    fun isConfigured(): Boolean
}
