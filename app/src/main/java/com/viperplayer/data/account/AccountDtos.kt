package com.viperplayer.data.account

import kotlinx.serialization.Serializable

/**
 * JSON DTOs for the ViPER backend account API (the `/auth/` routes; github.com/iscle/viper-backend).
 * The camelCase field names mirror the backend's Go response structs exactly (see internal/httpapi/
 * auth.go), so no `@SerialName` remapping is needed.
 */

@Serializable
data class RegisterRequestDto(val email: String, val password: String, val displayName: String)

@Serializable
data class LoginRequestDto(val email: String, val password: String)

@Serializable
data class RefreshRequestDto(val refreshToken: String)

/** `POST /auth/refresh` wraps the new pair under `tokens` (the client unwraps it). */
@Serializable
data class RefreshResponseDto(val tokens: TokenPairDto)

/** `POST /auth/register` + `POST /auth/login` response. */
@Serializable
data class AuthDto(val user: UserDto, val tokens: TokenPairDto)

@Serializable
data class UserDto(
    val id: String,
    val email: String,
    val displayName: String = "",
    val createdAtMs: Long = 0,
)

@Serializable
data class TokenPairDto(
    val accessToken: String,
    val refreshToken: String,
    val accessExpiresAtMs: Long = 0,
)

/** Backend error envelope: `{"error": "..."}` on any non-2xx. */
@Serializable
internal data class ErrorDto(val error: String = "")
