package com.viperplayer.data.rec

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import timber.log.Timber
import java.io.FileDescriptor
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Decoded PCM head of an audio file: interleaved float samples, the file's native sample rate and its
 * channel count. This is the raw-audio contract [MelSpectrogram.computeInputFeaturesFromRaw] consumes
 * (it downmixes to mono then resamples to 48kHz internally).
 *
 * @param interleaved frame-major interleaved float PCM in [-1, 1]: `[c0,c1,...,c0,c1,...]`.
 * @param sampleRate  the source sample rate in Hz (the decoder does NOT resample).
 * @param channels    the source channel count (>= 1).
 */
data class DecodedAudio(
    val interleaved: FloatArray,
    val sampleRate: Int,
    val channels: Int,
) {
    /** Number of frames (per-channel samples). */
    val frameCount: Int get() = if (channels <= 0) 0 else interleaved.size / channels

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DecodedAudio) return false
        if (sampleRate != other.sampleRate) return false
        if (channels != other.channels) return false
        return interleaved.contentEquals(other.interleaved)
    }

    override fun hashCode(): Int {
        var result = interleaved.contentHashCode()
        result = 31 * result + sampleRate
        result = 31 * result + channels
        return result
    }
}

/**
 * Decodes the first few seconds of a compressed audio file (mp3 / m4a-aac / flac / ogg-vorbis-opus /
 * wav) to interleaved float PCM at the file's NATIVE sample rate. Deliberately abstracted behind an
 * interface so the [LibraryEmbedder] / [LibraryIndexWorker] orchestration can be unit-tested off-device
 * with a fake — the concrete [MediaCodecAudioDecoder] needs a real device/emulator codec.
 */
interface AudioDecoder {

    /**
     * Decode from a file path. Returns null when the file cannot be opened / has no audio track / the
     * codec fails (the caller then SKIPS that song rather than aborting the whole run).
     */
    fun decode(filePath: String): DecodedAudio?

    /**
     * Decode from an open [FileDescriptor] (e.g. a `content://` URI the caller resolved via a
     * `ContentResolver`). The decoder does NOT take ownership of the FD; the caller closes it.
     */
    fun decode(fd: FileDescriptor): DecodedAudio?
}

/**
 * `MediaExtractor` + `MediaCodec` implementation of [AudioDecoder].
 *
 * ## Why only the head of the track
 * The CLAP front-end fixes the clip to [ClapModel.CLIP_SAMPLES] = 480000 samples = 10s at 48kHz, and
 * [MelSpectrogram.repeatPadOrCrop] pins the crop to idx 0 (the FIRST 10s). So there is no value in
 * decoding a whole 4-minute track: we decode only ~11s of source audio (a small margin over 10s so the
 * post-resample buffer comfortably reaches 480000 samples), then stop. On a short file (< ~11s) we
 * decode all of it and the mel front-end repeat-pads it up to 10s.
 *
 * ## PCM layout
 * `MediaCodec` yields interleaved PCM. On API 24+ the decoder may emit float PCM
 * ([MediaFormat.KEY_PCM_ENCODING] == [android.media.AudioFormat.ENCODING_PCM_FLOAT]); otherwise it is
 * 16-bit signed little-endian. Both are de-interleaved into a frame-major float array and scaled to
 * [-1, 1]. Downmix to mono is left to [MelSpectrogram.downmixToMono] (the single canonical downmix).
 *
 * ## Robustness
 * Any failure (no audio track, unsupported codec, decode exception, empty output) returns null so the
 * indexer skips that one song. Synchronous (dequeue-loop) decode is used for simplicity and determinism.
 */
