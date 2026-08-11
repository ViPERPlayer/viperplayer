package com.viperplayer.domain.social

import com.viperplayer.domain.config.BackendAvailability
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Feature gate for the (aspirational) social surface — friends rail, live-Jam cards, shared-playlist
 * inbox, friend-activity feed. Injectable into ViewModels/composables so the social sections are only
 * shown when the ViPER backend is configured for this build; when it isn't, [enabled] is `false` and
 * every social repository emits empty, so those sections render nothing.
 *
 * [enabled] is exactly [BackendAvailability.isConfigured] — the same notion the account/session
 * transports use — so there is no divergent "configured" test anywhere in the app.
 */
@Singleton
class SocialFeatures @Inject constructor(
    private val backendAvailability: BackendAvailability,
) {

    /** `true` iff a real backend URL was compiled into this build. */
    val enabled: Boolean get() = backendAvailability.isConfigured()
}
