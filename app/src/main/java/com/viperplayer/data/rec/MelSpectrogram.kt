package com.viperplayer.data.rec

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.sin

/**
 * Pure-Kotlin reimplementation of the EXACT `laion/larger_clap_music` audio mel front-end
 * (transformers `ClapFeatureExtractor`, rand_trunc / repeatpad, single-mel path).
 *
 * This is a faithful port of the P0 spike's `mel_reference.py` and the contract in `mel_spec.json`,
 * JVM-validated against golden vectors (mel cosine ≥ 0.99, bit-exact off-device).
 *
 * The output log-mel dB, shape (num_frames=1001, n_mels=64), reshaped to (1,1,1001,64), is the
 * `input_features` tensor for the ONNX CLAP audio tower ([ClapAudioEncoder]).
 *
 * Every constant is stated explicitly — no implicit library defaults. All internal math is done in
 * Double (Python/NumPy uses float64 for this front-end) and only cast to Float at the very end,
 * matching the reference which returns float32. A FloatArray accumulation would drift; the mel matmul
 * accumulates in Double.
 *
 * Contract (mel_spec.json):
 *  - SR 48000, mono float (device audio must be resampled to 48kHz + downmixed to mono first — see
 *    [resampleTo48k] / [downmixToMono]; those are the SINGLE canonical resampler/downmix and the
 *    server ingest must match them for vector parity).
 *  - n_fft/frame/win 1024, hop 480, periodic Hann window (hanning(1025)[:-1]), sum-of-window 512.0
 *  - center=True, reflect pad 512 each side
 *  - power=2.0 spectrogram, onesided, 513 freq bins
 *  - slaney mel filterbank (513x64), f_min=50, f_max=14000, n_mels=64, mel_floor=1e-10
 *  - log: 10*log10(max(mel_power, 1e-10)); reference=1.0 (no offset); no post-normalization
 *
 * Deliberately Android-`Context`-free so it can be unit-tested off-device and reused unchanged.
 */
object MelSpectrogram {

    // ---- Contract constants (mel_spec.json) ----
    const val SR = 48000
    const val N_FFT = 1024
    const val FRAME_LENGTH = 1024
    const val HOP_LENGTH = 480
    const val N_MELS = 64
    const val F_MIN = 50.0
    const val F_MAX = 14000.0
    const val NB_MAX_SAMPLES = 480_000        // 10s * 48kHz
    const val NUM_FREQ_BINS = N_FFT / 2 + 1   // 513
    const val NUM_FRAMES = 1001               // for the fixed 480000-sample (10s) contract
    const val MEL_FLOOR = 1e-10
    const val LOG_MIN_VALUE = 1e-10
    const val LOG_REFERENCE = 1.0

    /** Half-width (in output taps) of the windowed-sinc resampling kernel. */
    private const val RESAMPLE_KERNEL_HALF_WIDTH = 16

    // Lazily-built, cached artifacts (window + filterbank are input-independent).
    private val window: DoubleArray by lazy { periodicHann(FRAME_LENGTH) }

    /** Slaney mel filterbank, row-major (NUM_FREQ_BINS x N_MELS) = (513 x 64). */
    private val melFilters: DoubleArray by lazy { slaneyMelFilterbank() }

    // ---------------------------------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------------------------------

