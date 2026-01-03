package com.viperplayer.data.plugin.handler

import android.os.IBinder
import com.viperplayer.data.source.PluginException
import com.viperplayer.plugin.v1.IViperPluginV1
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Factory for creating plugin handlers based on API version.
 * This allows us to support multiple plugin API versions simultaneously.
 */
@Singleton
class PluginHandlerFactory @Inject constructor() {
    
    /**
     * Creates a plugin handler for the given binder and plugin ID.
     * Determines the API version from the binder interface descriptor.
     * 
     * @param pluginId The plugin identifier
     * @param binder The service binder
     * @return A plugin handler for the appropriate API version
     * @throws PluginException If the API version is not supported
     */
    fun createHandler(pluginId: String, binder: IBinder): PluginHandler {
        val descriptor = try {
            binder.interfaceDescriptor
        } catch (e: Exception) {
            Timber.e(e, "Error getting interface descriptor for plugin: $pluginId")
            throw PluginException(-1, "Failed to get interface descriptor: ${e.message}")
        }
        
        return when (descriptor) {
            IViperPluginV1.DESCRIPTOR -> {
                val service = IViperPluginV1.Stub.asInterface(binder)
                    ?: throw PluginException(-1, "Failed to get IViperPluginV1 interface from binder")
                PluginHandlerV1(pluginId, service)
            }
            // Future API versions can be added here:
            // IViperPluginV2.DESCRIPTOR -> PluginHandlerV2(pluginId, service)
            else -> {
                Timber.w("Unknown interface descriptor: $descriptor for plugin: $pluginId")
                throw PluginException(-1, "Unsupported plugin API version: $descriptor")
            }
        }
    }
    
    /**
     * Gets the API version from a binder without creating a handler.
     * Useful for connection logic that needs to know the version before creating the handler.
     */
    fun getApiVersion(binder: IBinder): Int? {
        return try {
            val descriptor = binder.interfaceDescriptor
            when (descriptor) {
                IViperPluginV1.DESCRIPTOR -> 1
                // Future API versions:
                // IViperPluginV2.DESCRIPTOR -> 2
                else -> null
            }
        } catch (e: Exception) {
            Timber.e(e, "Error getting API version from binder")
            null
        }
    }
}

