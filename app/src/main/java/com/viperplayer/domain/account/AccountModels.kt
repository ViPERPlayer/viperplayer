package com.viperplayer.domain.account

/**
 * The signed-in ViPER account, surfaced to the UI. Pure data. The access/refresh
 * TOKENS are deliberately NOT exposed here — they live only in the credential
 * store and must never leak into UI state or logs.
 */
data class AccountUser(
    val id: String,
    val email: String,
    val displayName: String,
    /** Epoch millis the account was created, or 0 when unknown (pre-existing sessions before this field). */
    val createdAtMs: Long = 0,
)

/**
 * The user-facing account state observed by the settings/account screen.
 *
 * @param user the signed-in account, or null when signed out.
 * @param isSignedIn whether a valid session is stored.
 */
data class AccountState(
    val user: AccountUser? = null,
    val isSignedIn: Boolean = false,
)

/** Result of a register/login attempt, surfaced to the account screen. */
sealed interface AuthResult {
    data class Success(val user: AccountUser) : AuthResult

    /** The server rejected the request (bad credentials, email taken, weak password…). */
    data class Failed(val message: String) : AuthResult

    /** Could not reach the backend (offline / transport error) — the user can retry. */
    data object NetworkError : AuthResult

    /** No backend URL is configured; auth is impossible until one is supplied. */
    data object NotConfigured : AuthResult
}
