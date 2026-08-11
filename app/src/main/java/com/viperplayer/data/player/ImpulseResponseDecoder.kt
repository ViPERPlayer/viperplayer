package com.viperplayer.data.player

import com.viperplayer.data.rec.DecodedAudio
import com.viperplayer.data.rec.MediaCodecAudioDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decodes a convolution kernel (impulse response) file to interleaved float PCM for
 * [ViperNativeDriver.setConvolverImpulseResponse].
 *
 * Thin wrapper over [MediaCodecAudioDecoder] — the single, unit-tested MediaCodec→PCM path in the
 * app (see `MelSpectrogramParityTest` / `ResamplerTest` and the `clap_golden` fixtures). It replaces
 * an earlier bespoke decoder that read samples with the `ByteBuffer` default byte order
 * (`BIG_ENDIAN`) while `MediaCodec` emits little-endian, so every kernel it produced was
 * byte-transposed — i.e. broadband noise rather than the intended response.
 *
 * Differences from the recommender's use of the same decoder:
 *  * the budget is [MediaCodecAudioDecoder.UNBOUNDED_SECONDS] — a kernel must be convolved in full,
 *    where CLAP only ever looks at the first ~11 seconds of a track;
 *  * decoding runs on [Dispatchers.IO] because the caller is the DSP state-application coroutine.
 *
 * Note the decoder does NOT resample: [DecodedAudio.sampleRate] is the kernel file's own rate, which
 * may differ from the playback stream's. Matching those rates is the native convolver's concern.
 */
@Singleton
class ImpulseResponseDecoder @Inject constructor() {

    private val decoder = MediaCodecAudioDecoder(MediaCodecAudioDecoder.UNBOUNDED_SECONDS)

    /**
     * Decode the kernel at [filePath] in full, or null when it cannot be opened / has no audio track
     * / the codec fails. Callers keep the previously loaded kernel on null.
     */
    suspend fun decode(filePath: String): DecodedAudio? = withContext(Dispatchers.IO) {
        decoder.decode(filePath)
    }
}
