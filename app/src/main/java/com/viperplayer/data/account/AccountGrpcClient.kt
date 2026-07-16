package com.viperplayer.data.account

import account.v1.Account
import account.v1.AccountServiceGrpcKt
import account.v1.getMeRequest
import account.v1.loginRequest
import account.v1.logoutRequest
import account.v1.refreshRequest
import account.v1.registerRequest
import io.grpc.ManagedChannel
import io.grpc.Metadata
import io.grpc.okhttp.OkHttpChannelBuilder
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * gRPC/protobuf transport for the ViPER `account.v1.AccountService` (replaces the old Ktor-JSON
 * `AccountApi`). Owns a single lazily-built [ManagedChannel] (grpc-okhttp, HTTP/2) to the configured
 * `host:port` and the generated coroutine stub.
 *
 * Register/Login/Refresh/Logout are public (no auth); [getMe] attaches the caller's access token as
 * `authorization: Bearer <token>` gRPC metadata. Every method catches gRPC status exceptions and maps
 * them to an [AccountApiResult] (see [toAccountApiResult]) so the repository never sees a raw
 * [io.grpc.StatusException]. No token persistence here — that's the credential store's job.
 */
@Singleton
class AccountGrpcClient @Inject constructor(
    private val config: AccountBackendConfig?,
) {
    val isConfigured: Boolean get() = config != null

    // Built on first use and reused for the process lifetime; grpc reconnects internally.
    private val channel: ManagedChannel? by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val cfg = config ?: return@lazy null
        OkHttpChannelBuilder.forAddress(cfg.host, cfg.port)
            .apply { if (!cfg.useTls) usePlaintext() }
            .keepAliveTime(KEEP_ALIVE_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    private val stub: AccountServiceGrpcKt.AccountServiceCoroutineStub?
        get() = channel?.let { AccountServiceGrpcKt.AccountServiceCoroutineStub(it) }

    suspend fun register(email: String, password: String, displayName: String?): AccountApiResult<Account.RegisterResponse> =
        call {
            it.register(
                registerRequest {
                    this.email = email
                    this.password = password
                    this.displayName = displayName.orEmpty()
                }
            )
        }

    suspend fun login(email: String, password: String): AccountApiResult<Account.LoginResponse> =
        call {
            it.login(
                loginRequest {
                    this.email = email
                    this.password = password
                }
            )
        }

    suspend fun refresh(refreshToken: String): AccountApiResult<Account.RefreshResponse> =
        call {
            it.refresh(refreshRequest { this.refreshToken = refreshToken })
        }

    /**
     * Fetches the current user. Authenticated: attaches `authorization: Bearer <accessToken>` metadata.
     * A rejected/expired token surfaces as [AccountApiResult.Unauthenticated] so the caller can refresh
     * and retry (see [AccountRepositoryImpl]).
     */
    suspend fun getMe(accessToken: String): AccountApiResult<Account.GetMeResponse> =
        call { it.getMe(getMeRequest {}, bearerMetadata(accessToken)) }

    /** Best-effort logout; revokes the presented refresh token server-side. Failures are swallowed. */
    suspend fun logout(refreshToken: String) {
        val s = stub ?: return
        try {
            s.logout(logoutRequest { this.refreshToken = refreshToken })
        } catch (e: CancellationException) {
            throw e // preserve structured-concurrency cancellation
        } catch (e: Exception) {
            Timber.w("Account logout RPC failed: ${e.javaClass.simpleName}")
        }
    }

    /** Shuts the channel down (e.g. on app teardown). Safe to call when never configured/used. */
    fun shutdown() {
        channel?.shutdown()
    }

    private suspend fun <T> call(
        block: suspend (AccountServiceGrpcKt.AccountServiceCoroutineStub) -> T,
    ): AccountApiResult<T> {
        val s = stub ?: return AccountApiResult.NotConfigured
        return try {
            AccountApiResult.Success(block(s))
        } catch (e: CancellationException) {
            throw e // let coroutine cancellation propagate — it's not a network failure
        } catch (e: Exception) {
            Timber.w("Account RPC failed: ${e.javaClass.simpleName}")
            e.toAccountApiResult()
        }
    }

    private companion object {
        const val KEEP_ALIVE_SECONDS = 60L
    }
}

/** gRPC metadata key for the bearer token (`authorization: Bearer <token>`). */
internal val AUTHORIZATION_METADATA_KEY: Metadata.Key<String> =
    Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER)

/**
 * Builds per-call gRPC metadata carrying `authorization: Bearer <accessToken>` for authenticated RPCs
 * (GetMe + the library RPCs). Top-level + internal so the header attach is unit-testable.
 */
internal fun bearerMetadata(accessToken: String): Metadata = Metadata().apply {
    put(AUTHORIZATION_METADATA_KEY, "Bearer $accessToken")
}
