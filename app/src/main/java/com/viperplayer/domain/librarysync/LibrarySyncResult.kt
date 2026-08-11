package com.viperplayer.domain.librarysync

import com.viperplayer.domain.account.AccountApiResult

/**
 * Transport-agnostic outcome of a ViPER backend library-sync call. The repository maps the account
 * transport's `AccountApiResult` into this so callers above the repository never see a raw HTTP
 * status or the account-transport type.
 */
sealed interface LibrarySyncResult<out T> {
    data class Success<T>(val value: T) : LibrarySyncResult<T>

    /** The backend rejected the request (bad payload, permanent failure). */
    data class Rejected(val message: String) : LibrarySyncResult<Nothing>

    /** The session is missing/expired and a refresh failed — the caller should treat it as signed out. */
    data object Unauthenticated : LibrarySyncResult<Nothing>

    /** Transport failure (offline / timeout / server unreachable) — retry later. */
    data object NetworkError : LibrarySyncResult<Nothing>

    /** No backend endpoint configured. */
    data object NotConfigured : LibrarySyncResult<Nothing>
}
