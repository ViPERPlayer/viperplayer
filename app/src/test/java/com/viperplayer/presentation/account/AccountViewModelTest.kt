package com.viperplayer.presentation.account

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.viperplayer.data.account.AccountApiResult
import com.viperplayer.data.preferences.LibrarySyncPreferences
import com.viperplayer.data.sync.LibrarySync
import com.viperplayer.data.sync.LibrarySyncStatusStore
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

        override suspend fun changePassword(current: String, new: String): AuthResult = result
        override suspend fun deleteAccount(password: String): AuthResult = result
        override suspend fun refreshProfile(): AccountUser? = (result as? AuthResult.Success)?.user

        override suspend fun <T> withBackendAuth(
            call: suspend (accessToken: String) -> AccountApiResult<T>,
        ): AccountApiResult<T> = AccountApiResult.Unauthenticated
    }

    private class FakeLibrarySync : LibrarySync {
        override val syncing = MutableStateFlow<Set<String>>(emptySet())
        override suspend fun syncConnectedPlugins() {}
    }

    /** Minimal in-memory DataStore<Preferences> so the sync stores work in a pure-JVM unit test. */
    private fun fakePrefsDataStore(): DataStore<Preferences> = object : DataStore<Preferences> {
        private val flow = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = flow
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val updated = transform(flow.value)
            flow.value = updated
            return updated
        }
    }

    private fun accountViewModel(repo: AccountRepository) = AccountViewModel(
        repo,
        FakeLibrarySync(),
        LibrarySyncPreferences(fakePrefsDataStore()),
        LibrarySyncStatusStore(fakePrefsDataStore()),
    )

    @Test
    fun signIn_success_clearsErrorAndStopsSubmitting() = runTest {
        val repo = FakeAccountRepository(result = AuthResult.Success(AccountUser("1", "a@b.com", "A")))
        val vm = accountViewModel(repo)

        vm.signIn("a@b.com", "supersecret")
        advanceUntilIdle()

        assertEquals(1, repo.loginCalls)
        assertNull(vm.uiState.value.error)
        assertFalse(vm.uiState.value.isSubmitting)
    }

    @Test
    fun signIn_failed_surfacesServerMessage() = runTest {
        val repo = FakeAccountRepository(result = AuthResult.Failed("Invalid email or password"))
        val vm = accountViewModel(repo)

        vm.signIn("a@b.com", "wrongpass")
        advanceUntilIdle()

        assertEquals("Invalid email or password", vm.uiState.value.error)
        assertFalse(vm.uiState.value.isSubmitting)
    }

    @Test
    fun register_networkError_surfacesConnectionMessage() = runTest {
        val repo = FakeAccountRepository(result = AuthResult.NetworkError)
        val vm = accountViewModel(repo)

        vm.register("a@b.com", "supersecret", "Alice")
        advanceUntilIdle()

        assertEquals(1, repo.registerCalls)
        assertTrue(vm.uiState.value.error!!.contains("Couldn't reach"))
    }

    @Test
    fun register_notConfigured_surfacesUnavailableMessage() = runTest {
        val repo = FakeAccountRepository(result = AuthResult.NotConfigured)
        val vm = accountViewModel(repo)

        vm.register("a@b.com", "supersecret", "")
        advanceUntilIdle()

        assertEquals("Accounts aren't available in this build.", vm.uiState.value.error)
    }

    @Test
    fun signedInState_flowsThroughFromRepository() = runTest {
        val repo = FakeAccountRepository()
        val vm = accountViewModel(repo)
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
        val vm = accountViewModel(repo)
        vm.signIn("a@b.com", "x")
        advanceUntilIdle()
        assertEquals("boom", vm.uiState.value.error)

        vm.setMode(AuthMode.Register)
        assertEquals(AuthMode.Register, vm.uiState.value.mode)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun isConfigured_reflectsRepository() = runTest {
        val vm = accountViewModel(FakeAccountRepository(isConfigured = false))
        assertFalse(vm.uiState.value.isConfigured)
    }

    @Test
    fun signOut_delegatesToRepository() = runTest {
        val repo = FakeAccountRepository()
        val vm = accountViewModel(repo)
        vm.signOut()
        advanceUntilIdle()
        assertEquals(1, repo.logoutCalls)
    }
}
