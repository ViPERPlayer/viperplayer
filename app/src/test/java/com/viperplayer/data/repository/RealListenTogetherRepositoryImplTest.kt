package com.viperplayer.data.repository

import com.viperplayer.data.account.AccountApiResult
import com.viperplayer.data.social.CMD_NEXT
import com.viperplayer.data.social.CMD_PAUSE
import com.viperplayer.data.social.CMD_PLAY
import com.viperplayer.data.social.CMD_PREV
import com.viperplayer.data.social.CMD_SEEK
import com.viperplayer.data.social.CMD_TRACK
import com.viperplayer.data.social.CreateSessionResponseDto
import com.viperplayer.data.social.DELTA_MEMBER_JOINED
import com.viperplayer.data.social.DELTA_PLAYBACK
import com.viperplayer.data.social.DeviceIdProvider
import com.viperplayer.data.social.FRAME_SESSION_DELTA
import com.viperplayer.data.social.FRAME_SESSION_SNAPSHOT
import com.viperplayer.data.social.FakeJamConnection
import com.viperplayer.data.social.FakeJamSocketClient
import com.viperplayer.data.social.FrameDto
import com.viperplayer.data.social.JamClientFrame
import com.viperplayer.data.social.JamServerEvent
import com.viperplayer.data.social.JoinSessionResponseDto
import com.viperplayer.data.social.MediaRefDto
import com.viperplayer.data.social.MemberDto
import com.viperplayer.data.social.PermissionsDto
import com.viperplayer.data.social.SessionApi
import com.viperplayer.data.social.SessionApiResult
import com.viperplayer.data.social.SessionDeltaDto
import com.viperplayer.data.social.SessionSnapshotDto
import com.viperplayer.data.social.TimelineDto
import com.viperplayer.domain.account.AccountRepository
import com.viperplayer.domain.account.AccountState
import com.viperplayer.domain.account.AccountUser
import com.viperplayer.domain.account.AuthResult
import com.viperplayer.domain.model.SessionTrack
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
 * Lifecycle + sync-engine tests for [RealListenTogetherRepositoryImpl] over a fake [SessionApi] result
 * and a fake [JamConnection]. Verifies: startSession publishes a mapped session and opens the connection
 * with the returned jwt; membership frames update the participant list; a `playback` event folds into
 * `playback`; the `control*` actions enqueue the right [com.viperplayer.data.social.CommandDto]; a
 * disconnect clears state; and leaveSession closes the connection + clears playback.
 */
class RealListenTogetherRepositoryImplTest {

    // --- Fakes ---

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

    private fun snapshotDto(
        host: MemberDto,
        members: List<MemberDto>,
        permissions: PermissionsDto = PermissionsDto(),
        playback: TimelineDto = TimelineDto(),
    ) = SessionSnapshotDto(sessionId = "s-1", mode = "JAM", host = host, members = members, permissions = permissions, playback = playback)

    private fun snapshotFrame(host: MemberDto, members: List<MemberDto>, permissions: PermissionsDto = PermissionsDto()) = FrameDto(
        type = FRAME_SESSION_SNAPSHOT,
        sessionSnapshot = snapshotDto(host, members, permissions),
    )

    private fun TestScope.repo(
        api: SessionApi,
        socket: FakeJamSocketClient,
        deviceId: String = "me-dev",
        user: AccountUser? = null,
    ) = RealListenTogetherRepositoryImpl(
        sessionApi = api,
        socketClient = socket,
        deviceIdStore = FakeDeviceIdProvider(deviceId),
        accountRepository = FakeAccountRepository(user),
        scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        // Cap the clock loop so advanceUntilIdle terminates (no TimeResp is scripted → each ping just
        // times out; without a cap the loop would ping forever in virtual time).
        clockMaxPings = 1,
    )

    private fun createResponse(host: MemberDto, members: List<MemberDto>) = CreateSessionResponseDto(
        sessionId = "s-1",
        code = "ABCD-EFG",
        inviteUrl = "https://viper.player/jam/abcdefg",
        jwt = "host-jwt",
        snapshot = snapshotDto(host, members),
    )

    private fun hostApi(host: MemberDto, members: List<MemberDto>) = FakeSessionApi(
        createResult = SessionApiResult.Success(createResponse(host, members)),
        joinResult = SessionApiResult.Rejected("n/a"),
    )

