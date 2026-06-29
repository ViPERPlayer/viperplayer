#pragma once

#include <cstdint>

/**
 * FIREqualizer
 *
 * Faithful FLOAT reconstruction of ViPER4Android v2505 `FIREqualizer`
 * (mangled: _ZN12FIREqualizer...).
 *
 * A 10-band linear-phase equalizer that operates on interleaved stereo float
 * samples (NOT FFT / frequency sampling). For each of 9 dyadic scales it keeps an
 * incrementally-updated running sum (box filter / moving average) over a 256-deep
 * delay line. The 10 band outputs telescope the differences between adjacent-scale
 * moving averages, each weighted by its band gain:
 *
 *   y = g[0]*(x - avg[0])
 *     + sum_{j=1..8} g[j]*(avg[j-1] - avg[j])
 *     + g[9]*avg[8]
 *
 * where avg[j] = runningSum[j] / 2^(j+1) and x is the center-tap (delayed) input.
 *
 * The original binary is Q25 fixed point; this is a mathematically-equivalent
 * float port (the Q25 2^25 scale cancels out, so gains are stored linear and the
 * multiplies/averages are plain float/double). Public method names/signatures
 * match the original. Currently unused — the active EQ is viper::dsp::IIREqualizer.
 */
class FIREqualizer {
public:
    FIREqualizer();
    ~FIREqualizer();

    // band: 0..9. level is a gain around unity (0 = flat); positive values are doubled.
    void SetBandLevel(uint32_t band, float level);
    void SetSamplingRate(uint32_t samplingRate);
    // Returns true if the enabled state actually changed.
    bool SetEnable(bool enable);
    void Reset();

    // samples: interleaved stereo float. frameCount = number of stereo frames.
    void Process(float *samples, int32_t frameCount);

private:
    static constexpr int kHistorySize = 256;   // delay-line ring depth (indexed & 0xff)
    static constexpr int kStageCount = 9;       // dyadic moving-average scales -> 10 bands

    // Per-band linear gains around unity (1.0 == flat). Stored reversed: band b -> mBandGains[9 - b].
    float mBandGains[10];

    // Delay line of samples already pushed out of the recent ring; the "subtract"
    // stage reads it to remove samples leaving each window.
    float mDelayedL[kHistorySize];
    float mDelayedR[kHistorySize];

    // Recent input ring; the "add" stage reads it to add samples entering each window.
    float mInputL[kHistorySize];
    float mInputR[kHistorySize];

    // Running sums per dyadic scale (double for accumulation precision).
    double mAccumL[kStageCount];
    double mAccumR[kStageCount];

    uint32_t mRingPos;
    uint32_t mSamplingRate;
    bool mEnabled;
};
