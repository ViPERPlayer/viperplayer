package com.viperplayer

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import com.viperplayer.domain.repository.SettingsRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class ViperPlayerApplication : Application(), SingletonImageLoader.Factory {
    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate() {
        super.onCreate()

        initializeTimber()
    }

    private fun initializeTimber() {
        Timber.plant(Timber.DebugTree())
//        if (BuildConfig.DEBUG) {
//            Timber.plant(Timber.DebugTree())
//        } else {
//            // In release builds, you might want to use a crash reporting tree
//            // For now, we'll use a no-op tree or you can add CrashlyticsTree
//            Timber.plant(object : Timber.Tree() {
//                override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
//                    // In release, you might want to send logs to crash reporting service
//                    // For now, we'll just ignore them
//                }
//            })
//        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val maxImageCacheSize = runBlocking(Dispatchers.IO) { settingsRepository.maxImageCacheSize.first() }

        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25) // 25% of app memory
                    .build()
            }
            .diskCache {
                if (maxImageCacheSize == 0L) {
                    null // Disable disk cache if size is 0
                } else {
                    DiskCache.Builder()
                        .directory(context.cacheDir.resolve("image_cache"))
                        .maxSizeBytes(maxImageCacheSize)
                        .build()
                }
            }
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }
}