    // --- Lifecycle tests ---

    @Test
    fun startSession_publishesMappedSession_asHost_andOpensConnectionWithJwt() = runTest {
        val host = member("me-dev", "Alice", role = "HOST")
        val api = hostApi(host, listOf(host))
        val socket = FakeJamSocketClient(FakeJamConnection())
        val repo = repo(api, socket, deviceId = "me-dev", user = AccountUser("me", "a@b.com", "Alice"))

        val result = repo.startSession()
        advanceUntilIdle()

        assertTrue(result.isSuccess)
        val session = repo.currentSession.value!!
        assertTrue(session.isHost)
        assertTrue(session.canControl)
        assertEquals("ABCD-EFG", session.code)
        assertEquals("Alice", session.hostName)
        assertEquals(1, session.participants.size)
        assertTrue(session.participants.single().isSelf)

        assertEquals(Triple("me-dev", "me", "Alice"), api.lastCreate)
        assertEquals("host-jwt", socket.connectedJwt)
        assertEquals("wss://backend.test/ws", socket.connectedUrl)
    }

    @Test
    fun membershipEvent_updatesParticipants() = runTest {
        val host = member("me-dev", "Alice", role = "HOST")
        val api = hostApi(host, listOf(host))
        val conn = FakeJamConnection(
            frames = listOf(
                snapshotFrame(host, listOf(host)),
                FrameDto(type = FRAME_SESSION_DELTA, sessionDelta = SessionDeltaDto(kind = DELTA_MEMBER_JOINED, memberJoined = member("bob-dev", "Bob"))),
            ),
        )
        val repo = repo(api, FakeJamSocketClient(conn), deviceId = "me-dev")

        repo.startSession()
        advanceUntilIdle()
        conn.emitScripted()
        advanceUntilIdle()

        val session = repo.currentSession.value!!
        assertEquals(2, session.participants.size)
        assertTrue(session.participants.any { it.name == "Bob" })
        assertEquals("ABCD-EFG", session.code)
    }

    @Test
    fun playbackEvent_foldsTimelineIntoPlayback() = runTest {
        val host = member("me-dev", "Alice", role = "HOST")
        val api = hostApi(host, listOf(host))
        val timeline = TimelineDto(
            epoch = 7,
            track = MediaRefDto(pluginId = "testsource", sourceId = "42", title = "Song", artist = "Artist", durationMs = 200_000),
            p0Us = 1_000_000,
            t0Us = 5_000_000,
            rate = 1.0f,
            effectiveAtUs = 5_000_000,
            controllerId = "me-dev",
        )
        val conn = FakeJamConnection(
            frames = listOf(
                snapshotFrame(host, listOf(host)),
                FrameDto(type = FRAME_SESSION_DELTA, sessionDelta = SessionDeltaDto(kind = DELTA_PLAYBACK, playback = timeline)),
            ),
        )
        val repo = repo(api, FakeJamSocketClient(conn), deviceId = "me-dev")

        repo.startSession()
        advanceUntilIdle()
        conn.emitScripted()
        advanceUntilIdle()

        val pb = repo.playback.value!!
        assertEquals(7, pb.epoch)
        assertEquals(1_000_000, pb.positionAnchorUs)
        assertEquals(5_000_000, pb.anchorServerTimeUs)
        assertEquals(1.0f, pb.rate)
        val track = pb.track!!
        assertEquals("testsource", track.pluginId)
        assertEquals("42", track.sourceId)
        // Extrapolation matches the backend formula: 1s past t0 → +1s of media.
        assertEquals(2_000_000, pb.positionUsAt(6_000_000))
    }

    @Test
    fun snapshotWithPlayback_seedsPlayback() = runTest {
        val host = member("me-dev", "Alice", role = "HOST")
        val api = hostApi(host, listOf(host))
        val conn = FakeJamConnection(
            frames = listOf(
                FrameDto(
                    type = FRAME_SESSION_SNAPSHOT,
                    sessionSnapshot = snapshotDto(
                        host, listOf(host),
                        playback = TimelineDto(epoch = 1, track = MediaRefDto(pluginId = "p", sourceId = "s"), rate = 0f),
                    ),
                ),
            ),
        )
        val repo = repo(api, FakeJamSocketClient(conn), deviceId = "me-dev")

        repo.startSession()
        advanceUntilIdle()
        conn.emitScripted()
        advanceUntilIdle()

        val pb = repo.playback.value!!
        assertEquals(1, pb.epoch)
        assertEquals(0f, pb.rate)
        assertEquals("p", pb.track!!.pluginId)
    }

