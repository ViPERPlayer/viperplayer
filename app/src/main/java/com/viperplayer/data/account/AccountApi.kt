package com.viperplayer.data.account

import com.viperplayer.BuildConfig
import com.viperplayer.data.social.BackendConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * HTTP/JSON transport for the ViPER backend account API (the `/auth/` routes; github.com/iscle/
 * viper-backend). Mirrors the backend's camelCase JSON DTOs ([AccountDtos]) over the shared Ktor
 * [HttpClient].
 *
 * Register/Login/Refresh/Logout are public (no auth); [getMe] attaches the caller's access token as
 * `Authorization: Bearer <token>`. Every method returns an [AccountApiResult] (see
 * [mapAccountHttpError]) so the repository can distinguish a backend rejection (message + mapped
 * status) from a transport failure, and a 401 from anything else — the seam the authed
 * refresh-and-retry path relies on. No token persistence here — that's the credential store's job.
 */
@Singleton
class AccountApi @Inject constructor(
    private val httpClient: HttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** The configured backend base URL (no trailing slash), or null when not built in. */
    val baseUrl: String?
        get() = BackendConfig.baseUrlOf(BuildConfig.VIPER_BACKEND_URL)

    val isConfigured: Boolean get() = baseUrl != null

    suspend fun register(email: String, password: String, displayName: String?): AccountApiResult<AuthDto> =
        post("/auth/register", json.encodeToString(RegisterRequestDto(email, password, displayName.orEmpty()))) {
            json.decodeFromString<AuthDto>(it)
        }

    suspend fun login(email: String, password: String): AccountApiResult<AuthDto> =
        post("/auth/login", json.encodeToString(LoginRequestDto(email, password))) {
            json.decodeFromString<AuthDto>(it)
        }

    suspend fun refresh(refreshToken: String): AccountApiResult<TokenPairDto> =
        post("/auth/refresh", json.encodeToString(RefreshRequestDto(refreshToken))) {
            json.decodeFromString<RefreshResponseDto>(it).tokens
        }

    /**
     * Fetches the current user (`GET /auth/me`) with the access token as `Authorization: Bearer`.
     * A rejected/expired token surfaces as [AccountApiResult.Unauthenticated] so the caller can
     * refresh and retry (see [AccountRepositoryImpl.withAuth]).
     */
    suspend fun getMe(accessToken: String): AccountApiResult<UserDto> {
        val base = baseUrl ?: return AccountApiResult.NotConfigured
        val response = try {
            httpClient.get("$base/auth/me") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            }
        } catch (e: CancellationException) {
            throw e // let coroutine cancellation propagate — it's not a network failure
        } catch (e: Exception) {
            Timber.w("Account getMe request failed: ${e.javaClass.simpleName}")
            return AccountApiResult.NetworkError
        }
        return handle(response) { json.decodeFromString<UserDto>(it) }
    }

    /** Best-effort logout; revokes the presented refresh token server-side. Failures are swallowed. */
    suspend fun logout(refreshToken: String) {
        val base = baseUrl ?: return
        try {
            httpClient.post("$base/auth/logout") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(RefreshRequestDto(refreshToken)))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w("Account logout request failed: ${e.javaClass.simpleName}")
        }
    }

    private suspend fun <T> post(
        path: String,
        body: String,
        map: (String) -> T,
    ): AccountApiResult<T> {
        val base = baseUrl ?: return AccountApiResult.NotConfigured
        val response = try {
            httpClient.post("$base$path") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Accept, ContentType.Application.Json.toString())
                setBody(body)
            }
        } catch (e: CancellationException) {
            throw e // let coroutine cancellation propagate — it's not a network failure
        } catch (e: Exception) {
            Timber.w("Account API request failed: ${e.javaClass.simpleName}")
            return AccountApiResult.NetworkError
        }
        return handle(response, map)
    }

    private suspend fun <T> handle(response: HttpResponse, map: (String) -> T): AccountApiResult<T> {
        val bodyText = try {
            response.bodyAsText()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return AccountApiResult.NetworkError
        }
        if (!response.status.isSuccess()) {
            val serverMessage = runCatching { json.decodeFromString<ErrorDto>(bodyText).error }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
            return mapAccountHttpError(response.status.value, serverMessage)
        }
        val mapped = runCatching { map(bodyText) }.getOrNull()
            ?: return AccountApiResult.Rejected("Malformed response")
        return AccountApiResult.Success(mapped)
    }
}
