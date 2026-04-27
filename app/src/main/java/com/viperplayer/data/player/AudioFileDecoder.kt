package com.viperplayer.data.player

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioFileDecoder @Inject constructor() {

    /**
     * Decodes an audio file to a float array of PCM samples.
     * Returns Pair<FloatArray, Int> where Int is the channel count.
     * 
     * @param context Context to access content resolver
     * @param uri URI of the file to decode
     * @return Pair of (PCM Float Data, Channel Count), or null if decoding failed
     */
    suspend fun decodeToFloatArray(context: Context, uri: Uri): Pair<FloatArray, Int>? =
        withContext(Dispatchers.IO) {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(context, uri, null)
            } catch (e: Exception) {
                Timber.e(e, "Failed to set data source for URI: $uri")
                return@withContext null
            }

            var trackIndex = -1
            var format: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val trackFormat = extractor.getTrackFormat(i)
                val mime = trackFormat.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    trackIndex = i
                    format = trackFormat
                    break
                }
            }

            if (trackIndex == -1 || format == null) {
                Timber.e("No audio track found in file")
                extractor.release()
                return@withContext null
            }

            extractor.selectTrack(trackIndex)

            val mime = format.getString(MediaFormat.KEY_MIME) ?: return@withContext null
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            // We need to decode to PCM
            val codec = MediaCodec.createDecoderByType(mime)

            try {
                codec.configure(format, null, null, 0)
                codec.start()
            } catch (e: Exception) {
                Timber.e(e, "Failed to configure/start codec")
                extractor.release()
                return@withContext null
            }

            val decodedData = mutableListOf<Float>()
            val info = MediaCodec.BufferInfo()
            var isEOS = false
            var inputDone = false

            try {
                while (!isEOS) {
                    if (!inputDone) {
                        val inputIndex = codec.dequeueInputBuffer(10000)
                        if (inputIndex >= 0) {
                            val buffer = codec.getInputBuffer(inputIndex)
                            if (buffer != null) {
                                val sampleSize = extractor.readSampleData(buffer, 0)
                                if (sampleSize < 0) {
                                    codec.queueInputBuffer(
                                        inputIndex,
                                        0,
                                        0,
                                        0,
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                    )
                                    inputDone = true
                                } else {
                                    codec.queueInputBuffer(
                                        inputIndex,
                                        0,
                                        sampleSize,
                                        extractor.sampleTime,
                                        0
                                    )
                                    extractor.advance()
                                }
                            }
                        }
                    }

                    val outputIndex = codec.dequeueOutputBuffer(info, 10000)
                    if (outputIndex >= 0) {
                        val outputBuffer = codec.getOutputBuffer(outputIndex)
                        if (outputBuffer != null && info.size > 0) {
                            outputBuffer.position(info.offset)
                            outputBuffer.limit(info.offset + info.size)

                            // PCM data is typically 16-bit signed short
                            // or floating point if specified.
                            // Most decoders output 16-bit PCM by default unless configured otherwise.
                            // Let's assume 16-bit PCM for broader compatibility and convert to float [-1.0, 1.0]

                            while (outputBuffer.remaining() >= 2) {
                                val sample = outputBuffer.getShort()
                                decodedData.add(sample / 32768.0f)
                            }
                        }
                        codec.releaseOutputBuffer(outputIndex, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            isEOS = true
                        }
                    } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        // formats.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error during decoding")
                return@withContext null
            } finally {
                codec.stop()
                codec.release()
                extractor.release()
            }

            return@withContext Pair(decodedData.toFloatArray(), channels)
        }
}
