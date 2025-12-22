package com.viperplayer

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

/**
 * Application class for ViPER Player.
 * Annotated with @HiltAndroidApp to enable Hilt dependency injection.
 */
@HiltAndroidApp
class ViperPlayerApp : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            // In release builds, you might want to use a crash reporting tree
            // For now, we'll use a no-op tree or you can add CrashlyticsTree
            Timber.plant(object : Timber.Tree() {
                override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                    // In release, you might want to send logs to crash reporting service
                    // For now, we'll just ignore them
                }
            })
        }
    }
}
