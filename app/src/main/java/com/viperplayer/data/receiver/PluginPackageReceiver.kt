package com.viperplayer.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.viperplayer.ViperPlayerApp
import com.viperplayer.data.source.PluginDataSource
import com.viperplayer.di.PluginEntryPoint
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * BroadcastReceiver that listens for package install/update/replace/uninstall events
 * and automatically refreshes the plugin list.
 * Uses Hilt EntryPoint for dependency injection.
 */
@AndroidEntryPoint
class PluginPackageReceiver : BroadcastReceiver() {
    @Inject
    lateinit var pluginDataSource: PluginDataSource
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val packageName = intent.data?.schemeSpecificPart
        
        Timber.d("Package event: $action for package: $packageName")
        
        when (action) {
            Intent.ACTION_PACKAGE_ADDED,
            Intent.ACTION_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REMOVED -> {
                try {
                    scope.launch {
                        pluginDataSource.discoverPlugins()
                        Timber.d("Plugin discovery triggered after package change")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to trigger plugin discovery")
                }
            }
        }
    }
}

