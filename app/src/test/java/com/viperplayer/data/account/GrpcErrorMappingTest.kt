package com.viperplayer.data.account

import io.grpc.Status
import io.grpc.StatusException
import io.grpc.StatusRuntimeException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Unit tests for the gRPC status → domain-error mapping ([mapGrpcStatus] / [toAccountApiResult]).
 * These lock down the contract the repository relies on: UNAUTHENTICATED → refresh/sign-out,
 * ALREADY_EXISTS → email-taken, INVALID_ARGUMENT → validation, UNAVAILABLE → network, etc.
 */
class GrpcErrorMappingTest {

    @Test
    fun unauthenticated_mapsToUnauthenticated() {
        assertEquals(
            AccountApiResult.Unauthenticated,
            mapGrpcStatus(Status.Code.UNAUTHENTICATED, "token expired"),
        )
    }

    @Test
    fun alreadyExists_mapsToRejected_withServerMessage() {
        val result = mapGrpcStatus(Status.Code.ALREADY_EXISTS, "that email is taken")
        assertEquals(AccountApiResult.Rejected("that email is taken"), result)
    }

    @Test
    fun alreadyExists_blankMessage_usesFallback() {
        val result = mapGrpcStatus(Status.Code.ALREADY_EXISTS, "   ")
        assertTrue(result is AccountApiResult.Rejected)
        assertEquals("That email is already registered", (result as AccountApiResult.Rejected).message)
    }

    @Test
    fun invalidArgument_mapsToRejected() {
        val result = mapGrpcStatus(Status.Code.INVALID_ARGUMENT, "password too weak")
        assertEquals(AccountApiResult.Rejected("password too weak"), result)
    }

    @Test
    fun permissionDenied_mapsToRejected() {
        val result = mapGrpcStatus(Status.Code.PERMISSION_DENIED, null)
        assertTrue(result is AccountApiResult.Rejected)
    }

    @Test
    fun unavailable_mapsToNetworkError() {
        assertEquals(AccountApiResult.NetworkError, mapGrpcStatus(Status.Code.UNAVAILABLE, "conn refused"))
    }

    @Test
    fun deadlineExceeded_mapsToNetworkError() {
        assertEquals(AccountApiResult.NetworkError, mapGrpcStatus(Status.Code.DEADLINE_EXCEEDED, null))
    }

    @Test
    fun unknownCode_mapsToRejected_withCodeInFallback() {
        val result = mapGrpcStatus(Status.Code.INTERNAL, null)
        assertTrue(result is AccountApiResult.Rejected)
        assertTrue((result as AccountApiResult.Rejected).message.contains("INTERNAL"))
    }

    @Test
    fun statusException_isMapped() {
        val ex: Throwable = StatusException(Status.UNAUTHENTICATED)
        assertEquals(AccountApiResult.Unauthenticated, ex.toAccountApiResult())
    }

    @Test
    fun statusRuntimeException_isMapped() {
        val ex: Throwable = StatusRuntimeException(Status.ALREADY_EXISTS.withDescription("dup"))
        assertEquals(AccountApiResult.Rejected("dup"), ex.toAccountApiResult())
    }

    @Test
    fun nonGrpcThrowable_mapsToNetworkError() {
        val ex: Throwable = IOException("socket closed")
        assertEquals(AccountApiResult.NetworkError, ex.toAccountApiResult())
    }
}
