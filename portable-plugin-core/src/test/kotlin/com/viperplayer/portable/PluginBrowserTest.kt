package com.viperplayer.portable

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginBrowserTest {
    private fun runtime(source: String, factory: PluginBrowserFactory? = null, granted: Boolean = true) = QuickJsPlugin(
        PluginPackage(PortableManifest("test.browser", "Browser test", "1", 2,
            methods = setOf("resolveStream"), allowedHosts = setOf("example.com"),
            capabilities = if (granted) setOf("browser") else emptySet()), source),
        PluginHttp { error("Unexpected HTTP") }, browserFactory = factory,
    )

    @Test fun browserIsOptionalAndRequiresManifestGrant() = runBlocking {
        val source = "globalThis.viperPlugin={resolveStream:(_,h)=>h.browser===null};"
        assertEquals(JsonPrimitive(true), runtime(source).invoke("resolveStream"))
        assertEquals(JsonPrimitive(true), runtime(source, PluginBrowserFactory { _, _ -> error("Not granted") }, false).invoke("resolveStream"))
    }

    @Test fun jsonRoundTripAndAutomaticCleanupOnSuccessAndFailure() = runBlocking {
        for (throwAfter in listOf(false, true)) {
            var closed = 0
            val plugin = runtime("""
                globalThis.viperPlugin={resolveStream:async(_,h)=>{
                  const id=await h.browser.open({origin:'https://example.com',userAgent:'Test'});
                  const value=await h.browser.evaluate({id,script:'async args => args',args:{quote:'"',number:42}});
                  ${if (throwAfter) "throw {code:'PLAYBACK',message:'fixture failure'};" else "return value.number;"}
                }};
            """, PluginBrowserFactory { origin, agent ->
                assertEquals("https://example.com", origin)
                assertEquals("Test", agent)
                object : PluginBrowserSession {
                    override suspend fun evaluate(script: String, args: JsonElement): JsonElement {
                        assertEquals("async args => args", script)
                        assertTrue(args.toString().contains("42"))
                        return args
                    }
                    override suspend fun close() { closed++ }
                }
            })
            val result = runCatching { plugin.invoke("resolveStream") }
            if (throwAfter) assertEquals("PLAYBACK", (result.exceptionOrNull() as PortablePluginException).code)
            else assertEquals(JsonPrimitive(42), result.getOrThrow())
            assertEquals(1, closed)
        }
    }

    @Test fun cancellationReachesBrowserAndClosesSession() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        var closed = false
        val plugin = runtime("""
            globalThis.viperPlugin={resolveStream:async(_,h)=>{
              const id=await h.browser.open({origin:'https://example.com'});
              return h.browser.evaluate({id,script:'async()=>{}'});
            }};
        """, PluginBrowserFactory { _, _ -> object : PluginBrowserSession {
            override suspend fun evaluate(script: String, args: JsonElement): JsonElement {
                entered.complete(Unit)
                try { awaitCancellation() } finally { cancelled.complete(Unit) }
            }
            override suspend fun close() { closed = true }
        } })
        withTimeout(5_000) {
            val call = async { plugin.invoke("resolveStream") }
            entered.await()
            call.cancelAndJoin()
            cancelled.await()
            assertTrue(closed)
        }
    }

    @Test fun browserOriginMustBeAnAllowedHttpsOrigin() = runBlocking {
        for (origin in listOf("https://evil.test", "https://example.com/path", "https://example.com?q=x", "http://example.com", "https://user@example.com")) {
            var opened = false
            val plugin = runtime("""
                globalThis.viperPlugin={resolveStream:(_,h)=>h.browser.open({origin:${JsonPrimitive(origin)}})};
            """, PluginBrowserFactory { _, _ -> opened = true; error("Must not open") })
            assertTrue(runCatching { plugin.invoke("resolveStream") }.isFailure)
            assertFalse(opened)
        }
    }

    @Test fun headOnlyDomainsDoNotGrantGetPostOrLookalikeHosts() {
        val policy = HttpPolicy(setOf("example.com"), setOf("media.test"))
        policy.validate(PluginHttpRequest("https://a.b.media.test/path", method = "HEAD"))
        policy.validate(PluginHttpRequest("https://media.test/path", method = "HEAD"))
        for (request in listOf(
            PluginHttpRequest("https://a.media.test/path"),
            PluginHttpRequest("https://a.media.test/path", method = "POST"),
            PluginHttpRequest("https://evilmedia.test/path", method = "HEAD"),
            PluginHttpRequest("https://media.test.evil/path", method = "HEAD"),
            PluginHttpRequest("https://example.com", maxResponseBytes = 8_388_609),
            PluginHttpRequest("https://example.com", timeoutMs = 10_001),
            PluginHttpRequest("https://example.com", method = "HEAD", body = "unexpected"),
        )) assertTrue(runCatching { policy.validate(request) }.isFailure)
    }
}
