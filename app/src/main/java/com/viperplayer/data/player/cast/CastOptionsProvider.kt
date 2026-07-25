package com.viperplayer.data.player.cast

import android.content.Context
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider

/**
 * Supplies the Cast framework configuration. Registered in the manifest via the
 * `com.google.android.gms.cast.framework.OPTIONS_PROVIDER_CLASS_NAME` meta-data, so
 * `CastContext.getSharedInstance(...)` (which the media3 cast button and player both use) can
 * initialize itself lazily without any explicit bootstrapping code.
 *
 * We target the Default Media Receiver, which plays a plain progressive HTTP(S) URL. That is why
 * only progressive streams are castable (see [CastEligibility]) — the default receiver can't fetch
 * device-local files nor decrypt our DRM/DASH streams.
 */
class CastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions =
        CastOptions.Builder()
            .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
            .setStopReceiverApplicationWhenEndingSession(true)
            .build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider> = emptyList()
}