    /**
     * Full front-end: repeatpad/crop -> STFT power -> slaney mel -> dB.
     *
     * @param waveform mono PCM at 48kHz (Float). Any length; padded/cropped to 480000. Callers with
     *                 arbitrary-rate and/or multi-channel input must first run [resampleTo48k] and
     *                 [downmixToMono] (or the convenience [computeInputFeaturesFromRaw]).
     * @param cropIdx  crop offset for clips > 480000 samples. The transformers default is a RANDOM
     *                 offset; for a deterministic on-device contract we pin idx=0 (crop the FIRST 10s).
     *                 The golden `long_mixed_14s` used the seeded offset 43567; pass that only to match
     *                 that golden vector.
     * @return log-mel dB in ROW-MAJOR (num_frames * n_mels) = (1001 * 64) FloatArray. Row-major means
     *         element[f * 64 + m] is frame f, mel bin m — matching NumPy C-order (1001,64).
     */
    fun computeLogMel(waveform: FloatArray, cropIdx: Int = 0): FloatArray {
        val padded = repeatPadOrCrop(waveform, NB_MAX_SAMPLES, cropIdx) // DoubleArray, length 480000
        val power = powerSpectrogram(padded)                            // (numFrames * NUM_FREQ_BINS) row-major
        val numFrames = power.size / NUM_FREQ_BINS
        // mel_power = max(MEL_FLOOR, melFilters.T @ power); then dB. Output row-major (numFrames * N_MELS).
        val out = FloatArray(numFrames * N_MELS)
        for (f in 0 until numFrames) {
            val pBase = f * NUM_FREQ_BINS
            for (m in 0 until N_MELS) {
                var acc = 0.0
                // melFilters is (NUM_FREQ_BINS x N_MELS) row-major: melFilters[k*N_MELS + m].
                var k = 0
                var fbIdx = m
                while (k < NUM_FREQ_BINS) {
                    acc += melFilters[fbIdx] * power[pBase + k]
                    fbIdx += N_MELS
                    k++
                }
                val melPower = if (acc < MEL_FLOOR) MEL_FLOOR else acc
                // power_to_db with reference=1.0 -> 10*log10(max(melPower, LOG_MIN_VALUE))
                val clipped = if (melPower < LOG_MIN_VALUE) LOG_MIN_VALUE else melPower
                out[f * N_MELS + m] = (10.0 * log10(clipped)).toFloat()
            }
        }
        return out
    }

    /**
     * Convenience: returns the ONNX input tensor payload (row-major (1,1,1001,64)) as a FloatArray.
     * The leading (batch=1, channel=1) dims are just a reshape of [computeLogMel] — the flat buffer
     * is identical; this exists purely to name the model-input contract at the call site.
     */
    fun computeInputFeatures(waveform: FloatArray, cropIdx: Int = 0): FloatArray =
        computeLogMel(waveform, cropIdx)

    /**
     * Full raw-audio front-end: interleaved multi-channel PCM at [srcSampleRate] and [channels]
     * channels -> mono -> 48kHz -> log-mel `input_features` (row-major (1,1,1001,64)).
     *
     * This is the single canonical raw-audio entry point for the app: it downmixes to mono FIRST
     * (average the channels), then resamples the mono signal to 48kHz with the canonical windowed-sinc
     * resampler, then runs the mel front-end. The server ingest MUST perform the identical
     * downmix-then-resample-to-48k for embedding vector parity.
     *
     * @param interleaved PCM as interleaved float samples: frame-major, [ch0, ch1, ..., ch0, ch1, ...].
     */
    fun computeInputFeaturesFromRaw(
        interleaved: FloatArray,
        srcSampleRate: Int,
        channels: Int,
        cropIdx: Int = 0
    ): FloatArray {
        val mono = downmixToMono(interleaved, channels)
        val mono48k = resampleTo48k(mono, srcSampleRate)
        return computeInputFeatures(mono48k, cropIdx)
    }

    // ---------------------------------------------------------------------------------------------
    // Channel downmix + resampling (P0-review additions — device audio is arbitrary rate/channels)
    // ---------------------------------------------------------------------------------------------

    /**
     * Downmix interleaved multi-channel PCM to mono by AVERAGING the channels (the CLAP contract's
     * "stereo must be downmixed before this stage"). Frame-major interleave: [c0,c1,...,c0,c1,...].
     * `channels <= 1` returns the input unchanged.
     */
    fun downmixToMono(interleaved: FloatArray, channels: Int): FloatArray {
        require(channels >= 1) { "channels must be >= 1, got $channels" }
        if (channels == 1) return interleaved
        val frames = interleaved.size / channels
        val out = FloatArray(frames)
        val inv = 1.0 / channels
        for (i in 0 until frames) {
            var acc = 0.0
            val base = i * channels
            for (c in 0 until channels) acc += interleaved[base + c].toDouble()
            out[i] = (acc * inv).toFloat()
        }
        return out
    }

