package com.viperplayer.portable

import kotlinx.serialization.json.JsonElement

/** Optional DOM execution capability. No provider-specific operations belong in this interface. */
fun interface PluginBrowserFactory {
    suspend fun open(origin: String, userAgent: String?): PluginBrowserSession
}

interface PluginBrowserSession {
    /** Script is a function expression taking JSON args; its awaited result must be JSON. */
    suspend fun evaluate(script: String, args: JsonElement): JsonElement
    suspend fun close()
}
