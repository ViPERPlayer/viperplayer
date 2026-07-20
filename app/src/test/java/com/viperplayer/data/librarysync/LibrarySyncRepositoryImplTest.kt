package com.viperplayer.data.librarysync

import com.viperplayer.data.account.AccountApiResult
import com.viperplayer.domain.account.AccountRepository
import com.viperplayer.domain.account.AccountState
import com.viperplayer.domain.account.AccountUser
import com.viperplayer.domain.librarysync.LibrarySyncResult
import com.viperplayer.domain.librarysync.SyncedPlaylist
import com.viperplayer.domain.librarysync.SyncedTrack
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [LibrarySyncRepositoryImpl]: that each method runs through the account
 * [AccountRepository.withBackendAuth] seam (so the current token is attached), and that the transport
 * outcome + DTOs are mapped into the domain [LibrarySyncResult] / models.
 */
class LibrarySyncRepositoryImplTest {

    private val BASE = "https://backend.test"

    private fun api(status: HttpStatusCode, body: String): LibraryApi =
        LibraryApi(
            HttpClient(
                MockEngine {
                    respond(
                        content = ByteReadChannel(body),
                        status = status,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                },
            ),
        ) { BASE }

    /** A fake account repo whose withBackendAuth just supplies a fixed token and delegates to the call. */
    private class FakeAccountRepository(
        private val token: String?,
    ) : AccountRepository {
        var authCalls = 0
        override val state: Flow<AccountState> = emptyFlow()
        override val isConfigured: Boolean = true
        override suspend fun register(email: String, password: String, displayName: String?) =
            throw UnsupportedOperationException()
        override suspend fun login(email: String, password: String) = throw UnsupportedOperationException()
        override suspend fun changePassword(current: String, new: String) = throw UnsupportedOperationException()
        override suspend fun deleteAccount(password: String) = throw UnsupportedOperationException()
        override suspend fun refreshProfile(): AccountUser? = null
        override suspend fun logout() {}
        override suspend fun validAccessToken(): String? = token
        override suspend fun <T> withBackendAuth(
            call: suspend (accessToken: String) -> AccountApiResult<T>,
        ): AccountApiResult<T> {
            authCalls++
            val t = token ?: return AccountApiResult.Unauthenticated
            return call(t)
        }
    }

    @Test
    fun getLibrary_runsThroughAuth_andMapsSnapshot() = runTest {
        val account = FakeAccountRepository("tok-1")
        val repo = LibrarySyncRepositoryImpl(
            api(HttpStatusCode.OK, """{"playlists":[{"id":"p1","name":"n","revision":2}],"likedTracks":[],"revision":9}"""),
            account,
        )

        val result = repo.getLibrary()

        assertEquals(1, account.authCalls)
        assertTrue(result is LibrarySyncResult.Success)
        val snapshot = (result as LibrarySyncResult.Success).value
        assertEquals(9L, snapshot.revision)
        assertEquals("p1", snapshot.playlists.single().id)
    }

    @Test
    fun getLibrary_noToken_isUnauthenticated_withoutCallingApi() = runTest {
        val account = FakeAccountRepository(null)
        // Engine would error if hit — proves the auth seam short-circuits before any request.
        val api = LibraryApi(HttpClient(MockEngine { error("should not be called") })) { BASE }
        val repo = LibrarySyncRepositoryImpl(api, account)

        assertEquals(LibrarySyncResult.Unauthenticated, repo.getLibrary())
    }

    @Test
    fun upsertPlaylist_mapsConflictResult() = runTest {
        val account = FakeAccountRepository("tok")
        val repo = LibrarySyncRepositoryImpl(
            api(HttpStatusCode.OK, """{"playlist":{"id":"p1","name":"server","revision":12},"conflict":true}"""),
            account,
        )

        val result = repo.upsertPlaylist(
            SyncedPlaylist(id = "p1", name = "local", tracks = listOf(SyncedTrack("testsource", "t1"))),
            baseRevision = 1,
        )

        assertTrue(result is LibrarySyncResult.Success)
        val value = (result as LibrarySyncResult.Success).value
        assertTrue(value.conflict)
        assertEquals(12L, value.playlist.revision)
        assertEquals("server", value.playlist.name)
    }

    @Test
    fun deletePlaylist_mapsRevision() = runTest {
        val repo = LibrarySyncRepositoryImpl(api(HttpStatusCode.OK, """{"revision":21}"""), FakeAccountRepository("tok"))
        val result = repo.deletePlaylist("p1")
        assertEquals(LibrarySyncResult.Success(21L), result)
    }

    @Test
    fun setLike_mapsRevision() = runTest {
        val repo = LibrarySyncRepositoryImpl(api(HttpStatusCode.OK, """{"revision":4}"""), FakeAccountRepository("tok"))
        val result = repo.setLike(SyncedTrack("local", "s1"), liked = true)
        assertEquals(LibrarySyncResult.Success(4L), result)
    }

    @Test
    fun serverError_mapsToNetworkError() = runTest {
        val repo = LibrarySyncRepositoryImpl(
            api(HttpStatusCode.ServiceUnavailable, """{"error":"down"}"""),
            FakeAccountRepository("tok"),
        )
        assertEquals(LibrarySyncResult.NetworkError, repo.deletePlaylist("p1"))
    }
}
