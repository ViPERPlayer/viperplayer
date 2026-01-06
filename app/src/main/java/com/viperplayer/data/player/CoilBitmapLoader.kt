package com.viperplayer.data.player

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.guava.future

@UnstableApi
class CoilBitmapLoader(
    private val context: Context
) : BitmapLoader {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun supportsMimeType(mimeType: String): Boolean {
        return Util.isBitmapFactorySupportedMimeType(mimeType)
    }

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        return scope.future { load(data) }
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        return scope.future { load(uri) }
    }

    private suspend fun load(data: Any): Bitmap {
        val request = ImageRequest.Builder(context)
            .data(data)
            .allowHardware(false)
            .build()

        val result = context.imageLoader.execute(request)

        return result.image?.toBitmap() ?: error("Failed to decode bitmap")
    }
}