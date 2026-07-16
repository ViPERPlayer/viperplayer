package com.viperplayer.data.account

import com.viperplayer.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin HTTP client for the ViPER backend account API (the `/auth/` routes).
 * Mirrors the account/v1 + common/v1 protobuf schema as JSON DTOs (Phase 0
 * transport is REST+JSON on both platforms).
 *
 * Every method returns an [AccountApiResult] so the repository can distinguish
 * a backend rejection (with a message + stable-ish status) from a transport
 * failure. No token persistence here — that's the credential store's job.
 */
@Singleton
class AccountApi @Inject constructor(
    private val httpClient: HttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** The configured backend base URL (no trailing slash), or null when not built in. */
    val baseUrl: String?
        get() = BuildConfig.VIPER_BACKEND_URL
            .trimEnd('/')
            .takeIf { it.isNotBlank() && it != PLACEHOLDER }

    val isConfigured: Boolean get() = baseUrl != null

    suspend fun register(email: String, password: String, displayName: String?): AccountApiResult<AuthDto> =
        request("/auth/register", RegisterRequestDto(email, password, displayName.orEmpty())) { body ->
            json.decodeFromString<AuthDto>(body)
        }

    suspend fun login(email: String, password: String): AccountApiResult<AuthDto> =
        request("/auth/login", LoginRequestDto(email, password)) { body ->
            json.decodeFromString<AuthDto>(body)
        }

    suspend fun refresh(refreshToken: String): AccountApiResult<TokenPairDto> =
        request("/auth/refresh", RefreshRequestDto(refreshToken)) { body ->
            json.decodeFromString<RefreshResponseDto>(body).tokens
        }

    /** Best-effort logout; revokes the refresh token server-side. Failures are ignored. */
    suspend fun logout(refreshToken: String) {
        val base = baseUrl ?: return
        runCatching {
            httpClient.post("$base/auth/logout") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(RefreshRequestDto(refreshToken)))
            }
        }
    }

    private suspend fun <T> request(
        path: String,
        requestBody: Any,
        map: (String) -> T,
    ): AccountApiResult<T> {
        val base = baseUrl ?: return AccountApiResult.NotConfigured
        val response: HttpResponse = try {
            httpClient.post("$base$path") {
                contentType(ContentType.Application.Json)
                header("Accept", ContentType.Application.Json.toString())
                setBody(encodeBody(requestBody))
            }
        } catch (e: Exception) {
            Timber.w("Account API request failed: ${e.javaClass.simpleName}")
            return AccountApiResult.NetworkError
        }

        val bodyText = try {
            response.bodyAsText()
        } catch (e: Exception) {
            return AccountApiResult.NetworkError
        }

        if (!response.status.isSuccess()) {
            val message = runCatching { json.decodeFromString<ErrorDto>(bodyText).error }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: defaultMessage(response.status)
            return AccountApiResult.Rejected(response.status.value, message)
        }

        val mapped = runCatching { map(bodyText) }.getOrNull()
            ?: return AccountApiResult.Rejected(response.status.value, "Malformed response")
        return AccountApiResult.Success(mapped)
    }

    private fun encodeBody(body: Any): String = when (body) {
        is RegisterRequestDto -> json.encodeToString(body)
        is LoginRequestDto -> json.encodeToString(body)
        is RefreshRequestDto -> json.encodeToString(body)
        else -> error("unsupported request body")
    }

    private fun defaultMessage(status: HttpStatusCode): String = when (status.value) {
        401 -> "Invalid email or password"
        409 -> "That email is already registered"
        400 -> "Check your details and try again"
        else -> "Request failed (${status.value})"
    }

    private companion object {
        const val PLACEHOLDER = "REPLACE_WITH_REAL_VALUE"
    }
}
