package com.viperplayer.data.social

import kotlinx.serialization.Serializable

/**
 * JSON DTOs for the ViPER backend social/follow API (the `/users/` + `/social/` routes;
 * github.com/iscle/viper-backend). The camelCase field names mirror the backend's Go response structs
 * exactly, so no `@SerialName` remapping is needed. See [FollowApi] for the transport.
 */

/** A public user surfaced by search / by-handle lookup / follow lists. `@handle` never includes the `@`. */
@Serializable
data class PublicUserDto(
    val userId: String,
    val handle: String = "",
    val displayName: String = "",
)

/** `POST /social/follow` (Bearer) request body, `{"userId": "..."}`. */
@Serializable
data class FollowRequestDto(val userId: String)

/** `GET /users/search?q=` response wrapper. */
@Serializable
data class UserSearchResponseDto(val results: List<PublicUserDto> = emptyList())

/** `GET /social/following` + `GET /social/followers` response wrapper. */
@Serializable
data class FollowListResponseDto(
    val users: List<PublicUserDto> = emptyList(),
    val count: Int = 0,
)

/** `GET /social/relationship/{userId}` response: the follow edges + aggregate counts. */
@Serializable
data class RelationshipDto(
    val isFollowing: Boolean = false,
    val followsMe: Boolean = false,
    val followerCount: Int = 0,
    val followingCount: Int = 0,
)
