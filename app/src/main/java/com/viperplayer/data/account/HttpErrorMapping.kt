package com.viperplayer.data.account

import com.viperplayer.R
import com.viperplayer.data.resources.StringProvider
import com.viperplayer.domain.account.AccountApiResult

/**
 * The locale-resolved fallback messages [mapAccountHttpError] uses when the backend supplies no
 * `{"error": ...}` body. Passed in by callers (which can resolve resources) so the mapper itself
 * stays pure + unit-testable — no Android imports, no `Context`.
 */
data class AccountErrorFallbacks(
    val emailTaken: String,
    val badRequest: String,
    val notFound: String,
    /** A `%1$d`-style format string; [mapAccountHttpError] fills in the status code. */
    val requestFailedFormat: String,
)

/**
 * Maps a non-2xx HTTP status (plus the optional server-supplied `{"error": ...}` message) from the
 * ViPER account backend into the transport-agnostic [AccountApiResult]. Pure and side-effect free so
 * the status → domain-error mapping is unit-testable without a live HttpClient — user-facing fallback
 * strings are supplied by the caller via [fallbacks] rather than resolved here.
 *
 * The backend signals rejections with stable HTTP status codes (internal/httpapi/auth.go):
 *  - 401 → the credentials/token were rejected ([AccountApiResult.Unauthenticated]). On login this is
 *    bad credentials; on an authenticated call it is an expired/invalid access token, so the caller
 *    may refresh + retry (see [runAuthenticated]). Non-destructive on its own.
 *  - 409 → email already registered.
 *  - 400 → validation failure (bad email, weak password…).
 *  - 404 → not found.
 *  - 408 / 429 / 5xx → transient server/transport condition, treated as retryable
 *    ([AccountApiResult.NetworkError]) so a momentary outage never masquerades as a hard rejection and
 *    clears the session mid-refresh.
 * Any other status is treated as a rejection carrying the server's message when present.
 */
fun mapAccountHttpError(
    status: Int,
    serverMessage: String?,
    fallbacks: AccountErrorFallbacks,
): AccountApiResult<Nothing> = when (status) {
    401 -> AccountApiResult.Unauthenticated
    409 -> AccountApiResult.Rejected(serverMessage.orDefault(fallbacks.emailTaken))
    400 -> AccountApiResult.Rejected(serverMessage.orDefault(fallbacks.badRequest))
    404 -> AccountApiResult.Rejected(serverMessage.orDefault(fallbacks.notFound))
    408, 429, in 500..599 -> AccountApiResult.NetworkError
    else -> AccountApiResult.Rejected(serverMessage.orDefault(String.format(fallbacks.requestFailedFormat, status)))
}

/** Resolves the localized account-error fallbacks from the app's string resources. */
fun StringProvider.accountErrorFallbacks(): AccountErrorFallbacks = AccountErrorFallbacks(
    emailTaken = getString(R.string.account_error_email_taken),
    badRequest = getString(R.string.account_error_bad_request),
    notFound = getString(R.string.account_error_not_found),
    requestFailedFormat = getString(R.string.account_error_request_failed),
)

private fun String?.orDefault(fallback: String): String =
    this?.trim()?.takeIf { it.isNotBlank() } ?: fallback
