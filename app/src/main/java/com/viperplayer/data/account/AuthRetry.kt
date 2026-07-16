package com.viperplayer.data.account

/**
 * Transport-agnostic "run an authenticated call, refresh-and-retry once on UNAUTHENTICATED" policy,
 * extracted as a pure suspend function so it can be unit-tested without a live gRPC channel or a real
 * credential store.
 *
 * @param provideToken returns the current valid access token (already transparently refreshed if it
 *        was about to expire), or null when there is no usable session.
 * @param forceRefresh forces a token refresh with the stored refresh token, returning the new access
 *        token or null when refresh failed (rejected/offline). Called only after the server rejects a
 *        token that looked valid locally.
 * @param call runs the actual RPC with the given access token.
 *
 * Returns the call result; [AccountApiResult.Unauthenticated] when there is no token, or when the
 * single refresh+retry also came back unauthenticated / refresh failed.
 */
suspend fun <T> runAuthenticated(
    provideToken: suspend () -> String?,
    forceRefresh: suspend () -> String?,
    call: suspend (accessToken: String) -> AccountApiResult<T>,
): AccountApiResult<T> {
    val token = provideToken() ?: return AccountApiResult.Unauthenticated
    val first = call(token)
    if (first !is AccountApiResult.Unauthenticated) return first

    // Token rejected server-side despite looking valid locally — force a refresh and retry exactly once.
    val refreshed = forceRefresh() ?: return AccountApiResult.Unauthenticated
    return call(refreshed)
}
