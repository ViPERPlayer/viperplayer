package com.viperplayer.data.repository

import com.viperplayer.data.account.AccountApiResult
import com.viperplayer.data.social.CreateSessionResponseDto
import com.viperplayer.data.social.DeviceIdProvider
import com.viperplayer.data.social.FakeJamSocketClient
import com.viperplayer.data.social.FrameDto
import com.viperplayer.data.social.JamSocketClient
import com.viperplayer.data.social.JamSocketState
import com.viperplayer.data.social.JoinSessionResponseDto
import com.viperplayer.data.social.MemberDto
import com.viperplayer.data.social.SessionApi
import com.viperplayer.data.social.SessionApiResult
import com.viperplayer.data.social.SessionDeltaDto
import com.viperplayer.data.social.SessionSnapshotDto
import com.viperplayer.data.social.DELTA_MEMBER_JOINED
import com.viperplayer.data.social.DELTA_MEMBER_LEFT
import com.viperplayer.data.social.FRAME_SESSION_DELTA
import com.viperplayer.data.social.FRAME_SESSION_SNAPSHOT
import com.viperplayer.domain.account.AccountRepository
import com.viperplayer.domain.account.AccountState
import com.viperplayer.domain.account.AccountUser
import com.viperplayer.domain.account.AuthResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lifecycle tests for [RealListenTogetherRepositoryImpl] over a fake [SessionApi] result and a fake
 * [JamSocketClient]. Verifies: startSession publishes a mapped session and opens the socket with the
 * returned jwt; incoming membership frames update the participant list on `currentSession`; a
 * disconnect frame clears it; and leaveSession cancels the socket collector + clears the session.
 */
class RealListenTogetherRepositoryImplTest {

    // --- Fakes ---

    /** A [SessionApi] subclass whose create/join return scripted results (its HttpClient is never used). */
    private class FakeSessionApi(
        var createResult: SessionApiResult<CreateSessionResponseDto>,
        var joinResult: SessionApiResult<JoinSessionResponseDto>,
    ) : SessionApi(HttpClient(MockEngine { error("unused") }), { "https://backend.test" }) {
        var lastCreate: Triple<String, String, String>? = null
        var lastJoin: List<String>? = null

        override suspend fun createSession(deviceId: String, userId: String, name: String): SessionApiResult<CreateSessionResponseDto> {
            lastCreate = Triple(deviceId, userId, name)
            return createResult
        }

        override suspend fun joinSession(code: String, deviceId: String, userId: String, name: String): SessionApiResult<JoinSessionResponseDto> {
            lastJoin = listOf(code, deviceId, userId, name)
            return joinResult
        }
    }

    private class FakeDeviceIdProvider(private val id: String) : DeviceIdProvider {
        override suspend fun deviceId(): String = id
    }

    private class FakeAccountRepository(user: AccountUser?) : AccountRepository {
        override val state: Flow<AccountState> = MutableStateFlow(AccountState(user = user, isSignedIn = user != null))
        override val isConfigured: Boolean = true
        override suspend fun register(email: String, password: String, displayName: String?): AuthResult = AuthResult.NetworkError
        override suspend fun login(email: String, password: String): AuthResult = AuthResult.NetworkError
        override suspend fun logout() {}
        override suspend fun validAccessToken(): String? = null
        override suspend fun <T> withBackendAuth(call: suspend (accessToken: String) -> AccountApiResult<T>): AccountApiResult<T> =
            AccountApiResult.Unauthenticated
    }

    // --- Helpers ---

    private fun member(deviceId: String, name: String, role: String = "MEMBER") =
        MemberDto(userId = "u-$deviceId", deviceId = deviceId, name = name, role = role, presence = true)

    private fun snapshotFrame(host: MemberDto, members: List<MemberDto>) = FrameDto(
        type = FRAME_SESSION_SNAPSHOT,
        sessionSnapshot = SessionSnapshotDto(sessionId = "s-1", mode = "JAM", host = host, members = members),
    )