    @Test
    fun disconnectEvent_clearsSessionAndPlayback() = runTest {
        val host = member("me-dev", "Alice", role = "HOST")
        val api = hostApi(host, listOf(host))
        val conn = FakeJamConnection(frames = listOf(snapshotFrame(host, listOf(host))), disconnectAfter = true)
        val repo = repo(api, FakeJamSocketClient(conn), deviceId = "me-dev")

        repo.startSession()
        advanceUntilIdle()
        conn.emitScripted()
        advanceUntilIdle()

        assertNull(repo.currentSession.value)
        assertNull(repo.playback.value)
        // A server-initiated drop must run the same cleanup as leaving: close the socket and clear
        // sync, so nothing keeps extrapolating from a dead clock.
        assertFalse(repo.synced.value)
        assertTrue("server disconnect must tear down the connection", conn.closed)
    }

    // --- canControl mapping ---

    @Test
    fun guestMember_canControl_reflectsPermission() = runTest {
        val host = member("host-dev", "Host", role = "HOST")
        val me = member("me-dev", "Guest", role = "MEMBER")
        val api = FakeSessionApi(
            createResult = SessionApiResult.Rejected("n/a"),
            joinResult = SessionApiResult.Success(
                JoinSessionResponseDto(
                    sessionId = "s-9",
                    jwt = "guest-jwt",
                    snapshot = snapshotDto(host, listOf(host, me), permissions = PermissionsDto(guestsCanControl = false)),
                ),
            ),
        )
        val conn = FakeJamConnection()
        val repo = repo(api, FakeJamSocketClient(conn), deviceId = "me-dev")

        repo.joinSession("ABCDEF")
        advanceUntilIdle()

        // Guest with guestsCanControl=false → cannot control.
        assertFalse(repo.currentSession.value!!.canControl)

        // A permissions flip to true (via a fresh snapshot) grants control.
        conn.emit(
            JamServerEvent.Membership(
                snapshotDto(host, listOf(host, me), permissions = PermissionsDto(guestsCanControl = true)),
            ),
        )
        advanceUntilIdle()
        assertTrue(repo.currentSession.value!!.canControl)
    }

    // --- Transport controls ---

    @Test
    fun controls_enqueueCorrectCommandFrames_whenPermitted() = runTest {
        val host = member("me-dev", "Alice", role = "HOST")
        val api = hostApi(host, listOf(host))
        val conn = FakeJamConnection()
        val repo = repo(api, FakeJamSocketClient(conn), deviceId = "me-dev")

        repo.startSession()
        advanceUntilIdle()

        repo.controlPlay()
        repo.controlPause()
        repo.controlSeek(123_456)
        repo.controlSkipNext()
        repo.controlSkipPrevious()
        val track = SessionTrack("testsource", "42", "T", "A", "", "", 100)
        repo.controlSetTrack(track, positionUs = 0)
        advanceUntilIdle()

        val commands = conn.sent.filterIsInstance<JamClientFrame.Command>().map { it.command }
        assertEquals(
            listOf(CMD_PLAY, CMD_PAUSE, CMD_SEEK, CMD_NEXT, CMD_PREV, CMD_TRACK),
            commands.map { it.kind },
        )
        assertEquals(123_456, commands.single { it.kind == CMD_SEEK }.seek!!.positionUs)
        assertEquals("testsource", commands.single { it.kind == CMD_TRACK }.track!!.mediaRef.pluginId)
        // forSeq is stamped and monotonically increasing across commands.
        val seqs = commands.map { it.forSeq }
        assertEquals(seqs.sorted(), seqs)
        assertTrue(seqs.toSet().size == seqs.size) // all distinct
    }

