package com.viperplayer.data.librarysync

import com.viperplayer.data.account.AccountApiResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondOk
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
 * Unit tests for [LibraryApi]'s request building and response/error mapping, driven by a Ktor
 * [MockEngine] so no live server is needed. They lock down the wire contract with the backend's
 * `/library` routes (method, path, bearer header, JSON body) and that the response maps through the
 * shared `mapAccountHttpError` (401 → Unauthenticated, 5xx → NetworkError, …).
 */
class LibraryApiTest {

    private val recorded = mutableListOf<HttpRequestData>()

    /** Builds a [LibraryApi] whose engine records requests and answers with [handler]. */
    private fun api(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): LibraryApi {
        recorded.clear()
        val engine = MockEngine { request ->
            recorded += request
            handler(request)
        }
        return LibraryApi(HttpClient(engine)) { BASE }
    }

    private fun MockRequestHandleScope.json(status: HttpStatusCode, body: String) = respond(
        content = ByteReadChannel(body),
        status = status,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )

    // --- getLibrary ---

    @Test
    fun getLibrary_buildsGetRequest_withBearer_andDecodesSnapshot() = runTest {
        val api = api {
            json(
                HttpStatusCode.OK,
                """
                {
                  "playlists": [
                    {"id":"p1","name":"Road trip","tracks":[
                      {"pluginId":"testsource","sourceId":"t1","title":"Song","artist":"A","album":"B","artworkUrl":"u","durationMs":1000}
                    ],"revision":5,"updatedAtMs":42}
                  ],
                  "likedTracks": [{"pluginId":"local","sourceId":"s9"}],
                  "revision": 7
                }
                """.trimIndent(),
            )
        }

        val result = api.getLibrary("access-123")

        val req = recorded.single()
        assertEquals(HttpMethod.Get, req.method)
        assertEquals("$BASE/library", req.url.toString())
        assertEquals("Bearer access-123", req.headers[HttpHeaders.Authorization])

        assertTrue(result is AccountApiResult.Success)
        val snapshot = (result as AccountApiResult.Success).value
        assertEquals(7, snapshot.revision)
        assertEquals(1, snapshot.playlists.size)
        assertEquals("Road trip", snapshot.playlists[0].name)
        assertEquals(5, snapshot.playlists[0].revision)
        assertEquals("t1", snapshot.playlists[0].tracks.single().sourceId)
        assertEquals("local", snapshot.likedTracks.single().pluginId)
    }

    @Test
    fun getLibrary_unauthorized_mapsToUnauthenticated() = runTest {
        val api = api { json(HttpStatusCode.Unauthorized, """{"error":"token expired"}""") }
        assertEquals(AccountApiResult.Unauthenticated, api.getLibrary("t"))
    }

    @Test
    fun getLibrary_serverError_mapsToNetworkError() = runTest {
        val api = api { json(HttpStatusCode.InternalServerError, """{"error":"boom"}""") }
        assertEquals(AccountApiResult.NetworkError, api.getLibrary("t"))
    }

    @Test
    fun getLibrary_malformedBody_isRejected() = runTest {
        val api = api { json(HttpStatusCode.OK, "not json") }
        val result = api.getLibrary("t")
        assertTrue(result is AccountApiResult.Rejected)
    }

    // --- upsertPlaylist ---

    @Test
    fun upsertPlaylist_buildsPut_withPlaylistAndBaseRevision_body() = runTest {
        val api = api {
            json(HttpStatusCode.OK, """{"playlist":{"id":"p1","name":"n","revision":9},"conflict":false}""")
        }

        val playlist = PlaylistDto(
            id = "p1",
            name = "Road trip",
            tracks = listOf(TrackRefDto(pluginId = "testsource", sourceId = "t1")),
            revision = 4,
        )
        val result = api.upsertPlaylist("access-1", playlist, baseRevision = 4)

        val req = recorded.single()
        assertEquals(HttpMethod.Put, req.method)
        assertEquals("$BASE/library/playlists", req.url.toString())
        assertEquals("Bearer access-1", req.headers[HttpHeaders.Authorization])

        val body = req.bodyText()
        assertTrue(body.contains("\"baseRevision\":4"))
        assertTrue(body.contains("\"playlist\""))
        assertTrue(body.contains("\"id\":\"p1\""))
        assertTrue(body.contains("\"pluginId\":\"testsource\""))

        assertTrue(result is AccountApiResult.Success)
        val value = (result as AccountApiResult.Success).value
        assertEquals(9, value.playlist.revision)
        assertEquals(false, value.conflict)
    }