    /**
     * Builds the repo under test. The socket collector runs on an [UnconfinedTestDispatcher] tied to
     * the test's scheduler so a launched flow starts eagerly (no lost background work), while
     * [awaitCancellation]-style long-lived collectors still cancel deterministically on leave.
     */
    private fun TestScope.repo(
        api: SessionApi,
        socket: JamSocketClient,
        deviceId: String = "me-dev",
        user: AccountUser? = null,
    ) = RealListenTogetherRepositoryImpl(
        sessionApi = api,
        socketClient = socket,
        deviceIdStore = FakeDeviceIdProvider(deviceId),
        accountRepository = FakeAccountRepository(user),
        scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
    )

    private fun createResponse(host: MemberDto, members: List<MemberDto>) = CreateSessionResponseDto(
        sessionId = "s-1",
        code = "ABCD-EFG",
        inviteUrl = "https://viper.player/jam/abcdefg",
        jwt = "host-jwt",
        snapshot = SessionSnapshotDto(sessionId = "s-1", mode = "JAM", host = host, members = members),
    )

    // --- Tests ---

    @Test
    fun startSession_publishesMappedSession_asHost_andOpensSocketWithJwt() = runTest {
        val host = member("me-dev", "Alice", role = "HOST")
        val api = FakeSessionApi(
            createResult = SessionApiResult.Success(createResponse(host, listOf(host))),
            joinResult = SessionApiResult.Rejected("n/a"),
        )
        val socket = FakeJamSocketClient(frames = emptyList())
        val repo = repo(api, socket, deviceId = "me-dev", user = AccountUser("me", "a@b.com", "Alice"))

        val result = repo.startSession()
        advanceUntilIdle()

        assertTrue(result.isSuccess)
        val session = repo.currentSession.value!!
        assertTrue(session.isHost)
        assertEquals("ABCD-EFG", session.code)
        assertEquals("Alice", session.hostName)
        assertEquals(1, session.participants.size)
        assertTrue(session.participants.single().isSelf)

        // Identity forwarded to the API; socket opened with the returned host jwt.
        assertEquals(Triple("me-dev", "me", "Alice"), api.lastCreate)
        assertEquals("host-jwt", socket.connectedJwt)
        assertEquals("wss://backend.test/ws", socket.connectedUrl)
    }

    @Test
    fun startSession_deltaFromSocket_updatesParticipants() = runTest {
        val host = member("me-dev", "Alice", role = "HOST")
        val api = FakeSessionApi(
            createResult = SessionApiResult.Success(createResponse(host, listOf(host))),
            joinResult = SessionApiResult.Rejected("n/a"),
        )
        // Socket replays: initial snapshot (1 member) then a member_joined delta (2 members).
        val socket = FakeJamSocketClient(
            frames = listOf(
                snapshotFrame(host, listOf(host)),
                FrameDto(type = FRAME_SESSION_DELTA, sessionDelta = SessionDeltaDto(kind = DELTA_MEMBER_JOINED, memberJoined = member("bob-dev", "Bob"))),
            ),
        )
        val repo = repo(api, socket, deviceId = "me-dev")

        repo.startSession()
        advanceUntilIdle()

        val session = repo.currentSession.value!!
        assertEquals(2, session.participants.size)
        assertTrue(session.participants.any { it.name == "Bob" })
        // Code + invite URL are carried over from the REST response onto the live snapshot.
        assertEquals("ABCD-EFG", session.code)
    }

    @Test
    fun socketDisconnect_clearsCurrentSession() = runTest {
        val host = member("me-dev", "Alice", role = "HOST")
        val api = FakeSessionApi(
            createResult = SessionApiResult.Success(createResponse(host, listOf(host))),
            joinResult = SessionApiResult.Rejected("n/a"),
        )
        val socket = FakeJamSocketClient(frames = listOf(snapshotFrame(host, listOf(host))), disconnectAfter = true)
        val repo = repo(api, socket, deviceId = "me-dev")

        repo.startSession()
        advanceUntilIdle()

        assertNull(repo.currentSession.value)
    }