    /**
     * Resample mono PCM from [srcSampleRate] to 48kHz — the SINGLE canonical on-device resampler.
     *
     * Linear-phase windowed-sinc (Blackman-windowed) polyphase-style interpolation: for each output
     * sample it convolves a band-limited sinc kernel (cutoff = min(src,dst) Nyquist to prevent
     * aliasing on downsampling) over the neighbouring input samples. This is the resampler whose
     * output the mel front-end (and the server ingest) must agree on for vector parity.
     *
     * @return the input unchanged when [srcSampleRate] == 48000. Empty in -> empty out.
     */
    fun resampleTo48k(mono: FloatArray, srcSampleRate: Int): FloatArray {
        require(srcSampleRate > 0) { "srcSampleRate must be > 0, got $srcSampleRate" }
        if (srcSampleRate == SR) return mono
        if (mono.isEmpty()) return FloatArray(0)

        val ratio = SR.toDouble() / srcSampleRate // dst/src; >1 upsample, <1 downsample
        val outLen = floor(mono.size * ratio).toInt()
        if (outLen <= 0) return FloatArray(0)
        val out = FloatArray(outLen)

        // Anti-alias cutoff (fraction of the SOURCE Nyquist). On downsampling, lower the cutoff to the
        // destination Nyquist so we do not fold energy above 24kHz back into the band.
        val cutoff = if (ratio < 1.0) ratio else 1.0
        // Kernel spans +-RESAMPLE_KERNEL_HALF_WIDTH output taps, widened by 1/cutoff when downsampling.
        val filterHalf = RESAMPLE_KERNEL_HALF_WIDTH / cutoff

        val n = mono.size
        for (i in 0 until outLen) {
            val srcPos = i / ratio // position in source-sample space
            val left = floor(srcPos - filterHalf).toInt()
            val right = floor(srcPos + filterHalf).toInt()
            var acc = 0.0
            var norm = 0.0
            var j = left
            while (j <= right) {
                if (j in 0 until n) {
                    val x = (srcPos - j) * cutoff
                    val w = sincWindowed(x, srcPos - j, filterHalf)
                    acc += mono[j].toDouble() * w
                    norm += w
                }
                j++
            }
            out[i] = if (norm != 0.0) (acc / norm).toFloat() else 0.0f
        }
        return out
    }

    /**
     * Band-limited sinc weight, Blackman-windowed over [-filterHalf, +filterHalf] input-sample offsets.
     * @param sincArg  cutoff-scaled distance (feeds sinc(pi * sincArg)).
     * @param offset   raw input-sample offset from the interpolation position (feeds the window).
     */
    private fun sincWindowed(sincArg: Double, offset: Double, filterHalf: Double): Double {
        // Blackman window over the raw offset in [-filterHalf, filterHalf].
        val t = (offset + filterHalf) / (2.0 * filterHalf) // 0..1
        if (t < 0.0 || t > 1.0) return 0.0
        val window = 0.42 - 0.5 * cos(2.0 * PI * t) + 0.08 * cos(4.0 * PI * t)
        return sinc(sincArg) * window
    }

    /** Normalized sinc: sin(pi x) / (pi x), with sinc(0) = 1. */
    private fun sinc(x: Double): Double {
        if (x == 0.0) return 1.0
        val px = PI * x
        return sin(px) / px
    }

    // ---------------------------------------------------------------------------------------------
    // Padding / truncation (rand_trunc / repeatpad)
    // ---------------------------------------------------------------------------------------------

    /**
     * ClapFeatureExtractor._get_input_mel, truncation='rand_trunc', padding='repeatpad':
     *  - len > max: crop `max` samples starting at cropIdx (pinned to 0 for a deterministic contract).
     *  - len < max: tile floor(max/len) times, then right zero-pad to `max`.
     *  - len == max: unchanged.
     * Promotes to Double (the processor promotes the waveform to float64 before the STFT).
     */
    internal fun repeatPadOrCrop(waveform: FloatArray, maxLength: Int, cropIdx: Int): DoubleArray {
        val n = waveform.size
        val out = DoubleArray(maxLength)
        when {
            n > maxLength -> {
                val start = cropIdx.coerceIn(0, n - maxLength)
                for (i in 0 until maxLength) out[i] = waveform[start + i].toDouble()
            }
            n < maxLength -> {
                if (n == 0) return out // all zeros; degenerate empty input
                val nRepeat = maxLength / n // floor
                var w = 0
                for (r in 0 until nRepeat) {
                    for (i in 0 until n) { out[w++] = waveform[i].toDouble() }
                }
                // remaining stays 0.0 (right zero-pad)
            }
            else -> {
                for (i in 0 until maxLength) out[i] = waveform[i].toDouble()
            }
        }
        return out
    }

    // ---------------------------------------------------------------------------------------------
    // STFT power spectrogram
    // ---------------------------------------------------------------------------------------------

