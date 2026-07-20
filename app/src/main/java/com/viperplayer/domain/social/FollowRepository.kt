package com.viperplayer.domain.social

/**
 * A public user in the one-way follow graph (social S1): the stable [userId], their public [handle]
 * (no leading `@`) and [displayName]. Surfaced by discovery search and the following/followers lists.
 * Plain immutable value type with no Android or transport dependencies.
 */
data class FollowUser(
    val userId: String,
    val handle: String,
    val displayName: String,
)

/** The caller's relationship to another user: the follow edges plus that user's aggregate counts. */
data class Relationship(
    val isFollowing: Boolean,
    val followsMe: Boolean,
    val followerCount: Int,
    val followingCount: Int,
)

/**
 * The one-way follow graph + user discovery for social S1 (public `@handle` + follow). Backed by the
 * ViPER backend when configured; when it isn't, every call returns [FollowResult.NotConfigured] and the
 * lists come back empty, so the discovery/following UI shows its gated state.
 *
 * All calls are authenticated (they run through the account token seam) — a signed-out / expired
 * session surfaces as [FollowResult.NotAuthenticated] so the UI can route to sign-in.
 */
interface FollowRepository {

    /** Whether a backend URL is configured for this build (mirrors the social feature gate). */
    val isConfigured: Boolean

    /** Finds public users by name/handle. Returns an empty list (Success) when [query] matches nobody. */
    suspend fun search(query: String): FollowResult<List<FollowUser>>

    /** Resolves a single user by exact `@handle`. [FollowResult.Failed] when no such handle exists. */
    suspend fun userByHandle(handle: String): FollowResult<FollowUser>

    /** Follows [userId]. Idempotent server-side. */
    suspend fun follow(userId: String): FollowResult<Unit>

    /** Unfollows [userId]. Idempotent server-side. */
    suspend fun unfollow(userId: String): FollowResult<Unit>

    /** The users the signed-in account currently follows. */
    suspend fun following(): FollowResult<List<FollowUser>>

    /** The users following the signed-in account. */
    suspend fun followers(): FollowResult<List<FollowUser>>

    /** The caller's [Relationship] to [userId] (edges + counts). */
    suspend fun relationship(userId: String): FollowResult<Relationship>
}

/**
 * Transport-agnostic outcome of a [FollowRepository] call — the follow-graph analogue of
 * [com.viperplayer.domain.account.AuthResult], letting the UI branch on success / rejection / offline /
 * signed-out / unconfigured without seeing HTTP statuses.
 */
sealed interface FollowResult<out T> {
    data class Success<T>(val value: T) : FollowResult<T>

    /** The backend rejected the request (e.g. handle not found, validation). */
    data class Failed(val message: String) : FollowResult<Nothing>

    /** No usable session — the caller should route to sign-in. */
    data object NotAuthenticated : FollowResult<Nothing>

    /** Could not reach the backend (offline / transport error) — the user can retry. */
    data object NetworkError : FollowResult<Nothing>

    /** No backend URL is configured; the follow graph is unavailable in this build. */
    data object NotConfigured : FollowResult<Nothing>
}
