package com.viperplayer.presentation.player

import com.viperplayer.domain.model.ListenSession
import com.viperplayer.domain.model.SessionParticipant
import com.viperplayer.domain.repository.ListenTogetherRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [ListenTogetherViewModel] (the host side of Listen-together) over a fake
 * [ListenTogetherRepository]. Covers: starting hosts a real session and exposes its code; a second
 * start while already hosting is a no-op (same session reused); leaving tears the session down; and a
 * start failure surfaces an error instead of a fabricated code.
 */
class ListenTogetherViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** A fake repository whose start/leave mutate an in-memory session flow, scriptable to fail. */
    private class FakeListenTogetherRepository(
        var failStart: String? = null,
    ) : ListenTogetherRepository {
        private val _currentSession = MutableStateFlow<ListenSession?>(null)
        override val currentSession: StateFlow<ListenSession?> = _currentSession.asStateFlow()
        override val codeLength: Int = 6
        var startCalls = 0
        var leaveCalls = 0

        /** When set, startSession suspends on this gate before creating — to script a slow backend. */
        var startGate: CompletableDeferred<Unit>? = null

        override suspend fun startSession(): Result<ListenSession> {
            startCalls++
            startGate?.await()
            failStart?.let { return Result.failure(IllegalStateException(it)) }
            val session = ListenSession(
                code = "ABCD-EFG",
                inviteUrl = inviteUrlFor("ABCD-EFG"),
                hostName = "You",
                isHost = true,
                participants = listOf(SessionParticipant(id = "self", name = "You", isSelf = true)),
            )
            _currentSession.value = session
            return Result.success(session)
        }

        override suspend fun joinSession(codeOrUrl: String): Result<ListenSession> =
            Result.failure(UnsupportedOperationException("not used"))

        override suspend fun leaveSession() {
            leaveCalls++
            _currentSession.value = null
        }

        override fun inviteUrlFor(code: String): String = "https://viper.player/jam/${code.lowercase()}"

        override fun parseCode(input: String): String? = input
    }

    @Test
    fun startHosting_createsSession_andExposesRealCode() = runTest {
        val repo = FakeListenTogetherRepository()
        val vm = ListenTogetherViewModel(repo)

        vm.startHosting()
        advanceUntilIdle()

        assertEquals(1, repo.startCalls)
        val session = vm.session.value
        assertNotNull(session)
        assertEquals("ABCD-EFG", session!!.code)
        assertEquals("https://viper.player/jam/abcd-efg", session.inviteUrl)
        assertFalse(vm.uiState.value.starting)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun startHosting_isNoOp_whenAlreadyHosting() = runTest {
        val repo = FakeListenTogetherRepository()
        val vm = ListenTogetherViewModel(repo)

        vm.startHosting()
        advanceUntilIdle()
        vm.startHosting() // re-opening the sheet must reuse the existing session
        advanceUntilIdle()

        assertEquals(1, repo.startCalls)
    }

    @Test
    fun leaveHosting_endsSession() = runTest {
        val repo = FakeListenTogetherRepository()
        val vm = ListenTogetherViewModel(repo)

        vm.startHosting()
        advanceUntilIdle()
        assertNotNull(vm.session.value)

        vm.leaveHosting()
        advanceUntilIdle()

        assertEquals(1, repo.leaveCalls)
        assertNull(vm.session.value)
    }

    @Test
    fun leaveWhileStartInFlight_tearsDownTheLateSession_noLeak() = runTest {
        // Real backend: startSession is a slow REST+WS call. If the user dismisses the sheet before it
        // returns, the session created afterwards must NOT leak — leaveHosting's intent must win.
        val repo = FakeListenTogetherRepository()
        val gate = CompletableDeferred<Unit>()
        repo.startGate = gate
        val vm = ListenTogetherViewModel(repo)

        vm.startHosting()
        advanceUntilIdle()            // start runs, then suspends on the gate — no session yet
        assertNull(vm.session.value)

        vm.leaveHosting()             // user leaves before the create returns
        advanceUntilIdle()

        gate.complete(Unit)           // the in-flight startSession now completes and creates the session
        advanceUntilIdle()

        assertEquals(1, repo.startCalls)
        assertNull("session created after leave must be torn down, not leaked", vm.session.value)
        assertTrue("the late session must be explicitly left", repo.leaveCalls >= 1)
    }

    @Test
    fun startHosting_failure_surfacesError_andNoSession() = runTest {
        val repo = FakeListenTogetherRepository(failStart = "Backend unreachable")
        val vm = ListenTogetherViewModel(repo)

        vm.startHosting()
        advanceUntilIdle()

        assertNull(vm.session.value)
        assertFalse(vm.uiState.value.starting)
        assertEquals("Backend unreachable", vm.uiState.value.error)
    }
}
