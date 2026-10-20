package com.viperplayer.portable

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class IsolatedJavaScriptTest {
    @Test fun remoteCodeHasNoHostBindingsOrSharedGlobals() = runBlocking {
        val source = """async args => {
          if (typeof viperHost !== 'undefined' || typeof __hostHttp !== 'undefined' || typeof fetch !== 'undefined' || typeof window !== 'undefined') throw Error('Host leaked');
          globalThis.counter = (globalThis.counter || 0) + 1;
          return args + counter;
        }"""
        repeat(2) { assertEquals(JsonPrimitive(42), IsolatedJavaScript.evaluate(source, JsonPrimitive(41))) }
    }

    @Test fun nonTerminatingCodeIsInterrupted() = runBlocking {
        val error = runCatching { IsolatedJavaScript.evaluate("() => {while(true){}}", JsonNull, executionTimeoutMs = 20) }.exceptionOrNull()
        assertEquals("TIMEOUT", (error as PortablePluginException).code)
    }
}
