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

    /**
     * Changes the signed-in user's password (`POST /auth/change-password`, `Authorization: Bearer`).
     * The backend replies 204 on success (no body). A rejected access token surfaces as
     * [AccountApiResult.Unauthenticated] so the caller can refresh + retry; a wrong current password
     * is also 401 — the repository disambiguates by retry outcome. A weak new password is 400
     * ([AccountApiResult.Rejected]).
     */
    suspend fun changePassword(
        accessToken: String,
        currentPassword: String,
        newPassword: String,
    ): AccountApiResult<Unit> = bearerPost(
        path = "/auth/change-password",
        accessToken = accessToken,
        body = json.encodeToString(ChangePasswordRequestDto(currentPassword, newPassword)),
    )

    /**
     * Permanently deletes the signed-in account (`POST /auth/delete`, `Authorization: Bearer`),
     * confirming with the current [password]. The backend replies 204 on success (no body); a wrong
     * password is 401.
     */
    suspend fun deleteAccount(accessToken: String, password: String): AccountApiResult<Unit> =
        bearerPost(
            path = "/auth/delete",
            accessToken = accessToken,
            body = json.encodeToString(DeleteAccountRequestDto(password)),
        )

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

    /**
     * A `Authorization: Bearer` POST whose 2xx carries no meaningful body (the backend answers 204).
     * Returns [AccountApiResult.Success] for any empty-2xx, so callers get `Unit` without decoding.
     */
    private suspend fun bearerPost(
        path: String,
        accessToken: String,
        body: String,
    ): AccountApiResult<Unit> {
        val base = baseUrl ?: return AccountApiResult.NotConfigured
        val response = try {
            httpClient.post("$base$path") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
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
        return handleNoContent(response)
    }

    /**
     * Maps a no-body response: any 2xx (including 204) is [AccountApiResult.Success] with [Unit];
     * a non-2xx maps through [mapAccountHttpError] carrying the server's `{"error": …}` when present.
     */
    private suspend fun handleNoContent(response: HttpResponse): AccountApiResult<Unit> {
        if (response.status.isSuccess()) return AccountApiResult.Success(Unit)
        val bodyText = try {
            response.bodyAsText()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return AccountApiResult.NetworkError
        }
        val serverMessage = runCatching { json.decodeFromString<ErrorDto>(bodyText).error }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
        return mapAccountHttpError(response.status.value, serverMessage)
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
