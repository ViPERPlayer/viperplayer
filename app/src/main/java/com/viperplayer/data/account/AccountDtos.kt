package com.viperplayer.data.account

import kotlinx.serialization.Serializable

/** Outcome of an account API call. */
sealed interface AccountApiResult<out T> {
    data class Success<T>(val value: T) : AccountApiResult<T>

    /** The backend responded with a non-2xx status; [message] is user-presentable. */
    data class Rejected(val status: Int, val message: String) : AccountApiResult<Nothing>

    /** Transport failure (offline / timeout). */
    data object NetworkError : AccountApiResult<Nothing>

    /** No backend URL configured. */
    data object NotConfigured : AccountApiResult<Nothing>
}

// --- JSON DTOs (camelCase; mirror account/v1 + common/v1) ---

@Serializable
data class RegisterRequestDto(val email: String, val password: String, val displayName: String)

@Serializable
data class LoginRequestDto(val email: String, val password: String)

@Serializable
data class RefreshRequestDto(val refreshToken: String)

@Serializable
data class RefreshResponseDto(val tokens: TokenPairDto)

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

@Serializable
internal data class ErrorDto(val error: String = "")
