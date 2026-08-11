package com.viperplayer.data.social

import com.viperplayer.BuildConfig
import com.viperplayer.domain.account.AccountApiResult
import com.viperplayer.data.account.ErrorDto
import com.viperplayer.data.account.accountErrorFallbacks
import com.viperplayer.data.account.mapAccountHttpError
import com.viperplayer.data.resources.StringProvider
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import io.ktor.http.encodeURLQueryComponent
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * HTTP/JSON transport for the ViPER backend social/follow API (the `/users/` + `/social/` routes;
 * github.com/iscle/viper-backend). Mirrors [com.viperplayer.data.account.AccountApi]: every route is
 * `Authorization: Bearer`, results are the shared [AccountApiResult] (so a 401 surfaces as
 * [AccountApiResult.Unauthenticated] for the refresh-and-retry seam), and the base URL resolves via
 * [BackendConfig] so an unconfigured build short-circuits to [AccountApiResult.NotConfigured].
 *
 * No token persistence here — callers run these through
 * [com.viperplayer.domain.account.AccountRepository.withBackendAuth] which attaches a valid token.
 */
@Singleton
open class FollowApi @Inject constructor(
    private val httpClient: HttpClient,
    private val stringProvider: StringProvider,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * The raw backend URL source. Defaults to [BuildConfig.VIPER_BACKEND_URL]; overridable only for
     * tests (where `BuildConfig` is the placeholder) so a MockEngine client can be pointed at a fake
     * origin. Not part of the injected surface — Hilt uses the primary constructor.
     */
    private var rawBackendUrl: () -> String = { BuildConfig.VIPER_BACKEND_URL }

    internal constructor(
        httpClient: HttpClient,
        stringProvider: StringProvider,
        rawBackendUrl: () -> String,
    ) : this(httpClient, stringProvider) {
        this.rawBackendUrl = rawBackendUrl
    }

    /** The configured backend base URL (no trailing slash), or null when not built in. */
    val baseUrl: String?
        get() = BackendConfig.baseUrlOf(rawBackendUrl())

    val isConfigured: Boolean get() = baseUrl != null

    /** `GET /users/search?q=` — find public users by name/handle prefix. */
    open suspend fun searchUsers(accessToken: String, query: String): AccountApiResult<List<PublicUserDto>> =
        bearerGet("/users/search?q=${query.encodeURLQueryComponent()}", accessToken) {
            json.decodeFromString<UserSearchResponseDto>(it).results
        }

    /** `GET /users/by-handle/{handle}` — resolve a single user by exact `@handle` (404 → Rejected). */
    open suspend fun userByHandle(accessToken: String, handle: String): AccountApiResult<PublicUserDto> =
        bearerGet("/users/by-handle/${handle.removePrefix("@").encodeURLPathPart()}", accessToken) {
            json.decodeFromString<PublicUserDto>(it)
        }

    /** `POST /social/follow` — follow [userId] (204 on success). */
    open suspend fun follow(accessToken: String, userId: String): AccountApiResult<Unit> =
        noContent {
            httpClient.post("$it/social/follow") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Accept, ContentType.Application.Json.toString())
                setBody(json.encodeToString(FollowRequestDto(userId)))
            }
        }

    /** `DELETE /social/follow/{userId}` — unfollow [userId] (204 on success). */
    open suspend fun unfollow(accessToken: String, userId: String): AccountApiResult<Unit> =
        noContent {
            httpClient.delete("$it/social/follow/${userId.encodeURLPathPart()}") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            }
        }

    /** `GET /social/following` — the users the caller follows. */
    open suspend fun following(accessToken: String): AccountApiResult<List<PublicUserDto>> =
        bearerGet("/social/following", accessToken) {
            json.decodeFromString<FollowListResponseDto>(it).users
        }

    /** `GET /social/followers` — the users following the caller. */
    open suspend fun followers(accessToken: String): AccountApiResult<List<PublicUserDto>> =
        bearerGet("/social/followers", accessToken) {
            json.decodeFromString<FollowListResponseDto>(it).users
        }

    /** `GET /social/relationship/{userId}` — the follow edges + aggregate counts vs [userId]. */
    open suspend fun relationship(accessToken: String, userId: String): AccountApiResult<RelationshipDto> =
        bearerGet("/social/relationship/${userId.encodeURLPathPart()}", accessToken) {
            json.decodeFromString<RelationshipDto>(it)
        }

    /** A `Authorization: Bearer` GET, decoding the 2xx JSON body via [map]. */
    private suspend fun <T> bearerGet(
        path: String,
        accessToken: String,
        map: (String) -> T,
    ): AccountApiResult<T> {
        val base = baseUrl ?: return AccountApiResult.NotConfigured
        val response = try {
            httpClient.get("$base$path") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            }
        } catch (e: CancellationException) {
            throw e // let coroutine cancellation propagate — it's not a network failure
        } catch (e: Exception) {
            Timber.w("Follow API request failed: ${e.javaClass.simpleName}")
            return AccountApiResult.NetworkError
        }
        return handle(response, map)
    }

    /**
     * A `Authorization: Bearer` request whose 2xx carries no meaningful body (the backend answers 204).
     * [request] issues the actual POST/DELETE (receiving the resolved base URL); any empty-2xx is
     * [Unit] success, a non-2xx maps through [mapAccountHttpError] carrying the server's message.
     */
    private suspend fun noContent(
        request: suspend (baseUrl: String) -> HttpResponse,
    ): AccountApiResult<Unit> {
        val base = baseUrl ?: return AccountApiResult.NotConfigured
        val response = try {
            request(base)
        } catch (e: CancellationException) {
            throw e // let coroutine cancellation propagate — it's not a network failure
        } catch (e: Exception) {
            Timber.w("Follow API request failed: ${e.javaClass.simpleName}")
            return AccountApiResult.NetworkError
        }
        if (response.status.isSuccess()) return AccountApiResult.Success(Unit)
        return mapAccountHttpError(response.status.value, serverMessageOf(response), stringProvider.accountErrorFallbacks())
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
            return mapAccountHttpError(response.status.value, serverMessage, stringProvider.accountErrorFallbacks())
        }
        val mapped = runCatching { map(bodyText) }.getOrNull()
            ?: return AccountApiResult.Rejected("Malformed response")
        return AccountApiResult.Success(mapped)
    }

    private suspend fun serverMessageOf(response: HttpResponse): String? {
        val bodyText = try {
            response.bodyAsText()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        }
        return runCatching { json.decodeFromString<ErrorDto>(bodyText).error }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }
}
