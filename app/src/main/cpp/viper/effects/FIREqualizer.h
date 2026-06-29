#pragma once

#include <cstdint>

/**
 * FIREqualizer
 *
 * Faithful reconstruction of ViPER4Android v2505 `FIREqualizer`
 * (mangled: _ZN12FIREqualizer...).
 *
 * This is a fixed-point (Q25), 10-band linear-phase equalizer that operates
 * directly on interleaved stereo Q.xx integer samples. It does NOT use FFT /
 * frequency sampling. Internally it maintains, for each of 9 dyadic scales, an
 * incrementally-updated running sum (box filter / moving average) over a
 * 256-deep delay line. The 10 band outputs are formed by telescoping the
 * differences between adjacent-scale moving averages, each weighted by the
 * band gain:
 *
 *   y = g[0]*(x - avg[0])
 *     + sum_{j=1..8} g[j]*(avg[j-1] - avg[j])
 *     + g[9]*avg[8]
 *
 * where avg[j] = runningSum[j] >> (j+1) and x is the center-tap (delayed) input.
 *
 * All public method names/signatures match the original binary; nothing else in
 * the codebase currently calls this class.
 */
class FIREqualizer {
public:
    FIREqualizer();
    ~FIREqualizer();

    // band: 0..9. level is a gain in "raw" units (see .cpp for the Q25 encoding).
    void SetBandLevel(uint32_t band, float level);
    void SetSamplingRate(uint32_t samplingRate);
    // Returns true if the enabled state actually changed.
    bool SetEnable(bool enable);
    void Reset();

    // samples: interleaved stereo, fixed-point. frameCount = number of stereo frames.
    void Process(int32_t *samples, int32_t frameCount);

private:
    static constexpr int kHistorySize = 256;   // delay-line ring depth (indexed & 0xff)
    static constexpr int kStageCount = 9;       // dyadic moving-average scales -> 10 bands

    // Per-band gains in Q25 (1.0 == 2^25). Stored reversed: band b -> mBandGains[9 - b].
    int32_t mBandGains[10];

    // Delay line holding the input samples already pushed out of the recent ring;
    // the "subtract" stage reads from here to remove samples leaving each window.
    int32_t mDelayedL[kHistorySize];
    int32_t mDelayedR[kHistorySize];

    // Recent input ring; the "add" stage reads from here to add samples entering
    // each window.
    int32_t mInputL[kHistorySize];
    int32_t mInputR[kHistorySize];

    // 64-bit running sums for each dyadic scale.
    int64_t mAccumL[kStageCount];
    int64_t mAccumR[kStageCount];

    uint32_t mRingPos;
    uint32_t mSamplingRate;
    bool mEnabled;
};