class MediaCodecAudioDecoder(
    /** Stop decoding once this many source frames are captured. See the class doc for the ~11s budget. */
    private val maxSourceDurationSeconds: Double = DECODE_SECONDS,
) : AudioDecoder {

    override fun decode(filePath: String): DecodedAudio? =
        runDecode { it.setDataSource(filePath) }

    override fun decode(fd: FileDescriptor): DecodedAudio? =
        runDecode { it.setDataSource(fd) }

    /**
     * Shared decode driver: [configureSource] points a fresh [MediaExtractor] at the input, then we
     * select the first audio track, spin up its [MediaCodec] and pump buffers until we have enough
     * frames (or hit EOS). All resources are released in `finally`. Returns null on any failure.
     */
    private fun runDecode(configureSource: (MediaExtractor) -> Unit): DecodedAudio? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            configureSource(extractor)
            val trackIndex = firstAudioTrack(extractor) ?: run {
                Timber.w("AudioDecoder: no audio track")
                return null
            }
            extractor.selectTrack(trackIndex)
            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return null

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            return pumpDecoder(extractor, codec)
        } catch (e: Exception) {
            Timber.w(e, "AudioDecoder: decode failed")
            return null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    /** Index of the first track whose MIME starts with `audio/`, or null when none exists. */
    private fun firstAudioTrack(extractor: MediaExtractor): Int? {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) return i
        }
        return null
    }

    /**
     * Synchronous dequeue loop: feed encoded frames from [extractor] into [codec], drain decoded PCM
     * into a growing float list, and stop at EOS or once we've captured [maxSourceDurationSeconds] of
     * audio. The output sample rate / channel count are read from the codec's OUTPUT format (the
     * decoder is authoritative — some containers under-report on the track format).
     */
    private fun pumpDecoder(extractor: MediaExtractor, codec: MediaCodec): DecodedAudio? {
        val bufferInfo = MediaCodec.BufferInfo()
        val pcm = FloatArrayBuilder()
        var sampleRate = 0
        var channels = 0
        var pcmEncoding = ENCODING_UNKNOWN
        var sawInputEos = false
        var sawOutputEos = false
        var maxFrames = Int.MAX_VALUE // recomputed once we know the sample rate

        while (!sawOutputEos) {
            if (!sawInputEos) {
                val inIndex = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                if (inIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inIndex)
                    val sampleSize = inputBuffer?.let { extractor.readSampleData(it, 0) } ?: -1
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        sawInputEos = true
                    } else {
                        codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            val outIndex = codec.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)
            when {
                outIndex >= 0 -> {
                    if (bufferInfo.size > 0) {
                        val outputBuffer = codec.getOutputBuffer(outIndex)
                        if (outputBuffer != null) {
                            appendPcm(outputBuffer, bufferInfo, pcmEncoding, pcm)
                        }
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        sawOutputEos = true
                    }
                    if (pcm.size >= maxFrames * channels && channels > 0) sawOutputEos = true
                }
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val outFormat = codec.outputFormat
                    sampleRate = outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    pcmEncoding = resolvePcmEncoding(outFormat)
                    if (sampleRate > 0) {
                        maxFrames = Math.ceil(maxSourceDurationSeconds * sampleRate).toInt()
                    }
                }
                // INFO_TRY_AGAIN_LATER / other negative codes: just loop again.
            }
        }

        if (sampleRate <= 0 || channels <= 0 || pcm.size == 0) {
            Timber.w("AudioDecoder: empty/invalid output (sr=$sampleRate ch=$channels n=${pcm.size})")
            return null
        }
        return DecodedAudio(interleaved = pcm.toArray(), sampleRate = sampleRate, channels = channels)
    }

    /** Reads the output [buffer]'s PCM (float or 16-bit LE) into [out] as floats in [-1, 1]. */
    private fun appendPcm(
        buffer: ByteBuffer,
        info: MediaCodec.BufferInfo,
        encoding: Int,
        out: FloatArrayBuilder,
    ) {
        buffer.position(info.offset)
        buffer.limit(info.offset + info.size)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        if (encoding == android.media.AudioFormat.ENCODING_PCM_FLOAT) {
            val fb = buffer.asFloatBuffer()
            val n = fb.remaining()
            for (i in 0 until n) out.add(fb.get())
        } else {
            // Default / ENCODING_PCM_16BIT: signed 16-bit LE -> [-1, 1].
            val sb = buffer.asShortBuffer()
            val n = sb.remaining()
            for (i in 0 until n) out.add(sb.get() / 32768.0f)
        }
    }

    /** Reads [MediaFormat.KEY_PCM_ENCODING] when present (API 24+), else assumes 16-bit. */
    private fun resolvePcmEncoding(format: MediaFormat): Int =
        if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
            format.getInteger(MediaFormat.KEY_PCM_ENCODING)
        } else {
            android.media.AudioFormat.ENCODING_PCM_16BIT
        }

    companion object {
        /**
         * Seconds of SOURCE audio to decode. 11s > the 10s (480000-sample @ 48kHz) clip contract, with
         * ~1s margin so the resampled buffer comfortably covers 480000 samples for any source rate.
         */
        const val DECODE_SECONDS = 11.0

        private const val DEQUEUE_TIMEOUT_US = 10_000L
        private const val ENCODING_UNKNOWN = -1
    }
}

/**
 * Amortized-growth float buffer (avoids boxing an `ArrayList<Float>` in the decode hot loop and the
 * O(n) copies of repeatedly resizing a raw FloatArray one chunk at a time).
 */
private class FloatArrayBuilder(initialCapacity: Int = 1 shl 16) {
    private var buf = FloatArray(initialCapacity)
    var size = 0
        private set

    fun add(value: Float) {
        if (size == buf.size) buf = buf.copyOf(buf.size * 2)
        buf[size++] = value
    }

    fun toArray(): FloatArray = buf.copyOf(size)
}
