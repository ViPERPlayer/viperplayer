package com.viperplayer.data.account

/**
 * Transport-agnostic outcome of an account backend call. The repository maps this into the domain
 * [com.viperplayer.domain.account.AuthResult]; the HTTP client ([AccountApi]) produces it from a
 * decoded 2xx response or a non-2xx status (see [mapAccountHttpError]).
 */
sealed interface AccountApiResult<out T> {
    data class Success<T>(val value: T) : AccountApiResult<T>

    /** The backend rejected the request (bad credentials, email taken, validation…). */
    data class Rejected(val message: String) : AccountApiResult<Nothing>

    /** The access token was missing/expired/invalid — the caller should refresh or sign out. */
    data object Unauthenticated : AccountApiResult<Nothing>

    /** Transport failure (offline / timeout / server unreachable). */
    data object NetworkError : AccountApiResult<Nothing>

    /** No backend endpoint configured. */
    data object NotConfigured : AccountApiResult<Nothing>
}