    @Test
    fun joinSession_publishesGuestSession_andForwardsCode() = runTest {
        val host = member("host-dev", "Host", role = "HOST")
        val me = member("me-dev", "Nexus 5")
        val api = FakeSessionApi(
            createResult = SessionApiResult.Rejected("n/a"),
            joinResult = SessionApiResult.Success(
                JoinSessionResponseDto(
                    sessionId = "s-9",
                    jwt = "guest-jwt",
                    snapshot = SessionSnapshotDto(sessionId = "s-9", mode = "JAM", host = host, members = listOf(host, me)),
                ),
            ),
        )
        val socket = FakeJamSocketClient(frames = emptyList())
        val repo = repo(api, socket, deviceId = "me-dev")

        val result = repo.joinSession("https://viper.player/jam/abcdef")
        advanceUntilIdle()

        assertTrue(result.isSuccess)
        val session = repo.currentSession.value!!
        assertFalse(session.isHost)
        assertEquals("Host", session.hostName)
        assertEquals(2, session.participants.size)
        assertTrue(session.participants.single { it.id == "me-dev" }.isSelf)
        // parseCode normalised the pasted invite URL to the 6-char code before the REST call.
        assertEquals("ABCDEF", api.lastJoin?.first())
        assertEquals("guest-jwt", socket.connectedJwt)
    }

    @Test
    fun joinSession_invalidCode_failsWithoutHittingApi() = runTest {
        val api = FakeSessionApi(SessionApiResult.Rejected("n/a"), SessionApiResult.Rejected("n/a"))
        val repo = repo(api, FakeJamSocketClient(emptyList()))

        val result = repo.joinSession("!!")
        assertTrue(result.isFailure)
        assertNull(api.lastJoin)
    }

    @Test
    fun joinSession_backendRejects_surfacesFailure() = runTest {
        val api = FakeSessionApi(
            createResult = SessionApiResult.Rejected("n/a"),
            joinResult = SessionApiResult.Rejected("session not found"),
        )
        val repo = repo(api, FakeJamSocketClient(emptyList()))

        val result = repo.joinSession("ABCDEF")
        advanceUntilIdle()
        assertTrue(result.isFailure)
        assertEquals("session not found", result.exceptionOrNull()?.message)
        assertNull(repo.currentSession.value)
    }

    @Test
    fun leaveSession_cancelsSocket_andClearsSession() = runTest {
        val host = member("me-dev", "Alice", role = "HOST")
        val api = FakeSessionApi(
            createResult = SessionApiResult.Success(createResponse(host, listOf(host))),
            joinResult = SessionApiResult.Rejected("n/a"),
        )
        // A socket that stays "open" (never disconnects) so we can observe leaveSession tearing it down.
        val socket = CountingSocketClient()
        val repo = repo(api, socket, deviceId = "me-dev")

        repo.startSession()
        advanceUntilIdle()
        assertTrue(socket.collecting)
        assertNotNull(repo.currentSession.value)

        repo.leaveSession()
        advanceUntilIdle()

        assertNull(repo.currentSession.value)
        assertFalse(socket.collecting) // collector cancelled → socket torn down (server treats as leave)
    }

    /** A socket client whose flow never completes, exposing whether a collector is currently active. */
    private class CountingSocketClient : JamSocketClient {
        @Volatile var collecting = false
        override fun connect(httpBaseUrl: String, jwt: String): Flow<JamSocketState> =
            flow {
                collecting = true
                try {
                    awaitCancellation()
                } finally {
                    collecting = false
                }
            }
    }

    private fun assertNotNull(value: Any?) = assertTrue(value != null)
}