    /**
     * transformers.audio_utils.spectrogram with center=True, pad_mode='reflect', power=2.0,
     * onesided=True. Returns row-major (numFrames * NUM_FREQ_BINS) power spectrogram.
     *
     * NumPy computes rfft over a real frame; here we run a real-input radix-2 FFT (N=1024 is a power
     * of two) and take the one-sided magnitude^2.
     */
    internal fun powerSpectrogram(waveform: DoubleArray): DoubleArray {
        val pad = FRAME_LENGTH / 2 // 512
        val padded = reflectPad(waveform, pad)
        val numFrames = (1 + floor((padded.size - FRAME_LENGTH).toDouble() / HOP_LENGTH)).toInt()
        val out = DoubleArray(numFrames * NUM_FREQ_BINS)

        val re = DoubleArray(N_FFT)
        val im = DoubleArray(N_FFT)
        val fft = Fft(N_FFT)

        var t = 0
        for (frame in 0 until numFrames) {
            // windowed frame into re[0..FRAME_LENGTH); zero for the rest (FRAME_LENGTH == N_FFT here).
            for (i in 0 until FRAME_LENGTH) re[i] = padded[t + i] * window[i]
            for (i in 0 until N_FFT) im[i] = 0.0
            fft.transform(re, im)
            val base = frame * NUM_FREQ_BINS
            for (k in 0 until NUM_FREQ_BINS) {
                val r = re[k]
                val ii = im[k]
                out[base + k] = r * r + ii * ii // |X|^2 (power = 2.0)
            }
            t += HOP_LENGTH
        }
        return out
    }

    /**
     * NumPy `np.pad(x, (pad,pad), mode='reflect')` — mirror WITHOUT repeating the edge sample.
     * Requires pad < len(x). For our contract len=480000, pad=512, so always satisfied.
     */
    internal fun reflectPad(x: DoubleArray, pad: Int): DoubleArray {
        val n = x.size
        val out = DoubleArray(n + 2 * pad)
        for (i in 0 until n) out[pad + i] = x[i]
        // left: out[pad-1-j] = x[j+1]  (reflect, edge not repeated)
        for (j in 0 until pad) out[pad - 1 - j] = x[j + 1]
        // right: out[pad+n+j] = x[n-2-j]
        for (j in 0 until pad) out[pad + n + j] = x[n - 2 - j]
        return out
    }

    // ---------------------------------------------------------------------------------------------
    // Window
    // ---------------------------------------------------------------------------------------------

    /** periodic (DFT-even) Hann: np.hanning(N+1)[:-1]. np.hanning(M) = 0.5 - 0.5*cos(2*pi*n/(M-1)). */
    internal fun periodicHann(winLength: Int): DoubleArray {
        val m = winLength + 1 // np.hanning(winLength+1)
        val w = DoubleArray(winLength)
        for (n in 0 until winLength) {
            w[n] = 0.5 - 0.5 * cos(2.0 * PI * n / (m - 1))
        }
        return w
    }

    // ---------------------------------------------------------------------------------------------
    // Slaney mel filterbank
    // ---------------------------------------------------------------------------------------------

    private fun hertzToMelSlaney(freq: Double): Double {
        val minLogHertz = 1000.0
        val minLogMel = 15.0
        val logstep = 27.0 / ln(6.4)
        return if (freq >= minLogHertz) minLogMel + ln(freq / minLogHertz) * logstep
        else 3.0 * freq / 200.0
    }

    private fun melToHertzSlaney(mel: Double): Double {
        val minLogHertz = 1000.0
        val minLogMel = 15.0
        val logstep = ln(6.4) / 27.0
        return if (mel >= minLogMel) minLogHertz * exp(logstep * (mel - minLogMel))
        else 200.0 * mel / 3.0
    }

