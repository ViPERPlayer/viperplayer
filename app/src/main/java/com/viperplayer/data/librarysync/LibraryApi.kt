package com.viperplayer.data.librarysync

import com.viperplayer.BuildConfig
import com.viperplayer.domain.account.AccountApiResult
import com.viperplayer.data.account.ErrorDto
import com.viperplayer.data.account.accountErrorFallbacks
import com.viperplayer.data.account.mapAccountHttpError
import com.viperplayer.data.resources.StringProvider
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
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
 * HTTP/JSON transport for the ViPER backend library-sync API (the `/library` routes; github.com/
 * iscle/viper-backend). Mirrors the backend's camelCase JSON DTOs ([LibraryDtos]) over the shared
 * Ktor [HttpClient], and reuses the account transport's [AccountApiResult] + [mapAccountHttpError] so
 * a 401 surfaces as [AccountApiResult.Unauthenticated] — the seam the authed refresh-and-retry path
 * (`AccountRepository.withBackendAuth`) relies on.
 *
 * Every route is bearer-authenticated: each method takes the caller's access token and attaches it as
 * `Authorization: Bearer <token>` (same as `AccountApi.getMe`). No token persistence here — that is
 * the credential store's job; callers run these through the account refresh-and-retry policy.
 */
@Singleton
class LibraryApi @Inject constructor(
    private val httpClient: HttpClient,
    private val stringProvider: StringProvider,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Resolves the raw configured backend URL. Defaults to [BuildConfig.VIPER_BACKEND_URL]; overridable
     * only for tests (where `BuildConfig` is always the placeholder) so a MockEngine-backed client can
     * be pointed at a fake origin. Not part of the injected surface — Hilt uses the primary constructor.
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
        get() = rawBackendUrl()
            .trimEnd('/')
            .takeIf { it.isNotBlank() && it != PLACEHOLDER }

    val isConfigured: Boolean get() = baseUrl != null

    /** `GET /library` — the caller's whole synced state (playlists + likes + high-water revision). */
    suspend fun getLibrary(accessToken: String): AccountApiResult<LibrarySnapshotDto> {
        val base = baseUrl ?: return AccountApiResult.NotConfigured
        val response = try {
            httpClient.get("$base/library") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            }
        } catch (e: CancellationException) {
            throw e // let coroutine cancellation propagate — it's not a network failure
        } catch (e: Exception) {
            Timber.w("Library getLibrary request failed: ${e.javaClass.simpleName}")
            return AccountApiResult.NetworkError
        }
        return handle(response) { json.decodeFromString<LibrarySnapshotDto>(it) }
    }

    /**
     * `PUT /library/playlists` — creates or replaces a playlist. [baseRevision] is the revision the
     * caller last saw for this playlist; a stale base comes back as
     * [UpsertPlaylistResponseDto.conflict] = true rather than clobbering a concurrent edit.
     */
    suspend fun upsertPlaylist(
        accessToken: String,
        playlist: PlaylistDto,
        baseRevision: Long,
    ): AccountApiResult<UpsertPlaylistResponseDto> =
        authed(
            accessToken = accessToken,
            method = HttpMethod.PUT,
            path = "/library/playlists",
            body = json.encodeToString(UpsertPlaylistRequestDto(playlist, baseRevision)),
        ) { json.decodeFromString<UpsertPlaylistResponseDto>(it) }

    /** `POST /library/playlists/delete` — deletes a playlist by id; returns the new library revision. */
    suspend fun deletePlaylist(
        accessToken: String,
        playlistId: String,
    ): AccountApiResult<RevisionResponseDto> =
        authed(
            accessToken = accessToken,
            method = HttpMethod.POST,
            path = "/library/playlists/delete",
            body = json.encodeToString(DeletePlaylistRequestDto(playlistId)),
        ) { json.decodeFromString<RevisionResponseDto>(it) }

    /** `POST /library/likes` — sets/unsets a like on [track]; returns the new library revision. */
    suspend fun setLike(
        accessToken: String,
        track: TrackRefDto,
        liked: Boolean,
    ): AccountApiResult<RevisionResponseDto> =
        authed(
            accessToken = accessToken,
            method = HttpMethod.POST,
            path = "/library/likes",
            body = json.encodeToString(SetLikeRequestDto(track, liked)),
        ) { json.decodeFromString<RevisionResponseDto>(it) }

    private enum class HttpMethod { PUT, POST }

    /** Shared authed body request: attaches the bearer token + JSON body, then maps the response. */
    private suspend fun <T> authed(
        accessToken: String,
        method: HttpMethod,
        path: String,
        body: String,
        map: (String) -> T,
    ): AccountApiResult<T> {
        val base = baseUrl ?: return AccountApiResult.NotConfigured
        val response = try {
            val url = "$base$path"
            val block: HttpRequestBuilder.() -> Unit = {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                header(HttpHeaders.Accept, ContentType.Application.Json.toString())
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            when (method) {
                HttpMethod.PUT -> httpClient.put(url, block)
                HttpMethod.POST -> httpClient.post(url, block)
            }
        } catch (e: CancellationException) {
            throw e // let coroutine cancellation propagate — it's not a network failure
        } catch (e: Exception) {
            Timber.w("Library API request failed: ${e.javaClass.simpleName}")
            return AccountApiResult.NetworkError
        }
        return handle(response, map)
    }

    /**
     * Reads the response body, mapping a non-2xx status (plus the server's `{"error": ...}` message)
     * through [mapAccountHttpError], and a malformed 2xx body to [AccountApiResult.Rejected].
     */
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

    private companion object {
        const val PLACEHOLDER = "REPLACE_WITH_REAL_VALUE"
    }
}