    @Test
    fun upsertPlaylist_conflict_isSuccessWithConflictFlag() = runTest {
        // A stale write is reported via the conflict flag on a 200 — NOT an HTTP error.
        val api = api {
            json(HttpStatusCode.OK, """{"playlist":{"id":"p1","name":"server copy","revision":12},"conflict":true}""")
        }
        val result = api.upsertPlaylist("t", PlaylistDto(id = "p1"), baseRevision = 1)
        assertTrue(result is AccountApiResult.Success)
        val value = (result as AccountApiResult.Success).value
        assertTrue(value.conflict)
        assertEquals(12, value.playlist.revision)
        assertEquals("server copy", value.playlist.name)
    }

    // --- deletePlaylist ---

    @Test
    fun deletePlaylist_buildsPost_toDeleteRoute_withPlaylistId() = runTest {
        val api = api { json(HttpStatusCode.OK, """{"revision":15}""") }

        val result = api.deletePlaylist("access-1", "p-42")

        val req = recorded.single()
        assertEquals(HttpMethod.Post, req.method)
        assertEquals("$BASE/library/playlists/delete", req.url.toString())
        assertTrue(req.bodyText().contains("\"playlistId\":\"p-42\""))

        assertTrue(result is AccountApiResult.Success)
        assertEquals(15L, (result as AccountApiResult.Success).value.revision)
    }

    // --- setLike ---

    @Test
    fun setLike_buildsPost_toLikesRoute_withTrackAndLiked() = runTest {
        val api = api { json(HttpStatusCode.OK, """{"revision":3}""") }

        val track = TrackRefDto(pluginId = "testsource", sourceId = "trk-1", title = "T")
        val result = api.setLike("access-1", track, liked = true)

        val req = recorded.single()
        assertEquals(HttpMethod.Post, req.method)
        assertEquals("$BASE/library/likes", req.url.toString())
        val body = req.bodyText()
        assertTrue(body.contains("\"liked\":true"))
        assertTrue(body.contains("\"sourceId\":\"trk-1\""))

        assertTrue(result is AccountApiResult.Success)
        assertEquals(3L, (result as AccountApiResult.Success).value.revision)
    }

    @Test
    fun setLike_networkFailure_mapsToNetworkError() = runTest {
        val api = LibraryApi(HttpClient(MockEngine { throw IOException("offline") })) { BASE }
        assertEquals(AccountApiResult.NetworkError, api.setLike("t", TrackRefDto("p", "s"), liked = false))
    }

    // --- not configured ---

    @Test
    fun notConfigured_shortCircuits_withoutHittingEngine() = runTest {
        val engine = MockEngine { error("should not be called when unconfigured") }
        val api = LibraryApi(HttpClient(engine)) { "REPLACE_WITH_REAL_VALUE" }

        assertEquals(AccountApiResult.NotConfigured, api.getLibrary("t"))
        assertEquals(AccountApiResult.NotConfigured, api.deletePlaylist("t", "p"))
        assertNull(api.baseUrl)
    }

    @Test
    fun baseUrl_trimsTrailingSlash() {
        val api = LibraryApi(HttpClient(MockEngine { respondOk() })) { "https://api.example.com/" }
        assertEquals("https://api.example.com", api.baseUrl)
    }

    private companion object {
        const val BASE = "https://backend.test"
    }
}

private fun HttpRequestData.bodyText(): String = (body as TextContent).text
