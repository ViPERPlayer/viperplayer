package com.viperplayer.portable

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.QuickJsException
import com.dokar.quickjs.QuickJsInterruptedException
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.evaluate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * One disposable runtime per operation: no leaked globals, concurrent use, or retained rejected
 * promises. Persistent state must eventually go through a versioned host storage capability.
 * This prototype only loads trusted, bundled source. It is not an untrusted-plugin installer.
 */
class QuickJsPlugin(
    val plugin: PluginPackage,
    private val http: PluginHttp,
    private val executionTimeoutMs: Long = 1_000,
    private val operationTimeoutMs: Long = 20_000,
) {
    private val slots = Semaphore(2)
    private val policy = HttpPolicy(plugin.manifest.allowedHosts)
    private val json = PluginPackage.json

    suspend fun invoke(method: String, args: JsonElement = JsonObject(emptyMap())): JsonElement {
        if (method !in plugin.manifest.methods) {
            throw PortablePluginException("UNSUPPORTED", "Plugin does not support $method")
        }
        val request = args.toString()
        if (request.toByteArray().size > 262_144) {
            throw PortablePluginException("INVALID_REQUEST", "Plugin request is too large")
        }
        return withTimeout(operationTimeoutMs) {
            slots.withPermit {
                withContext(Dispatchers.Default) {
                    val engine = QuickJs.create(Dispatchers.IO)
                    try {
                        engine.memoryLimit = 32L * 1024 * 1024
                        engine.maxStackSize = 512L * 1024
                        engine.evaluationTimeoutMillis = executionTimeoutMs
                        engine.asyncFunction<String, String>("__hostHttp") { raw ->
                            try {
                                val input = json.decodeFromString<PluginHttpRequest>(raw)
                                policy.validate(input)
                                val response = http.request(input)
                                if (response.body.toByteArray().size > 2_097_152) {
                                    throw PortablePluginException("NETWORK", "HTTP response is too large")
                                }
                                "{\"ok\":true,\"value\":${json.encodeToString(response)}}"
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Exception) {
                                val code = (error as? PortablePluginException)?.code
                                    ?: if (error is IOException) "NETWORK" else "INVALID_REQUEST"
                                "{\"ok\":false,\"error\":{\"code\":${JsonPrimitive(code)},\"message\":${JsonPrimitive(error.message)},\"retryable\":${error is IOException}}}"
                            }
                        }
                        engine.evaluate<Any?>(BOOTSTRAP, filename = "viper-host.js")
                        engine.evaluate<Any?>(plugin.source, filename = plugin.manifest.entry)
                        val output = engine.evaluate<String>(
                            """JSON.stringify(await __viperInvoke(${JsonPrimitive(method)}, $request))""",
                            filename = "viper-invoke.js",
                        )
                        if (output.toByteArray().size > 524_288) {
                            throw PortablePluginException("INVALID_RESULT", "Plugin result exceeds 512 KiB; paginate it")
                        }
                        val result = json.parseToJsonElement(output).jsonObject
                        if (result["ok"]?.jsonPrimitive?.booleanOrNull != true) {
                            val error = result["error"]?.jsonObject ?: JsonObject(emptyMap())
                            throw PortablePluginException(
                                error["code"]?.jsonPrimitive?.contentOrNull ?: "INTERNAL",
                                error["message"]?.jsonPrimitive?.contentOrNull ?: "Plugin operation failed",
                                error["retryable"]?.jsonPrimitive?.booleanOrNull ?: false,
                            )
                        }
                        result["value"] ?: JsonNull
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: QuickJsInterruptedException) {
                        throw PortablePluginException("TIMEOUT", "JavaScript execution limit exceeded", cause = error)
                    } catch (error: QuickJsException) {
                        throw PortablePluginException("INTERNAL", "Plugin JavaScript failed: ${error.message}", cause = error)
                    } finally {
                        engine.close()
                    }
                }
            }
        }
    }

    private companion object {
        // Only JSON strings cross the native binding. The same host.http contract can be provided
        // by an iOS interpreter or a browser host (subject to CORS and restricted headers).
        val BOOTSTRAP = """
            globalThis.viperHost = Object.freeze({
                apiVersion: 1,
                http: async request => {
                    const result = JSON.parse(await __hostHttp(JSON.stringify(request)));
                    if (!result.ok) throw Object.assign(new Error(result.error.message), result.error);
                    return result.value;
                }
            });
            globalThis.__viperInvoke = async function(method, args) {
                try {
                    if (!globalThis.viperPlugin || typeof viperPlugin[method] !== 'function') {
                        return {ok: false, error: {code: 'UNSUPPORTED', message: 'Missing method: ' + method}};
                    }
                    const value = await viperPlugin[method](args, viperHost);
                    return {ok: true, value: value === undefined ? null : value};
                } catch (error) {
                    return {ok: false, error: {
                        code: error && typeof error.code === 'string' ? error.code : 'INTERNAL',
                        message: String(error && error.message || error),
                        retryable: error && error.retryable === true
                    }};
                }
            };
        """.trimIndent()
    }
}
