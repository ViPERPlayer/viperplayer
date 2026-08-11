package com.viperplayer.data.rec

import com.viperplayer.BuildConfig
import com.viperplayer.data.social.BackendConfig
import com.viperplayer.domain.account.AccountApiResult
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
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
 * HTTP/JSON transport for the ViPER backend recommender REST API (P3; the `/recommendations` +
 * `/discovery/contribute` routes; github.com/iscle/viper-backend), over the shared Ktor [HttpClient].
 *
 * Mirrors [com.viperplayer.data.librarysync.LibraryApi]'s idiom: each call takes a caller-supplied
 * access token and attaches it as `Authorization: Bearer <token>` (no token persistence here — callers
 * run these through [com.viperplayer.domain.account.AccountRepository.withBackendAuth]), and the base
 * URL resolves via [BackendConfig] so an unconfigured build short-circuits.
 *
 * Unlike the account transports, results are surfaced as this API's own [DiscoverResult] /
 * [ContributeResult] rather than `AccountApiResult`, because the recommender contract has a
 * feature-specific 409 (model-version handshake mismatch: the device's taste is stale relative to the
 * server model → treat as "unavailable, recompute") that the generic account error mapping doesn't model.
 */
@Singleton
open class RecommendationApi @Inject constructor(
    private val httpClient: HttpClient,
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
        rawBackendUrl: () -> String,
    ) : this(httpClient) {
        this.rawBackendUrl = rawBackendUrl
    }

    /** The configured backend base URL (no trailing slash), or null when not built in. */
    val baseUrl: String?
        get() = BackendConfig.baseUrlOf(rawBackendUrl())

    val isConfigured: Boolean get() = baseUrl != null

    /**
     * `POST /recommendations` — fetches a page of discovery candidates for the device's [tasteVector],
     * excluding the ids the caller already owns ([excludeIds], each `"pluginId:sourceId"`).
     *
     * Distinguishes the recommender-specific outcomes:
     *  - 200 → [DiscoverResult.Success] (possibly with an empty candidate list — "nothing to discover",
     *    NOT an error);
     *  - 409 → [DiscoverResult.HandshakeMismatch] (the device taste is stale vs the server model);
     *  - anything else / transport failure → [DiscoverResult.Error].
     */
    open suspend fun discover(
        accessToken: String,
        modelVersion: String,
        tasteVector: FloatArray,
        excludeIds: List<String>,
        limit: Int,
    ): DiscoverResult {
        val base = baseUrl ?: return DiscoverResult.NotConfigured
        val request = DiscoverRequestDto(
            modelVersion = modelVersion,
            tasteVector = tasteVector.toList(),
            excludeIds = excludeIds,
            limit = limit,
        )
        val response = try {
            httpClient.post("$base/recommendations") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                header(HttpHeaders.Accept, ContentType.Application.Json.toString())
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(request))
            }
        } catch (e: CancellationException) {
            throw e // let coroutine cancellation propagate — it's not a network failure
        } catch (e: Exception) {
            Timber.w("Recommendation discover request failed: ${e.javaClass.simpleName}")
            return DiscoverResult.Error
        }
        if (response.status == HttpStatusCode.Conflict) return DiscoverResult.HandshakeMismatch
        if (!response.status.isSuccess()) {
            Timber.w("Recommendation discover: HTTP ${response.status.value}")
            return DiscoverResult.Error
        }
        val bodyText = try {
            response.bodyAsText()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return DiscoverResult.Error
        }
        val dto = runCatching { json.decodeFromString<DiscoverResponseDto>(bodyText) }.getOrElse {
            Timber.w("Recommendation discover parse failed: ${it.javaClass.simpleName}")
            return DiscoverResult.Error
        }
        return DiscoverResult.Success(dto)
    }

    /**
     * `POST /discovery/contribute` — anonymized, opt-in contribution of L2-normalized taste [vectors]
     * (no ids, no audio, no identity). Caps at [MAX_VECTORS] per call. Returns [ContributeResult.Success]
     * (with the server-reported accepted count) on 200, else [ContributeResult.Error] — never throws.
     */
    open suspend fun contribute(
        accessToken: String,
        modelVersion: String,
        vectors: List<FloatArray>,
    ): ContributeResult {
        val base = baseUrl ?: return ContributeResult.NotConfigured
        if (vectors.isEmpty()) return ContributeResult.Success(0)
        val capped = vectors.take(MAX_VECTORS).map { it.toList() }
        val request = ContributeRequestDto(modelVersion = modelVersion, vectors = capped)
        val response = try {
            httpClient.post("$base/discovery/contribute") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                header(HttpHeaders.Accept, ContentType.Application.Json.toString())
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(request))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w("Recommendation contribute request failed: ${e.javaClass.simpleName}")
            return ContributeResult.Error
        }
        if (!response.status.isSuccess()) {
            Timber.w("Recommendation contribute: HTTP ${response.status.value}")
            return ContributeResult.Error
        }
        val bodyText = try {
            response.bodyAsText()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return ContributeResult.Error
        }
        val dto = runCatching { json.decodeFromString<ContributeResponseDto>(bodyText) }.getOrElse {
            return ContributeResult.Error
        }
        return ContributeResult.Success(dto.accepted)
    }

    /** Outcome of [discover]. */
    sealed interface DiscoverResult {
        data class Success(val response: DiscoverResponseDto) : DiscoverResult

        /** 409: the device taste vector is stale relative to the server's current model. */
        data object HandshakeMismatch : DiscoverResult

        /** No backend endpoint compiled into this build. */
        data object NotConfigured : DiscoverResult

        /** Transport / HTTP / parse failure. */
        data object Error : DiscoverResult
    }

    /** Outcome of [contribute]. */
    sealed interface ContributeResult {
        data class Success(val accepted: Int) : ContributeResult
        data object NotConfigured : ContributeResult
        data object Error : ContributeResult
    }

    private companion object {
        /** Server contract: at most 256 vectors per contribute call. */
        const val MAX_VECTORS = 256
    }
}
