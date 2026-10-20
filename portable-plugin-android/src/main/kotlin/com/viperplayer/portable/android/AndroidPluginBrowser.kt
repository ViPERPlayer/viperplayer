package com.viperplayer.portable.android

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.viperplayer.portable.PluginBrowserFactory
import com.viperplayer.portable.PluginBrowserSession
import com.viperplayer.portable.PluginPackage
import com.viperplayer.portable.PortablePluginException
import java.io.ByteArrayInputStream
import java.util.UUID
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** An optional DOM execution capability. All networking remains in the permission-checked HTTP host. */
class AndroidPluginBrowser(context: Context) : PluginBrowserFactory {
    private val application = context.applicationContext

    override suspend fun open(origin: String, userAgent: String?): PluginBrowserSession {
        val session = Session(application)
        try {
            withTimeoutOrNull(5_000) { session.open(origin, userAgent) }
                ?: throw PortablePluginException("PLAYBACK", "Browser initialization timed out", true)
            return session
        } catch (error: Throwable) {
            withContext(NonCancellable) { session.close() }
            throw error
        }
    }

    private class Session(private val context: Context) : PluginBrowserSession {
        private val mutex = Mutex()
        private val main = Handler(Looper.getMainLooper())
        private var view: WebView? = null
        private var ready: CancellableContinuation<Unit>? = null
        private var pending: CancellableContinuation<JsonElement>? = null
        private var nonce: String? = null
        private var closed = false

        @SuppressLint("SetJavaScriptEnabled")
        suspend fun open(origin: String, userAgent: String?) = withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { continuation ->
                ready = continuation
                val browser = WebView(context)
                view = browser
                browser.settings.apply {
                    javaScriptEnabled = true
                    allowFileAccess = false
                    allowContentAccess = false
                    blockNetworkLoads = true
                    domStorageEnabled = false
                    setGeolocationEnabled(false)
                    javaScriptCanOpenWindowsAutomatically = false
                    if (userAgent != null) userAgentString = userAgent
                }
                browser.addJavascriptInterface(object {
                    @JavascriptInterface
                    fun complete(id: String, result: String) {
                        // Binder calls arrive off the main thread. Nonces also discard late results.
                        if (result.length > 524_288) {
                            main.post { if (nonce == id) fail("Browser result exceeds 512 KiB") }
                            return
                        }
                        main.post {
                            if (nonce != id) return@post
                            val waiting = pending ?: return@post
                            pending = null
                            nonce = null
                            if (!waiting.isActive) return@post
                            try {
                                val envelope = PluginPackage.json.parseToJsonElement(result).jsonObject
                                if (envelope["ok"]?.jsonPrimitive?.booleanOrNull == true) {
                                    waiting.resume(envelope["value"] ?: JsonNull)
                                } else {
                                    waiting.resumeWithException(PortablePluginException("PLAYBACK",
                                        envelope["error"]?.jsonPrimitive?.contentOrNull ?: "Browser evaluation failed"))
                                }
                            } catch (error: Exception) {
                                waiting.resumeWithException(error)
                            }
                        }
                    }
                }, "__viperResult")
                browser.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        ready?.let { if (it.isActive) it.resume(Unit) }
                        ready = null
                    }
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) =
                        WebResourceResponse("text/plain", "UTF-8", 403, "Network disabled", emptyMap(), ByteArrayInputStream(byteArrayOf()))
                    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                        fail("Browser renderer exited")
                        destroy()
                        return true
                    }
                }
                browser.loadDataWithBaseURL(origin, "<!doctype html><html><head></head><body></body></html>", "text/html", "UTF-8", null)
            }
        }

        override suspend fun evaluate(script: String, args: JsonElement): JsonElement = mutex.withLock {
            try {
                withTimeoutOrNull(12_000) {
                    withContext(Dispatchers.Main.immediate) {
                        check(!closed && view != null) { "Browser session is closed" }
                        suspendCancellableCoroutine { continuation ->
                            val id = UUID.randomUUID().toString()
                            nonce = id
                            pending = continuation
                            // Quote source as data; no script is interpolated into HTML or executable syntax.
                            val expression = """
                                (() => {
                                  const complete = __viperResult.complete.bind(__viperResult);
                                  const id = ${JsonPrimitive(id)};
                                  Promise.resolve().then(() => (new Function('return (' + ${JsonPrimitive(script)} + ')'))()($args))
                                    .then(value => complete(id, JSON.stringify({ok:true,value:value === undefined ? null : value})))
                                    .catch(error => complete(id, JSON.stringify({ok:false,error:String(error && error.message || error)})));
                                })();
                            """.trimIndent()
                            view!!.evaluateJavascript(expression, null)
                        }
                    }
                } ?: throw PortablePluginException("PLAYBACK", "Browser evaluation timed out", true)
            } catch (error: Throwable) {
                withContext(NonCancellable) { close() }
                throw error
            }
        }

        private fun fail(message: String) {
            val error = PortablePluginException("PLAYBACK", message, true)
            ready?.let { if (it.isActive) it.resumeWithException(error) }
            pending?.let { if (it.isActive) it.resumeWithException(error) }
            ready = null
            pending = null
            nonce = null
        }

        private fun destroy() {
            closed = true
            view?.apply {
                stopLoading()
                removeJavascriptInterface("__viperResult")
                destroy()
            }
            view = null
        }

        override suspend fun close() = withContext(Dispatchers.Main.immediate) {
            if (!closed) {
                fail("Browser session closed")
                destroy()
            }
        }
    }
}
