package com.viperplayer.data.player

import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.viperplayer.plugin.model.AudioFormat
import com.viperplayer.plugin.model.PcmEncoding
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.io.SequenceInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Serves raw PCM arriving on a plugin's pipe FD as a WAV stream, so Media3's WavExtractor can decode
 * it without the plugin producing a container. A synthesized 44-byte WAV header (built from the
 * negotiated [AudioFormat]) is prepended to the pipe bytes. The audio itself flows through the
 * kernel pipe, never the binder.
 */
class PcmStreamDataSource(
    private val pfd: ParcelFileDescriptor,
    private val header: ByteArray,
) : BaseDataSource(/* isNetwork = */ true) {

    private var input: InputStream? = null
    private var uri: Uri? = null
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        if (dataSpec.position != 0L) {
            // A live PCM pipe can't be re-read from an arbitrary byte offset; refuse the seek
            // instead of serving misaligned audio. (Real seeking goes via the plugin's seekStream.)
            throw IOException("PCM stream does not support seeking (position=${dataSpec.position})")
        }
        transferInitializing(dataSpec)
        input = SequenceInputStream(
            ByteArrayInputStream(header),
            ParcelFileDescriptor.AutoCloseInputStream(pfd),
        )
        opened = true
        transferStarted(dataSpec)
        return C.LENGTH_UNSET.toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val read = input?.read(buffer, offset, length) ?: return C.RESULT_END_OF_INPUT
        if (read == -1) return C.RESULT_END_OF_INPUT
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        if (opened) {
            opened = false
            transferEnded()
        }
        runCatching { input?.close() }
        input = null
        // Close the pfd even if open() was never called, so the descriptor never leaks.
        runCatching { pfd.close() }
    }

    /** Single-use: a PCM pipe can be consumed exactly once, so the produced source is too. */
    class Factory(
        private val pfd: ParcelFileDescriptor,
        private val format: AudioFormat,
        private val durationMs: Long?,
    ) : DataSource.Factory {
        private var used = false
        override fun createDataSource(): DataSource {
            check(!used) { "PcmStreamDataSource.Factory is single-use; a PCM pipe can't be replayed" }
            used = true
            return PcmStreamDataSource(pfd, buildWavHeader(format, durationMs))
        }
    }

    companion object {
        fun buildWavHeader(format: AudioFormat, durationMs: Long?): ByteArray {
            val bitsPerSample = when (format.encoding) {
                PcmEncoding.PCM_16BIT -> 16
                PcmEncoding.PCM_24BIT -> 24
                PcmEncoding.PCM_FLOAT -> 32
            }
            val isFloat = format.encoding == PcmEncoding.PCM_FLOAT
            val formatCode = if (isFloat) 3 else 1 // 1 = PCM, 3 = IEEE float
            val channels = format.channelCount
            val sampleRate = format.sampleRate
            val byteRate = sampleRate * channels * bitsPerSample / 8
            val blockAlign = channels * bitsPerSample / 8
            val dataSize: Long = (if (durationMs != null && durationMs > 0) {
                durationMs * byteRate / 1000
            } else {
                // Live stream of unknown length: advertise a large size; the pipe EOF ends playback.
                (Int.MAX_VALUE - 64).toLong()
            }).coerceAtMost((Int.MAX_VALUE - 44).toLong()) // keep dataSize and 36+dataSize within Int

            return ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray(Charsets.US_ASCII))
                putInt((36 + dataSize).toInt())
                put("WAVE".toByteArray(Charsets.US_ASCII))
                put("fmt ".toByteArray(Charsets.US_ASCII))
                putInt(16)
                putShort(formatCode.toShort())
                putShort(channels.toShort())
                putInt(sampleRate)
                putInt(byteRate)
                putShort(blockAlign.toShort())
                putShort(bitsPerSample.toShort())
                put("data".toByteArray(Charsets.US_ASCII))
                putInt(dataSize.toInt())
            }.array()
        }
    }
}
