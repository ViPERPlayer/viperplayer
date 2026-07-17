package com.viperplayer.data.account

/**
 * Maps a non-2xx HTTP status (plus the optional server-supplied `{"error": ...}` message) from the
 * ViPER account backend into the transport-agnostic [AccountApiResult]. Pure and side-effect free so
 * the status → domain-error mapping is unit-testable without a live HttpClient.
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
fun mapAccountHttpError(status: Int, serverMessage: String?): AccountApiResult<Nothing> = when (status) {
    401 -> AccountApiResult.Unauthenticated
    409 -> AccountApiResult.Rejected(serverMessage.orDefault("That email is already registered"))
    400 -> AccountApiResult.Rejected(serverMessage.orDefault("Check your details and try again"))
    404 -> AccountApiResult.Rejected(serverMessage.orDefault("Not found"))
    408, 429, in 500..599 -> AccountApiResult.NetworkError
    else -> AccountApiResult.Rejected(serverMessage.orDefault("Request failed ($status)"))
}

private fun String?.orDefault(fallback: String): String =
    this?.trim()?.takeIf { it.isNotBlank() } ?: fallback
