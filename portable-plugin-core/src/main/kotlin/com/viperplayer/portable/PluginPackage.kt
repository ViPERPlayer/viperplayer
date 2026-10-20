package com.viperplayer.portable

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Plain JSON contract. No Android, Binder, QuickJS bytecode, or browser objects cross this API. */
@Serializable
data class PortableManifest(
    val id: String,
    val name: String,
    val version: String,
    val apiVersion: Int,
    val entry: String = "plugin.js",
    val methods: Set<String>,
    val allowedHosts: Set<String> = emptySet(),
) {
    fun validate() {
        require(apiVersion == 1) { "Unsupported portable plugin API: $apiVersion" }
        require(id.matches(Regex("[a-zA-Z0-9][a-zA-Z0-9._-]{0,127}"))) { "Invalid plugin id" }
        require(name.isNotBlank() && version.isNotBlank()) { "Missing plugin identity" }
        require(entry.matches(Regex("[a-zA-Z0-9_-]+\\.js"))) { "Entry must be a top-level .js file" }
        require(methods.isNotEmpty() && methods.size <= 64) { "Invalid method list" }
        require(methods.all { it.matches(Regex("[a-zA-Z][a-zA-Z0-9]{0,63}")) })
        require(allowedHosts.size <= 32)
        require(allowedHosts.all {
            it == it.lowercase() && it.matches(Regex("[a-z0-9]+([.-][a-z0-9]+)*\\.[a-z]{2,}"))
        }) { "Permissions require exact lowercase DNS hostnames; no wildcards or IP literals" }
    }
}

data class PluginPackage(val manifest: PortableManifest, val source: String) {
    init {
        manifest.validate()
        require(source.toByteArray().size <= MAX_SOURCE_BYTES) { "Plugin source is too large" }
    }

    companion object {
        const val MAX_SOURCE_BYTES = 1_048_576
        val json = Json { ignoreUnknownKeys = true }
    }
}

class PortablePluginException(
    val code: String,
    message: String,
    val retryable: Boolean = false,
    cause: Throwable? = null,
) : Exception(message, cause)
