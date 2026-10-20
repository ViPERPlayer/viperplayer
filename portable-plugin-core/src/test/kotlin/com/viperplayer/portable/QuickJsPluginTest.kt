package com.viperplayer.portable

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickJsPluginTest {
    private fun runtime(source: String, http: PluginHttp = PluginHttp { error("Unexpected HTTP") }, cpuMs: Long = 1_000) =
        QuickJsPlugin(PluginPackage(PortableManifest("test.plugin", "Test", "1", 1,
            methods = setOf("run"), allowedHosts = setOf("example.com")), source), http, executionTimeoutMs = cpuMs)

    @Test fun asyncHttpReturnsJsonAndFreshGlobals() = runBlocking {
        var requests = 0
        val plugin = runtime("""
            globalThis.count = 0;
            globalThis.viperPlugin = {run: async (_, host) => {
                const response = await host.http({url: 'https://example.com/data'});
                return JSON.parse(response.body).value + ++count;
            }};
        """, PluginHttp {
            delay(10)
            requests++
            PluginHttpResponse(200, """{"value":41}""")
        })
        assertEquals(JsonPrimitive(42), plugin.invoke("run"))
        assertEquals(JsonPrimitive(42), plugin.invoke("run"))
        assertEquals(2, requests)
    }

    @Test fun deniedHostNeverReachesTransport() = runBlocking {
        var called = false
        val plugin = runtime("""
            globalThis.viperPlugin = {run: (_, host) => host.http({url:'https://evil.example/data'})};
        """, PluginHttp { called = true; PluginHttpResponse(200, "") })
        assertEquals("PERMISSION_DENIED", failure { plugin.invoke("run") }.code)
        assertFalse(called)
    }

    @Test fun thrownNullIsReportedAndUnsupportedMethodsDoNotExecute() = runBlocking {
        val plugin = runtime("globalThis.viperPlugin = {run: () => {throw null;}};")
        assertEquals("INTERNAL", failure { plugin.invoke("run") }.code)
        assertEquals("UNSUPPORTED", failure { plugin.invoke("missing") }.code)
    }

    @Test fun pluginErrorsRetainCodeAndRetryability() = runBlocking {
        val plugin = runtime("""
            globalThis.viperPlugin = {run: () => {throw {code:'RATE_LIMITED', message:'Later', retryable:true};}};
        """)
        val error = failure { plugin.invoke("run") }
        assertEquals("RATE_LIMITED", error.code)
        assertTrue(error.retryable)
    }

    @Test fun infiniteLoopIsInterrupted() = runBlocking {
        withTimeout(5_000) {
            val plugin = runtime("globalThis.viperPlugin = {run: () => {while(true) {}}};", cpuMs = 50)
            assertEquals("TIMEOUT", failure { plugin.invoke("run") }.code)
        }
    }

    @Test fun cancellationPropagatesIntoPendingHttp() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val plugin = runtime("globalThis.viperPlugin = {run: (_, h) => h.http({url:'https://example.com'})};", PluginHttp {
            entered.complete(Unit)
            try { awaitCancellation() } finally { cancelled.complete(Unit) }
        })
        withTimeout(5_000) {
            val call = async { plugin.invoke("run") }
            entered.await()
            call.cancelAndJoin()
            cancelled.await()
        }
    }

    @Test fun httpPolicyRejectsCredentialsPortsSchemesAndHeaders() {
        val policy = HttpPolicy(setOf("example.com"))
        for (url in listOf("http://example.com", "https://example.com.evil.test", "https://u@example.com", "https://example.com:8443", "https://127.0.0.1")) {
            assertEquals("PERMISSION_DENIED", runCatching { policy.validate(PluginHttpRequest(url)) }.exceptionOrNull().let { (it as PortablePluginException).code })
        }
        assertTrue(runCatching { policy.validate(PluginHttpRequest("https://example.com", headers = mapOf("Cookie" to "session=secret"))) }.isFailure)
        policy.validate(PluginHttpRequest("https://example.com/path"))
    }

    private suspend fun failure(block: suspend () -> Unit): PortablePluginException {
        try { block() } catch (error: PortablePluginException) { return error }
        error("Expected PortablePluginException")
    }
}
