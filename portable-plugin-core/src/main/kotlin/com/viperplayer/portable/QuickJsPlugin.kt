package com.viperplayer.portable

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.QuickJsException
import com.dokar.quickjs.QuickJsInterruptedException
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.evaluate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.net.URI
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull

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
    private val browserFactory: PluginBrowserFactory? = null,
    private val playbackTimeoutMs: Long = 60_000,
) {
    private val slots = Semaphore(2)
    private val policy = HttpPolicy(plugin.manifest.allowedHosts, plugin.manifest.headOnlyDomains)
    private val json = PluginPackage.json

    suspend fun invoke(method: String, args: JsonElement = JsonObject(emptyMap())): JsonElement {
        if (method !in plugin.manifest.methods) {
            throw PortablePluginException("UNSUPPORTED", "Plugin does not support $method")
        }
        val request = args.toString()
        if (request.toByteArray().size > 262_144) {
            throw PortablePluginException("INVALID_REQUEST", "Plugin request is too large")
        }
        return withTimeout(if (method == "resolveStream") playbackTimeoutMs else operationTimeoutMs) {
            slots.withPermit {
                withContext(Dispatchers.Default) {
                    val engine = QuickJs.create(Dispatchers.IO)
                    val browsers = mutableMapOf<Int, PluginBrowserSession>()
                    val browserLock = Mutex()
                    var nextBrowserId = 0
                    val hasBrowser = browserFactory != null && "browser" in plugin.manifest.capabilities
                    try {
                        engine.memoryLimit = (if (method == "resolveStream") 96L else 32L) * 1024 * 1024
                        engine.maxStackSize = 512L * 1024
                        engine.evaluationTimeoutMillis = executionTimeoutMs
                        engine.asyncFunction<String, String>("__hostHttp") { raw ->
                            try {
                                val input = json.decodeFromString<PluginHttpRequest>(raw)
                                policy.validate(input)
                                val response = http.request(input)
                                if (response.body.toByteArray().size > input.maxResponseBytes) {
                                    throw PortablePluginException("NETWORK", "HTTP response is too large")
                                }
                                "{\"ok\":true,\"value\":${json.encodeToString(response)}}"
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Exception) {
                                hostError(error)
                            }
                        }
                        val scriptLock = Mutex()
                        var scriptCalls = 0
                        engine.asyncFunction<String, String>("__hostScript") { raw ->
                            try {
                                if (plugin.manifest.apiVersion < 2) throw PortablePluginException("UNSUPPORTED", "Requires API v2")
                                if (raw.toByteArray().size > 16_777_216) throw PortablePluginException("INVALID_REQUEST", "Script input exceeds 16 MiB")
                                val input = json.parseToJsonElement(raw).jsonObject
                                val value = scriptLock.withLock {
                                    if (++scriptCalls > 2) throw PortablePluginException("INVALID_REQUEST", "At most two isolated evaluations per operation")
                                    IsolatedJavaScript.evaluate(input.getValue("script").jsonPrimitive.content, input["args"] ?: JsonNull)
                                }
                                "{\"ok\":true,\"value\":$value}"
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Exception) {
                                hostError(error)
                            }
                        }
                        engine.asyncFunction<String, String>("__hostBrowser") { raw ->
                            try {
                                if (!hasBrowser) throw PortablePluginException("UNSUPPORTED", "Browser capability unavailable")
                                if (raw.toByteArray().size > 12_582_912) throw PortablePluginException("INVALID_REQUEST", "Browser input exceeds 12 MiB")
                                val input = json.parseToJsonElement(raw).jsonObject
                                val value = browserLock.withLock {
                                    when (input["op"]?.jsonPrimitive?.contentOrNull) {
                                        "open" -> {
                                            if (browsers.size >= 2) throw PortablePluginException("INVALID_REQUEST", "At most two browser sessions per operation")
                                            val origin = input.getValue("origin").jsonPrimitive.content
                                            val uri = URI(origin)
                                            policy.validate(PluginHttpRequest(origin))
                                            if (uri.rawQuery != null || uri.path !in listOf("", "/")) throw PortablePluginException("INVALID_REQUEST", "Expected an HTTPS origin")
                                            val userAgent = input["userAgent"]?.jsonPrimitive?.contentOrNull
                                            if (userAgent != null && (userAgent.length > 4096 || '\r' in userAgent || '\n' in userAgent)) throw PortablePluginException("INVALID_REQUEST", "Invalid user agent")
                                            val session = browserFactory.open(origin, userAgent)
                                            val id = ++nextBrowserId
                                            browsers[id] = session
                                            JsonPrimitive(id)
                                        }
                                        "evaluate" -> {
                                            val id = input["id"]?.jsonPrimitive?.intOrNull
                                            val session = browsers[id] ?: throw PortablePluginException("INVALID_REQUEST", "Unknown browser session")
                                            session.evaluate(input.getValue("script").jsonPrimitive.content, input["args"] ?: JsonNull)
                                        }
                                        "close" -> {
                                            browsers.remove(input["id"]?.jsonPrimitive?.intOrNull)?.close()
                                            JsonNull
                                        }
                                        else -> throw PortablePluginException("INVALID_REQUEST", "Unknown browser operation")
                                    }
                                }
                                if (value.toString().toByteArray().size > 524_288) throw PortablePluginException("INVALID_RESULT", "Browser result exceeds 512 KiB")
                                "{\"ok\":true,\"value\":$value}"
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Exception) {
                                hostError(error)
                            }
                        }
                        engine.evaluate<Any?>(BOOTSTRAP.replace("__API_VERSION__", plugin.manifest.apiVersion.toString()).replace("__HAS_BROWSER__", hasBrowser.toString()), filename = "viper-host.js")
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
                        try {
                            engine.close()
                        } finally {
                            withContext(NonCancellable) {
                                browsers.values.forEach { session -> runCatching { session.close() } }
                                browsers.clear()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun hostError(error: Exception): String {
        val code = (error as? PortablePluginException)?.code
            ?: if (error is IOException) "NETWORK" else "INVALID_REQUEST"
        val retryable = (error as? PortablePluginException)?.retryable ?: (error is IOException)
        return "{\"ok\":false,\"error\":{\"code\":${JsonPrimitive(code)},\"message\":${JsonPrimitive(error.message)},\"retryable\":$retryable}}"
    }

    private companion object {
        // Only JSON strings cross the native binding. The same host.http contract can be provided
        // by an iOS interpreter or a browser host (subject to CORS and restricted headers).
        val BOOTSTRAP = """
            const browserCall = async request => {
                const result = JSON.parse(await __hostBrowser(JSON.stringify(request)));
                if (!result.ok) throw Object.assign(new Error(result.error.message), result.error);
                return result.value;
            };
            globalThis.viperHost = Object.freeze({
                apiVersion: __API_VERSION__,
                javascript: __API_VERSION__ >= 2 ? Object.freeze({
                    evaluate: async request => {
                        const result = JSON.parse(await __hostScript(JSON.stringify(request)));
                        if (!result.ok) throw Object.assign(new Error(result.error.message), result.error);
                        return result.value;
                    }
                }) : null,
                browser: __HAS_BROWSER__ ? Object.freeze({
                    open: request => browserCall({...request, op: 'open'}),
                    evaluate: request => browserCall({...request, op: 'evaluate'}),
                    close: id => browserCall({op: 'close', id})
                }) : null,
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
