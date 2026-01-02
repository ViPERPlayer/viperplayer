package com.viperplayer.data.player.ktx

import androidx.media3.common.PlaybackException

fun PlaybackException.isNetworkError(): Boolean {
    return when {
        errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> true
        errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> true
        errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> true
        errorCode == PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE -> true
        errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> true
        errorCode == PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED -> true
        errorCode == PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE -> true
        else -> false
    }
}