    @Test
    fun controlSetTrack_withPosition_alsoSendsSeek() = runTest {
        val host = member("me-dev", "Alice", role = "HOST")
        val conn = FakeJamConnection()
        val repo = repo(hostApi(host, listOf(host)), FakeJamSocketClient(conn), deviceId = "me-dev")

        repo.startSession()
        advanceUntilIdle()
        repo.controlSetTrack(SessionTrack("p", "s", "T", "A", "", "", 100), positionUs = 5_000)
        advanceUntilIdle()

        val kinds = conn.sent.filterIsInstance<JamClientFrame.Command>().map { it.command.kind }
        assertEquals(listOf(CMD_TRACK, CMD_SEEK), kinds)
    }

    @Test
    fun controls_areNoOp_whenNotPermitted() = runTest {
        val host = member("host-dev", "Host", role = "HOST")
        val me = member("me-dev", "Listener", role = "LISTENER")
        val api = FakeSessionApi(
            createResult = SessionApiResult.Rejected("n/a"),
            joinResult = SessionApiResult.Success(
                JoinSessionResponseDto(
                    sessionId = "s-9",
                    jwt = "guest-jwt",
                    snapshot = snapshotDto(host, listOf(host, me)),
                ),
            ),
        )
        val conn = FakeJamConnection()
        val repo = repo(api, FakeJamSocketClient(conn), deviceId = "me-dev")

        repo.joinSession("ABCDEF")
        advanceUntilIdle()
        assertFalse(repo.currentSession.value!!.canControl)

        repo.controlPlay()
        repo.controlSeek(1)
        advanceUntilIdle()

        // The clock may send TimeReqs; assert no *command* frames escaped for a listener.
        assertTrue(
            "listener's transport commands must not be sent",
            conn.sent.filterIsInstance<JamClientFrame.Command>().isEmpty(),
        )
    }

    @Test
    fun controls_areNoOp_whenNotInSession() = runTest {
        val api = hostApi(member("me-dev", "Alice", role = "HOST"), emptyList())
        val conn = FakeJamConnection()
        val repo = repo(api, FakeJamSocketClient(conn), deviceId = "me-dev")

        repo.controlPlay() // never started a session
        advanceUntilIdle()
        assertTrue(conn.sent.isEmpty())
    }

    // --- join / leave ---

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
                    snapshot = snapshotDto(host, listOf(host, me)),
                ),
            ),
        )
        val socket = FakeJamSocketClient(FakeJamConnection())
        val repo = repo(api, socket, deviceId = "me-dev")

        val result = repo.joinSession("https://viper.player/jam/abcdef")
        advanceUntilIdle()

        assertTrue(result.isSuccess)
        val session = repo.currentSession.value!!
        assertFalse(session.isHost)
        assertEquals("Host", session.hostName)
        assertEquals(2, session.participants.size)
        assertTrue(session.participants.single { it.id == "me-dev" }.isSelf)
        assertEquals("ABCDEF", api.lastJoin?.first())
        assertEquals("guest-jwt", socket.connectedJwt)
    }

    @Test
    fun joinSession_invalidCode_failsWithoutHittingApi() = runTest {
        val api = FakeSessionApi(SessionApiResult.Rejected("n/a"), SessionApiResult.Rejected("n/a"))
        val repo = repo(api, FakeJamSocketClient(FakeJamConnection()))

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
        val repo = repo(api, FakeJamSocketClient(FakeJamConnection()))

        val result = repo.joinSession("ABCDEF")
        advanceUntilIdle()
        assertTrue(result.isFailure)
        assertEquals("session not found", result.exceptionOrNull()?.message)
        assertNull(repo.currentSession.value)
    }

    @Test
    fun leaveSession_closesConnection_andClearsSessionAndPlayback() = runTest {
        val host = member("me-dev", "Alice", role = "HOST")
        val api = hostApi(host, listOf(host))
        val conn = FakeJamConnection(frames = listOf(snapshotFrame(host, listOf(host))))
        val repo = repo(api, FakeJamSocketClient(conn), deviceId = "me-dev")

        repo.startSession()
        advanceUntilIdle()
        conn.emitScripted()
        advanceUntilIdle()
        assertTrue(repo.currentSession.value != null)

        repo.leaveSession()
        advanceUntilIdle()

        assertNull(repo.currentSession.value)
        assertNull(repo.playback.value)
        assertTrue("leave must close the connection (server treats as leave)", conn.closed)
    }
}
