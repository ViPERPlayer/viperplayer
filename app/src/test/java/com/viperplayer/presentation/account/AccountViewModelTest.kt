package com.viperplayer.presentation.account

import com.viperplayer.data.account.AccountApiResult
import com.viperplayer.domain.account.AccountRepository
import com.viperplayer.domain.account.AccountState
import com.viperplayer.domain.account.AccountUser
import com.viperplayer.domain.account.AuthResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [AccountViewModel] over a fake [AccountRepository]. Covers the register/login error
 * mapping (each [AuthResult] → the right user-facing message or null), the signed-in state passthrough
 * from the repository flow, mode switching, and the in-flight submit guard.
 */
class AccountViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** A fake repository whose auth calls return a scripted result and whose state flow is settable. */
    private class FakeAccountRepository(
        override val isConfigured: Boolean = true,
        var result: AuthResult = AuthResult.Success(AccountUser("id", "a@b.com", "Alice")),
    ) : AccountRepository {
        val stateFlow = MutableStateFlow(AccountState())
        override val state: Flow<AccountState> = stateFlow
        var loginCalls = 0
        var registerCalls = 0
        var logoutCalls = 0

        override suspend fun register(email: String, password: String, displayName: String?): AuthResult {
            registerCalls++
            return result
        }

        override suspend fun login(email: String, password: String): AuthResult {
            loginCalls++
            return result
        }

        override suspend fun logout() {
            logoutCalls++
            stateFlow.value = AccountState()
        }

        override suspend fun validAccessToken(): String? = null

        override suspend fun <T> withBackendAuth(
            call: suspend (accessToken: String) -> AccountApiResult<T>,
        ): AccountApiResult<T> = AccountApiResult.Unauthenticated
    }

    @Test
    fun signIn_success_clearsErrorAndStopsSubmitting() = runTest {
        val repo = FakeAccountRepository(result = AuthResult.Success(AccountUser("1", "a@b.com", "A")))
        val vm = AccountViewModel(repo)

        vm.signIn("a@b.com", "supersecret")
        advanceUntilIdle()

        assertEquals(1, repo.loginCalls)
        assertNull(vm.uiState.value.error)
        assertFalse(vm.uiState.value.isSubmitting)
    }

    @Test
    fun signIn_failed_surfacesServerMessage() = runTest {
        val repo = FakeAccountRepository(result = AuthResult.Failed("Invalid email or password"))
        val vm = AccountViewModel(repo)

        vm.signIn("a@b.com", "wrongpass")
        advanceUntilIdle()

        assertEquals("Invalid email or password", vm.uiState.value.error)
        assertFalse(vm.uiState.value.isSubmitting)
    }

    @Test
    fun register_networkError_surfacesConnectionMessage() = runTest {
        val repo = FakeAccountRepository(result = AuthResult.NetworkError)
        val vm = AccountViewModel(repo)

        vm.register("a@b.com", "supersecret", "Alice")
        advanceUntilIdle()

        assertEquals(1, repo.registerCalls)
        assertTrue(vm.uiState.value.error!!.contains("Couldn't reach"))
    }

    @Test
    fun register_notConfigured_surfacesUnavailableMessage() = runTest {
        val repo = FakeAccountRepository(result = AuthResult.NotConfigured)
        val vm = AccountViewModel(repo)

        vm.register("a@b.com", "supersecret", "")
        advanceUntilIdle()

        assertEquals("Accounts aren't available in this build.", vm.uiState.value.error)
    }

    @Test
    fun signedInState_flowsThroughFromRepository() = runTest {
        val repo = FakeAccountRepository()
        val vm = AccountViewModel(repo)
        advanceUntilIdle()

        repo.stateFlow.value = AccountState(
            user = AccountUser("1", "a@b.com", "Alice"),
            isSignedIn = true,
        )
        advanceUntilIdle()

        assertTrue(vm.uiState.value.account.isSignedIn)
        assertEquals("Alice", vm.uiState.value.account.user?.displayName)
    }

    @Test
    fun setMode_togglesAndClearsError() = runTest {
        val repo = FakeAccountRepository(result = AuthResult.Failed("boom"))
        val vm = AccountViewModel(repo)
        vm.signIn("a@b.com", "x")
        advanceUntilIdle()
        assertEquals("boom", vm.uiState.value.error)

        vm.setMode(AuthMode.Register)
        assertEquals(AuthMode.Register, vm.uiState.value.mode)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun isConfigured_reflectsRepository() = runTest {
        val vm = AccountViewModel(FakeAccountRepository(isConfigured = false))
        assertFalse(vm.uiState.value.isConfigured)
    }

    @Test
    fun signOut_delegatesToRepository() = runTest {
        val repo = FakeAccountRepository()
        val vm = AccountViewModel(repo)
        vm.signOut()
        advanceUntilIdle()
        assertEquals(1, repo.logoutCalls)
    }
}
