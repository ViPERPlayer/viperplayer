package com.viperplayer.data.social

import com.viperplayer.domain.config.BackendAvailability
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [BackendAvailability] backed by the build-time `VIPER_BACKEND_URL`, resolved through the shared
 * [BackendConfig] rule so the account/session transports and the social gate never disagree about
 * what "configured" means.
 */
@Singleton
class BuildConfigBackendAvailability @Inject constructor() : BackendAvailability {
    override fun isConfigured(): Boolean = BackendConfig.isConfigured
}
