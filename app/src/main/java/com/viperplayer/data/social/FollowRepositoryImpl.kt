package com.viperplayer.data.social

import com.viperplayer.data.account.AccountApiResult
import com.viperplayer.domain.account.AccountRepository
import com.viperplayer.domain.social.FollowRepository
import com.viperplayer.domain.social.FollowResult
import com.viperplayer.domain.social.FollowUser
import com.viperplayer.domain.social.Relationship
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default [FollowRepository]: the ViPER backend's one-way follow graph + user discovery (social S1),
 * over [FollowApi]. Every call is authenticated through [AccountRepository.withBackendAuth] — the shared
 * refresh-and-retry token seam — and mapped from the transport-level [AccountApiResult] into the domain
 * [FollowResult].
 *
 * Backend-gated: when no backend URL is compiled in ([isConfigured] is false) every call short-circuits
 * to [FollowResult.NotConfigured] without touching the network, so the discovery/following UI degrades
 * to its gated state (matching how the other social seams behave when the backend is off).
 */
@Singleton
class FollowRepositoryImpl @Inject constructor(
    private val followApi: FollowApi,
    private val accountRepository: AccountRepository,
) : FollowRepository {

    override val isConfigured: Boolean get() = followApi.isConfigured

    override suspend fun search(query: String): FollowResult<List<FollowUser>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return FollowResult.Success(emptyList())
        return authed { token -> followApi.searchUsers(token, trimmed) }
            .map { users -> users.map { it.toFollowUser() } }
    }

    override suspend fun userByHandle(handle: String): FollowResult<FollowUser> =
        authed { token -> followApi.userByHandle(token, handle) }.map { it.toFollowUser() }

    override suspend fun follow(userId: String): FollowResult<Unit> =
        authed { token -> followApi.follow(token, userId) }

    override suspend fun unfollow(userId: String): FollowResult<Unit> =
        authed { token -> followApi.unfollow(token, userId) }

    override suspend fun following(): FollowResult<List<FollowUser>> =
        authed { token -> followApi.following(token) }.map { users -> users.map { it.toFollowUser() } }

    override suspend fun followers(): FollowResult<List<FollowUser>> =
        authed { token -> followApi.followers(token) }.map { users -> users.map { it.toFollowUser() } }

    override suspend fun relationship(userId: String): FollowResult<Relationship> =
        authed { token -> followApi.relationship(token, userId) }.map {
            Relationship(
                isFollowing = it.isFollowing,
                followsMe = it.followsMe,
                followerCount = it.followerCount,
                followingCount = it.followingCount,
            )
        }

    /**
     * Runs an authenticated [FollowApi] call through the account token seam and maps the transport
     * [AccountApiResult] into a domain [FollowResult]. Short-circuits to [FollowResult.NotConfigured]
     * when the backend isn't built in, so no token is fetched and no request is made.
     */
    private suspend fun <T> authed(
        call: suspend (accessToken: String) -> AccountApiResult<T>,
    ): FollowResult<T> {
        if (!followApi.isConfigured) return FollowResult.NotConfigured
        return accountRepository.withBackendAuth(call).toFollowResult()
    }
}

/** Maps a transport [AccountApiResult] into the domain [FollowResult]. */
internal fun <T> AccountApiResult<T>.toFollowResult(): FollowResult<T> = when (this) {
    is AccountApiResult.Success -> FollowResult.Success(value)
    is AccountApiResult.Rejected -> FollowResult.Failed(message)
    AccountApiResult.Unauthenticated -> FollowResult.NotAuthenticated
    AccountApiResult.NetworkError -> FollowResult.NetworkError
    AccountApiResult.NotConfigured -> FollowResult.NotConfigured
}

/** Maps only the value of a [FollowResult.Success]; every other variant passes through unchanged. */
internal inline fun <T, R> FollowResult<T>.map(transform: (T) -> R): FollowResult<R> = when (this) {
    is FollowResult.Success -> FollowResult.Success(transform(value))
    is FollowResult.Failed -> this
    FollowResult.NotAuthenticated -> FollowResult.NotAuthenticated
    FollowResult.NetworkError -> FollowResult.NetworkError
    FollowResult.NotConfigured -> FollowResult.NotConfigured
}

/** Maps a wire [PublicUserDto] into the domain [FollowUser], defaulting a blank display name to the handle. */
internal fun PublicUserDto.toFollowUser(): FollowUser = FollowUser(
    userId = userId,
    handle = handle,
    displayName = displayName.ifBlank { handle },
)
