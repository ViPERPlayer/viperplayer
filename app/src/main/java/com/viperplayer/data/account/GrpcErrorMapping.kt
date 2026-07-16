package com.viperplayer.data.account

import io.grpc.Status
import io.grpc.StatusException
import io.grpc.StatusRuntimeException

/**
 * Maps a gRPC failure into the transport-agnostic [AccountApiResult]. Pure and side-effect free so
 * the status→domain-error mapping is unit-testable without a live channel.
 *
 * The backend signals rejections with standard gRPC status codes (auth interceptor + handlers,
 * viper-backend DESIGN §5.6):
 *  - UNAUTHENTICATED        → the access token is missing/expired/invalid ([AccountApiResult.Unauthenticated]).
 *  - ALREADY_EXISTS         → email already registered.
 *  - INVALID_ARGUMENT       → validation failure (bad email, weak password…).
 *  - PERMISSION_DENIED      → not allowed (surfaced as a rejection).
 *  - UNAVAILABLE / DEADLINE_EXCEEDED / ABORTED / CANCELLED → transport/reachability problem, treated
 *    as retryable ([AccountApiResult.NetworkError]) — non-destructive, never clears the session.
 * Any other code is treated as a rejection carrying the server's message.
 */
fun mapGrpcStatus(code: Status.Code, description: String?): AccountApiResult<Nothing> = when (code) {
    Status.Code.UNAUTHENTICATED -> AccountApiResult.Unauthenticated

    Status.Code.UNAVAILABLE,
    Status.Code.DEADLINE_EXCEEDED,
    Status.Code.ABORTED,
    Status.Code.CANCELLED -> AccountApiResult.NetworkError

    Status.Code.ALREADY_EXISTS ->
        AccountApiResult.Rejected(description.orDefault("That email is already registered"))

    Status.Code.INVALID_ARGUMENT,
    Status.Code.FAILED_PRECONDITION,
    Status.Code.OUT_OF_RANGE ->
        AccountApiResult.Rejected(description.orDefault("Check your details and try again"))

    Status.Code.PERMISSION_DENIED ->
        AccountApiResult.Rejected(description.orDefault("You don't have access to that"))

    Status.Code.NOT_FOUND ->
        AccountApiResult.Rejected(description.orDefault("Not found"))

    else -> AccountApiResult.Rejected(description.orDefault("Request failed (${code.name})"))
}

/** Maps a thrown gRPC exception, extracting its [Status] code + description. */
fun Throwable.toAccountApiResult(): AccountApiResult<Nothing> = when (this) {
    is StatusException -> mapGrpcStatus(status.code, status.description)
    is StatusRuntimeException -> mapGrpcStatus(status.code, status.description)
    // Not a gRPC status (e.g. a raw IOException from the transport) — treat as unreachable.
    else -> AccountApiResult.NetworkError
}

private fun String?.orDefault(fallback: String): String =
    this?.trim()?.takeIf { it.isNotBlank() } ?: fallback
