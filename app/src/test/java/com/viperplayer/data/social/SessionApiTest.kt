package com.viperplayer.data.social

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Unit tests for [SessionApi]'s request building + response/error mapping, driven by a Ktor
 * [MockEngine] so no live server is needed. Locks down the wire contract with the backend's
 * `POST /sessions` + `POST /sessions/join` routes (method, path, JSON body, no bearer needed) and the
 * result mapping (404 join → Rejected, 5xx → NetworkError).
 */
class SessionApiTest {

    private val recorded = mutableListOf<HttpRequestData>()

    private fun api(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): SessionApi {
        recorded.clear()
        val engine = MockEngine { request ->
            recorded += request
            handler(request)
        }
        return SessionApi(HttpClient(engine), { _, _ -> "" }) { BASE }
    }

    private fun MockRequestHandleScope.json(status: HttpStatusCode, body: String) = respond(
        content = ByteReadChannel(body),
        status = status,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )

    // --- createSession ---

    @Test
    fun createSession_buildsPost_withBody_noBearer_andDecodesResponse() = runTest {
        val api = api {
            json(
                HttpStatusCode.OK,
                """
                {
                  "sessionId": "s-1",
                  "code": "ABCD-EFG",
                  "inviteUrl": "https://viper.player/jam/abcdefg",
                  "jwt": "host-jwt",
                  "snapshot": {
                    "sessionId": "s-1",
                    "mode": "JAM",
                    "host": {"userId":"u1","deviceId":"dev-1","name":"Alice","role":"HOST","presence":true},
                    "members": [
                      {"userId":"u1","deviceId":"dev-1","name":"Alice","role":"HOST","presence":true}
                    ]
                  }
                }
                """.trimIndent(),
            )
        }

        val result = api.createSession(deviceId = "dev-1", userId = "u1", name = "Alice")

        val req = recorded.single()
        assertEquals(HttpMethod.Post, req.method)
        assertEquals("$BASE/sessions", req.url.toString())
        // Create is a public route — no Authorization header is sent.
        assertNull(req.headers[HttpHeaders.Authorization])

        val body = req.bodyText()
        assertTrue(body.contains("\"deviceId\":\"dev-1\""))
        assertTrue(body.contains("\"userId\":\"u1\""))
        assertTrue(body.contains("\"name\":\"Alice\""))
        assertTrue(body.contains("\"mode\":\"JAM\""))

        assertTrue(result is SessionApiResult.Success)
        val value = (result as SessionApiResult.Success).value
        assertEquals("s-1", value.sessionId)
        assertEquals("ABCD-EFG", value.code)
        assertEquals("host-jwt", value.jwt)
        assertEquals("HOST", value.snapshot.host.role)
        assertEquals(1, value.snapshot.members.size)
        assertEquals("Alice", value.snapshot.members.single().name)
    }

    @Test
    fun createSession_serverError_mapsToNetworkError() = runTest {
        val api = api { json(HttpStatusCode.InternalServerError, """{"error":"boom"}""") }
        assertEquals(SessionApiResult.NetworkError, api.createSession("d", "u", "n"))
    }

    // --- joinSession ---

    @Test
    fun joinSession_buildsPost_toJoinRoute_withCode() = runTest {
        val api = api {
            json(
                HttpStatusCode.OK,
                """
                {
                  "sessionId": "s-9",
                  "jwt": "guest-jwt",
                  "snapshot": {
                    "sessionId": "s-9",
                    "mode": "JAM",
                    "host": {"deviceId":"host-dev","name":"Host","role":"HOST","presence":true},
                    "members": [
                      {"deviceId":"host-dev","name":"Host","role":"HOST","presence":true},
                      {"deviceId":"me-dev","name":"Bob","role":"MEMBER","presence":true}
                    ]
                  }
                }
                """.trimIndent(),
            )
        }

        val result = api.joinSession(code = "ABCDEF", deviceId = "me-dev", userId = "", name = "Bob")

        val req = recorded.single()
        assertEquals(HttpMethod.Post, req.method)
        assertEquals("$BASE/sessions/join", req.url.toString())
        val body = req.bodyText()
        assertTrue(body.contains("\"code\":\"ABCDEF\""))
        assertTrue(body.contains("\"deviceId\":\"me-dev\""))

        assertTrue(result is SessionApiResult.Success)
        val value = (result as SessionApiResult.Success).value
        assertEquals("s-9", value.sessionId)
        assertEquals("guest-jwt", value.jwt)
        assertEquals(2, value.snapshot.members.size)
    }

    @Test
    fun joinSession_notFound_mapsToRejected_withServerMessage() = runTest {
        val api = api { json(HttpStatusCode.NotFound, """{"error":"session not found"}""") }
        val result = api.joinSession("BADCODE", "d", "u", "n")
        assertTrue(result is SessionApiResult.Rejected)
        assertEquals("session not found", (result as SessionApiResult.Rejected).message)
    }

    @Test
    fun joinSession_rateLimited_mapsToRejected() = runTest {
        val api = api { json(HttpStatusCode.TooManyRequests, """{"error":"too many join attempts"}""") }
        val result = api.joinSession("ABCDEF", "d", "u", "n")
        assertTrue(result is SessionApiResult.Rejected)
    }

    @Test
    fun joinSession_networkFailure_mapsToNetworkError() = runTest {
        val api = SessionApi(HttpClient(MockEngine { throw IOException("offline") }), { _, _ -> "" }) { BASE }
        assertEquals(SessionApiResult.NetworkError, api.joinSession("ABCDEF", "d", "u", "n"))
    }

    @Test
    fun malformedBody_isRejected() = runTest {
        val api = api { json(HttpStatusCode.OK, "not json") }
        assertTrue(api.createSession("d", "u", "n") is SessionApiResult.Rejected)
    }

    // --- not configured ---

    @Test
    fun notConfigured_shortCircuits_withoutHittingEngine() = runTest {
        val engine = MockEngine { error("should not be called when unconfigured") }
        val api = SessionApi(HttpClient(engine), { _, _ -> "" }) { "REPLACE_WITH_REAL_VALUE" }

        assertEquals(SessionApiResult.NotConfigured, api.createSession("d", "u", "n"))
        assertEquals(SessionApiResult.NotConfigured, api.joinSession("c", "d", "u", "n"))
        assertNull(api.baseUrl)
    }

    private companion object {
        const val BASE = "https://backend.test"
    }
}

private fun HttpRequestData.bodyText(): String = (body as TextContent).text
