package com.viperplayer.portable

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.QuickJsException
import com.dokar.quickjs.QuickJsInterruptedException
import com.dokar.quickjs.evaluate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/** Disposable ECMAScript realm with no host bindings, DOM, networking, files, or module loader. */
internal object IsolatedJavaScript {
    private val slot = Semaphore(1)
    suspend fun evaluate(script: String, args: JsonElement, executionTimeoutMs: Long = 10_000): JsonElement = slot.withPermit {
        withTimeoutOrNull(15_000) {
            val engine = QuickJs.create(Dispatchers.Default)
            try {
                engine.memoryLimit = 256L * 1024 * 1024
                engine.maxStackSize = 1024L * 1024
                engine.evaluationTimeoutMillis = executionTimeoutMs
                val output = engine.evaluate<String>(
                    """JSON.stringify(await (new Function('return (' + ${JsonPrimitive(script)} + ')'))()($args))""",
                    filename = "isolated-plugin-script.js",
                )
                if (output.toByteArray().size > 524_288) throw PortablePluginException("INVALID_RESULT", "Script result exceeds 512 KiB")
                PluginPackage.json.parseToJsonElement(output)
            } catch (error: QuickJsInterruptedException) {
                throw PortablePluginException("TIMEOUT", "Isolated script execution limit exceeded", cause = error)
            } catch (error: QuickJsException) {
                throw PortablePluginException("PLAYBACK", "Isolated script failed: ${error.message}", cause = error)
            } finally {
                engine.close()
            }
        } ?: throw PortablePluginException("TIMEOUT", "Isolated script operation timed out", true)
    }
}
