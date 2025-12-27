package com.viperplayer

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.request.CachePolicy
import com.viperplayer.domain.repository.SettingsRepository
import com.viperplayer.ktx.awaitBlocking
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class ViperPlayerApplication : Application(), SingletonImageLoader.Factory {
    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val cacheSizeDeferred = CompletableDeferred<Long>()
    
    override fun onCreate() {
        super.onCreate()
        
        initializeTimber()
        prefetchCacheSize()
    }

    private fun initializeTimber() {
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

    private fun prefetchCacheSize() {
        scope.launch {
            val size = settingsRepository.cacheSize.first()
            cacheSizeDeferred.complete(size)
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val cacheSize = cacheSizeDeferred.awaitBlocking()

        return ImageLoader.Builder(this)
            .apply {
                if (cacheSize == 0L)
                    diskCachePolicy(CachePolicy.DISABLED)
                else
                    diskCache {
                        DiskCache.Builder()
                            .maxSizeBytes(cacheSize)
                            .build()
                    }
            }
            .build()
    }
}