    /**
     * Slaney-scale, slaney-norm mel filterbank, row-major (NUM_FREQ_BINS x N_MELS) = (513 x 64).
     * Mirrors transformers.audio_utils.mel_filter_bank / _create_triangular_filter_bank.
     */
    internal fun slaneyMelFilterbank(
        numFreqBins: Int = NUM_FREQ_BINS,
        nMels: Int = N_MELS,
        fMin: Double = F_MIN,
        fMax: Double = F_MAX,
        sr: Int = SR
    ): DoubleArray {
        val melMin = hertzToMelSlaney(fMin)
        val melMax = hertzToMelSlaney(fMax)
        // filter_freqs = mel_to_hertz(linspace(melMin, melMax, nMels+2))
        val filterFreqs = DoubleArray(nMels + 2)
        for (i in 0 until nMels + 2) {
            val mel = melMin + (melMax - melMin) * i / (nMels + 2 - 1)
            filterFreqs[i] = melToHertzSlaney(mel)
        }
        // fft_freqs = linspace(0, sr//2, numFreqBins) = linspace(0, 24000, 513)
        val fftFreqs = DoubleArray(numFreqBins)
        val fMaxLin = (sr / 2).toDouble()
        for (i in 0 until numFreqBins) fftFreqs[i] = fMaxLin * i / (numFreqBins - 1)

        // filter_diff = diff(filter_freqs)
        val filterDiff = DoubleArray(nMels + 1)
        for (i in 0 until nMels + 1) filterDiff[i] = filterFreqs[i + 1] - filterFreqs[i]

        // triangular filters + slaney area norm.
        // filters[k, m] = max(0, min(down, up)); down = -(filterFreqs[m]-fftFreqs[k])/filterDiff[m];
        //                                        up  =  (filterFreqs[m+2]-fftFreqs[k])/filterDiff[m+1]
        // enorm[m] = 2/(filterFreqs[m+2]-filterFreqs[m]); filters[k,m] *= enorm[m]
        val fb = DoubleArray(numFreqBins * nMels)
        for (k in 0 until numFreqBins) {
            val fk = fftFreqs[k]
            for (m in 0 until nMels) {
                val down = -(filterFreqs[m] - fk) / filterDiff[m]
                val up = (filterFreqs[m + 2] - fk) / filterDiff[m + 1]
                var v = min(down, up)
                if (v < 0.0) v = 0.0
                val enorm = 2.0 / (filterFreqs[m + 2] - filterFreqs[m])
                fb[k * nMels + m] = v * enorm
            }
        }
        return fb
    }

    /** Exposed for tests: sum of the periodic-Hann window (contract says 512.0). */
    internal fun windowSum(): Double = periodicHann(FRAME_LENGTH).sum()

    // ---------------------------------------------------------------------------------------------
    // Radix-2 Cooley-Tukey FFT (N power of two). In-place, decimation-in-time, iterative.
    // Matches np.fft.rfft for the one-sided bins (0..N/2) we read out.
    // ---------------------------------------------------------------------------------------------
    internal class Fft(private val n: Int) {
        private val cosT: DoubleArray
        private val sinT: DoubleArray
        private val bitrev: IntArray

        init {
            require(n > 0 && (n and (n - 1)) == 0) { "FFT size must be a power of two, got $n" }
            val half = n / 2
            cosT = DoubleArray(half)
            sinT = DoubleArray(half)
            for (i in 0 until half) {
                val ang = -2.0 * PI * i / n // forward transform (numpy convention)
                cosT[i] = cos(ang)
                sinT[i] = sin(ang)
            }
            bitrev = IntArray(n)
            var bits = 0
            var t = n
            while (t > 1) { t = t shr 1; bits++ }
            for (i in 0 until n) {
                var x = i
                var r = 0
                for (b in 0 until bits) { r = (r shl 1) or (x and 1); x = x shr 1 }
                bitrev[i] = r
            }
        }

        /** In-place forward FFT on (re, im), each length n. */
        fun transform(re: DoubleArray, im: DoubleArray) {
            // bit-reversal permutation
            for (i in 0 until n) {
                val j = bitrev[i]
                if (j > i) {
                    var tmp = re[i]; re[i] = re[j]; re[j] = tmp
                    tmp = im[i]; im[i] = im[j]; im[j] = tmp
                }
            }
            var size = 2
            while (size <= n) {
                val half = size / 2
                val step = n / size
                var i = 0
                while (i < n) {
                    var k = 0
                    var tw = 0
                    while (k < half) {
                        val wr = cosT[tw]
                        val wi = sinT[tw]
                        val a = i + k
                        val b = i + k + half
                        val tr = re[b] * wr - im[b] * wi
                        val ti = re[b] * wi + im[b] * wr
                        re[b] = re[a] - tr
                        im[b] = im[a] - ti
                        re[a] += tr
                        im[a] += ti
                        k++
                        tw += step
                    }
                    i += size
                }
                size = size shl 1
            }
        }
    }
}
