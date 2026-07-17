package com.viperplayer.data.social

import com.viperplayer.BuildConfig
import io.ktor.client.HttpClient
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
 * Transport-agnostic outcome of a session REST call ([SessionApi]).
 */
sealed interface SessionApiResult<out T> {
    data class Success<T>(val value: T) : SessionApiResult<T>

    /** The backend rejected the request — bad/expired code (404 join), validation, etc. */
    data class Rejected(val message: String) : SessionApiResult<Nothing>

    /** Transport failure (offline / timeout / server unreachable). */
    data object NetworkError : SessionApiResult<Nothing>

    /** No backend endpoint configured. */
    data object NotConfigured : SessionApiResult<Nothing>
}

/**
 * HTTP/JSON transport for the ViPER backend Jam session REST routes (`POST /sessions`,
 * `POST /sessions/join`; github.com/iscle/viper-backend, internal/httpapi/api.go). Mirrors the
 * backend's camelCase JSON DTOs ([SessionDtos]) over the shared Ktor [HttpClient].
 *
 * Both routes are public (no bearer needed) — the response carries a session-scoped `jwt` the caller
 * then presents to the WebSocket gateway ([JamSocketClient]). No token persistence here.
 */
@Singleton
open class SessionApi @Inject constructor(
    private val httpClient: HttpClient,
) {
    // encodeDefaults so the `mode` request field (which defaults to "JAM") is always sent — the backend
    // reads it. ignoreUnknownKeys so response fields we don't model (playback/queue/permissions/...)
    // don't fail decoding.
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Resolves the raw configured backend URL. Defaults to [BuildConfig.VIPER_BACKEND_URL]; overridable
     * only for tests (where `BuildConfig` is always the placeholder) so a MockEngine-backed client can
     * be pointed at a fake origin. Not part of the injected surface — Hilt uses the primary constructor.
     */
    private var rawBackendUrl: () -> String = { BuildConfig.VIPER_BACKEND_URL }

    internal constructor(httpClient: HttpClient, rawBackendUrl: () -> String) : this(httpClient) {
        this.rawBackendUrl = rawBackendUrl
    }

    /** The configured backend base URL (no trailing slash), or null when not built in. */
    val baseUrl: String?
        get() = rawBackendUrl()
            .trimEnd('/')
            .takeIf { it.isNotBlank() && it != PLACEHOLDER }

    val isConfigured: Boolean get() = baseUrl != null

    /** `POST /sessions` — host a new session. Response carries the share code, invite URL and host JWT. */
    open suspend fun createSession(
        deviceId: String,
        userId: String,
        name: String,
    ): SessionApiResult<CreateSessionResponseDto> =
        post("/sessions", json.encodeToString(CreateSessionRequestDto(deviceId, userId, name))) {
            json.decodeFromString<CreateSessionResponseDto>(it)
        }

    /**
     * `POST /sessions/join` — join by share code. A bad/expired code answers 404 → [SessionApiResult.Rejected];
     * the route is rate-limited (429) which also maps to [SessionApiResult.Rejected].
     */
    open suspend fun joinSession(
        code: String,
        deviceId: String,
        userId: String,
        name: String,
    ): SessionApiResult<JoinSessionResponseDto> =
        post("/sessions/join", json.encodeToString(JoinSessionRequestDto(code, deviceId, userId, name))) {
            json.decodeFromString<JoinSessionResponseDto>(it)
        }

    private suspend fun <T> post(
        path: String,
        body: String,
        map: (String) -> T,
    ): SessionApiResult<T> {
        val base = baseUrl ?: return SessionApiResult.NotConfigured
        val response = try {
            httpClient.post("$base$path") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Accept, ContentType.Application.Json.toString())
                setBody(body)
            }
        } catch (e: CancellationException) {
            throw e // let coroutine cancellation propagate — it's not a network failure
        } catch (e: Exception) {
            Timber.w("Session API request failed: ${e.javaClass.simpleName}")
            return SessionApiResult.NetworkError
        }
        return handle(response, map)
    }

    private suspend fun <T> handle(response: HttpResponse, map: (String) -> T): SessionApiResult<T> {
        val bodyText = try {
            response.bodyAsText()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return SessionApiResult.NetworkError
        }
        if (!response.status.isSuccess()) {
            val serverMessage = runCatching { json.decodeFromString<SessionErrorDto>(bodyText).error }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
            // A 5xx is a server-side transient — surface as a network error so the UI can suggest retry;
            // 4xx (bad code, rate-limited) is a definitive rejection.
            return if (response.status.value >= 500) {
                SessionApiResult.NetworkError
            } else {
                SessionApiResult.Rejected(serverMessage ?: "Couldn't reach the session")
            }
        }
        val mapped = runCatching { map(bodyText) }.getOrNull()
            ?: return SessionApiResult.Rejected("Malformed response")
        return SessionApiResult.Success(mapped)
    }

    private companion object {
        const val PLACEHOLDER = "REPLACE_WITH_REAL_VALUE"
    }
}
