package com.viperplayer.local

import android.content.Context
import com.viperplayer.plugin.ViperPlugin
import com.viperplayer.plugin.ViperPluginService

class LocalService : ViperPluginService() {
    override fun createPlugin(context: Context): ViperPlugin {
        return LocalPlugin(context)
    }
}
