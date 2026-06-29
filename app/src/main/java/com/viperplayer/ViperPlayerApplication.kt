package com.viperplayer

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Application
import android.content.Intent
import android.os.Build
import android.util.Log
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import com.viperplayer.BuildConfig
import com.viperplayer.domain.repository.HistoryDuration
import com.viperplayer.domain.repository.MediaLibraryRepository
import com.viperplayer.domain.repository.SettingsRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import javax.inject.Inject
import kotlin.system.exitProcess

private const val TAG = "ViperPlayerApplication"

@HiltAndroidApp
class ViperPlayerApplication : Application(), SingletonImageLoader.Factory {
    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var mediaLibraryRepository: MediaLibraryRepository

    override fun onCreate() {
        super.onCreate()
        setupCrashHandler()

        if (!isMainProcess()) {
            // We are inside the ":crash" process (or a background service process).
            // Do absolutely nothing. Just return.
            return
        }

        initializeTimber()
        pruneHistory()
    }

    /** Enforce the History Duration retention window at startup (FOREVER prunes nothing). */
    private fun pruneHistory() {
        CoroutineScope(Dispatchers.IO).launch {
            val retentionDays = when (settingsRepository.historyDuration.first()) {
                HistoryDuration.DAYS_7 -> 7L
                HistoryDuration.DAYS_30 -> 30L
                HistoryDuration.DAYS_90 -> 90L
                HistoryDuration.DAYS_180 -> 180L
                HistoryDuration.DAYS_365 -> 365L
                HistoryDuration.FOREVER -> return@launch
            }
            val cutoff = System.currentTimeMillis() - retentionDays * 24L * 60L * 60L * 1000L
            mediaLibraryRepository.pruneHistory(cutoff)
        }
    }

    @SuppressLint("LogNotTimber")
    private fun setupCrashHandler() {
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val message = throwable.message
            val stackTrace = Log.getStackTraceString(throwable)
            val threadName = thread.name
            Log.e(TAG, "Uncaught exception on thread $threadName:\n $message")
            val intent = Intent(this, CrashActivity::class.java)
                .putExtra(CrashActivity.EXTRA_MESSAGE, message)
                .putExtra(CrashActivity.EXTRA_STACK_TRACE, stackTrace)
                .putExtra(CrashActivity.EXTRA_THREAD_NAME, threadName)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(intent)
            exitProcess(10)
        }
    }

    private fun initializeTimber() {
        // Debug logging only in debug builds — release ships silent (no Timber chatter / leaked tags).
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }

    private fun isMainProcess(): Boolean {
        val currentProcessName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getProcessName()
        } else {
            getProcessNameLegacy()
        }

        // The main process name is exactly your application's package name
        return currentProcessName == packageName
    }

    // Fallback for devices running Android 8.1 (API 27) and below
    private fun getProcessNameLegacy(): String? {
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val myPid = android.os.Process.myPid()
        for (processInfo in am.runningAppProcesses.orEmpty()) {
            if (processInfo.pid == myPid) {
                return processInfo.processName
            }
        }
        return null
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val maxImageCacheSize =
            runBlocking(Dispatchers.IO) { settingsRepository.maxImageCacheSize.first() }

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
