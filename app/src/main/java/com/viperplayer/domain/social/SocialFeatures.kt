package com.viperplayer.domain.social

import com.viperplayer.data.social.BackendConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Feature gate for the (aspirational) social surface — friends rail, live-Jam cards, shared-playlist
 * inbox, friend-activity feed. Injectable into ViewModels/composables so the social sections are only
 * shown when the ViPER backend is configured for this build; when it isn't, [enabled] is `false` and
 * every social repository emits empty, so those sections render nothing.
 *
 * [enabled] reuses the exact backend-configured notion the account/session transports use
 * ([BackendConfig]) — a blank or placeholder `VIPER_BACKEND_URL` is "off".
 */
@Singleton
class SocialFeatures @Inject constructor() {

    /** `true` iff a real backend URL was compiled into this build. */
    val enabled: Boolean get() = BackendConfig.isConfigured
}
