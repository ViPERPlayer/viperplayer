package com.viperplayer.data.lastfm

import com.viperplayer.BuildConfig
import com.viperplayer.domain.lastfm.LastfmRequests
import com.viperplayer.domain.lastfm.LastfmSigner
import com.viperplayer.domain.lastfm.PendingScrobble
import com.viperplayer.domain.lastfm.ScrobbleTrack
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin network client for the Last.fm 2.0 web API, built on the app's shared Ktor [HttpClient].
 * Signs every call with [LastfmSigner], POSTs the parameters as a form body, and requests JSON.
 *
 * All builders/signing are the pure [com.viperplayer.domain.lastfm] helpers; this class only performs
 * I/O and maps the JSON response into a [LastfmResult]. It never logs the API secret, the session key,
 * or the password.
 *
 * The API key + shared secret come from [BuildConfig.LASTFM_API_KEY] / [BuildConfig.LASTFM_API_SECRET],
 * which ship as PLACEHOLDERS — a real key/secret from https://www.last.fm/api/account/create is
 * required for any call to succeed. [isConfigured] reports whether real values are present.
 */
@Singleton
class LastfmApi @Inject constructor(
    private val httpClient: HttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val apiKey: String get() = BuildConfig.LASTFM_API_KEY
    private val apiSecret: String get() = BuildConfig.LASTFM_API_SECRET

    /** Whether a real (non-placeholder, non-blank) API key + secret are configured at build time. */
    val isConfigured: Boolean
        get() = apiKey.isNotBlank() && apiSecret.isNotBlank() &&
                apiKey != PLACEHOLDER && apiSecret != PLACEHOLDER

    /**
     * Exchanges [username] + [password] for a session key via `auth.getMobileSession`.
     *
     * SECURITY: the password is only referenced inside this call's parameter map to compute the
     * signature and form body, and is discarded when the method returns. It is never stored or logged.
     */
    suspend fun getMobileSession(username: String, password: String): LastfmResult<LastfmSession> {
        if (!isConfigured) return LastfmResult.NotConfigured
        val params = LastfmRequests.getMobileSession(apiKey, username, password)
        return post(params) { body ->
            val parsed = json.decodeFromString<SessionResponse>(body)
            parsed.session?.let { LastfmSession(name = it.name, key = it.key) }
        }
    }

    /** Sends `track.updateNowPlaying` for [track] using [sessionKey]. */
    suspend fun updateNowPlaying(sessionKey: String, track: ScrobbleTrack): LastfmResult<Unit> {
        if (!isConfigured) return LastfmResult.NotConfigured
        val params = LastfmRequests.updateNowPlaying(apiKey, sessionKey, track)
        return post(params) { Unit }
    }

    /**
     * Submits a `track.scrobble` batch (1..[LastfmRequests.MAX_BATCH]) using [sessionKey]. Past
     * timestamps are honored, which is how the offline queue drains.
     */
    suspend fun scrobble(sessionKey: String, scrobbles: List<PendingScrobble>): LastfmResult<Unit> {
        if (!isConfigured) return LastfmResult.NotConfigured
        if (scrobbles.isEmpty()) return LastfmResult.Success(Unit)
        val params = LastfmRequests.scrobble(apiKey, sessionKey, scrobbles)
        return post(params) { Unit }
    }

    /**
     * Signs [params], POSTs them as a form body to the API root (with `format=json` in the query),
     * and maps a 2xx JSON body via [map]. A Last.fm error envelope (`{"error":N,...}`) becomes
     * [LastfmResult.ApiError]; any transport failure becomes [LastfmResult.NetworkError] (the signal
     * to enqueue for retry). Never logs secrets.
     */
    private suspend fun <T> post(
        params: Map<String, String>,
        map: (String) -> T?,
    ): LastfmResult<T> {
        val signed = LastfmSigner.signed(params, apiSecret)
        val form = Parameters.build {
            signed.forEach { (key, value) -> append(key, value) }
        }
        val response: HttpResponse = try {
            httpClient.submitForm(url = API_ROOT, formParameters = form) {
                parameter("format", "json")
            }
        } catch (e: Exception) {
            Timber.w("Last.fm request failed (network): ${e.javaClass.simpleName}")
            return LastfmResult.NetworkError(e)
        }

        val bodyText = try {
            response.bodyAsText()
        } catch (e: Exception) {
            return LastfmResult.NetworkError(e)
        }

        // Last.fm returns HTTP 200 with an {"error":N} body for logical errors, and non-2xx for some
        // failures. Parse the error envelope regardless of status.
        val error = runCatching { json.decodeFromString<ErrorResponse>(bodyText) }.getOrNull()
        if (error?.error != null) {
            Timber.w("Last.fm API error ${error.error}: ${error.message}")
            return LastfmResult.ApiError(error.error, error.message.orEmpty())
        }
        if (!response.status.value.let { it in 200..299 }) {
            return LastfmResult.NetworkError(IllegalStateException("HTTP ${response.status.value}"))
        }

        val mapped = runCatching { map(bodyText) }.getOrNull()
            ?: return LastfmResult.ApiError(-1, "Malformed Last.fm response")
        return LastfmResult.Success(mapped)
    }

    @Serializable
    private data class SessionResponse(val session: SessionBody? = null)

    @Serializable
    private data class SessionBody(val name: String = "", val key: String = "")

    @Serializable
    private data class ErrorResponse(val error: Int? = null, val message: String? = null)

    companion object {
        const val API_ROOT = "https://ws.audioscrobbler.com/2.0/"

        /** The value the BuildConfig fields hold until a real key/secret is supplied. */
        const val PLACEHOLDER = "REPLACE_WITH_REAL_VALUE"
    }
}

/** The session returned by a successful login: the account name and the persisted session key. */
data class LastfmSession(val name: String, val key: String)

/**
 * Outcome of a Last.fm call, distinguishing failures the caller must treat differently:
 * - [Success]: the call succeeded.
 * - [ApiError]: Last.fm rejected it (bad auth, invalid params). NOT retried by re-queueing.
 * - [NetworkError]: transport/offline failure — the signal to enqueue a scrobble for later retry.
 * - [NotConfigured]: no real API key/secret is built in; nothing was sent.
 */
sealed interface LastfmResult<out T> {
    data class Success<T>(val value: T) : LastfmResult<T>
    data class ApiError(val code: Int, val message: String) : LastfmResult<Nothing>
    data class NetworkError(val cause: Throwable) : LastfmResult<Nothing>
    data object NotConfigured : LastfmResult<Nothing>
}
