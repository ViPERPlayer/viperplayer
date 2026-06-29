#include "FIREqualizer.h"
#include <cstring>

// Q25 fixed-point scale. 1.0 is represented as 2^25.
// 0x0006573c = 33554432.00 (== 2^25)
static constexpr double kQ25Scale = 33554432.0;

// Rounding constant added before the >> 25 in every fixed-point multiply.
// 0x1000000 == 2^24 == half of 2^25, i.e. round-to-nearest for the Q25 shift.
static constexpr int64_t kRoundQ25 = 0x1000000;

// Q25 multiply with round-to-nearest: (int32)((a * b + 2^24) >> 25).
static inline int32_t mulQ25(int64_t a, int64_t b) {
    return static_cast<int32_t>((a * b + kRoundQ25) >> 25);
}

// ---------------------------------------------------------------------------
// Tap-window offsets into the 256-deep history rings, one entry per dyadic
// moving-average stage.
//
// In the original binary these are two adjacent 9-int read-only tables:
//   add-window table at vaddr 0x65910 (relocated base 0x68c40 -> 0xce550)
//   sub-window table at vaddr 0x65934 (= add table + 9 ints)
// Process() reads them as: index = (ringPos + table[j]) & 0xff.
//
// UNRECOVERED: the constants dump provided is section-relative (addresses
// restart at 0 per section), so the 9+9 integer values at 0xce550 could not be
// byte-extracted. They must be recovered from the binary's .rodata. The dyadic
// structure of the algorithm (stage j has shift j+1, i.e. box width 2^(j+1))
// strongly implies the entering/leaving taps are symmetric +/- 2^j around the
// ring center, but this is NOT confirmed, so the values are left as an explicit
// non-functional placeholder rather than guessed.
// ---------------------------------------------------------------------------
static const int32_t kWindowAddOffsets[9] = {0, 0, 0, 0, 0, 0, 0, 0, 0};
static const int32_t kWindowSubOffsets[9] = {0, 0, 0, 0, 0, 0, 0, 0, 0};

FIREqualizer::FIREqualizer() {
    // Original: SetBandLevel(this, i, 0.0f) for i = 0..9, which writes the
    // neutral Q25 gain (== 2^25) into every band slot.
    for (uint32_t band = 0; band < 10; band++) {
        SetBandLevel(band, 0.0f);
    }
    Reset();
    mSamplingRate = 0;
    SetSamplingRate(0xac44); // 44100 Hz
    mEnabled = false;
}

FIREqualizer::~FIREqualizer() = default;

void FIREqualizer::SetBandLevel(uint32_t band, float level) {
    if (band > 9) {
        return;
    }
    // Positive gains are doubled before encoding (asymmetric boost/cut curve).
    if (level > 0.0f) {
        level = level + level;
    }
    // Encode as Q25 around unity: (level + 1.0) * 2^25, rounded.
    uint32_t encoded = static_cast<uint32_t>((static_cast<double>(level) + 1.0) * kQ25Scale + 0.5);
    // Clamp negatives to 0: q & ~(arithmetic_shift_right(q, 31)).
    encoded &= ~(static_cast<int32_t>(encoded) >> 31);
    mBandGains[9 - band] = static_cast<int32_t>(encoded);
}

void FIREqualizer::SetSamplingRate(uint32_t samplingRate) {
    if (mSamplingRate == samplingRate) {
        return;
    }
    mSamplingRate = samplingRate;
    Reset();
}

bool FIREqualizer::SetEnable(bool enable) {
    // When transitioning out of the (initial) disabled state, flush state first.
    if (!mEnabled) {
        if (!enable) {
            return false;
        }
        Reset();
    }
    if (enable == mEnabled) {
        return false;
    }
    mEnabled = enable;
    return true;
}

void FIREqualizer::Reset() {
    std::memset(mDelayedL, 0, sizeof(mDelayedL));
    std::memset(mDelayedR, 0, sizeof(mDelayedR));
    std::memset(mInputL, 0, sizeof(mInputL));
    std::memset(mInputR, 0, sizeof(mInputR));
    std::memset(mAccumL, 0, sizeof(mAccumL));
    std::memset(mAccumR, 0, sizeof(mAccumR));
    mRingPos = 0;
}

void FIREqualizer::Process(int32_t *samples, int32_t frameCount) {
    if (samples == nullptr || !mEnabled || frameCount <= 0) {
        return;
    }

    uint32_t ringPos = mRingPos;

    for (int32_t frame = 0; frame < frameCount; frame++) {
        int32_t *left = &samples[frame * 2];
        int32_t *right = &samples[frame * 2 + 1];

        // 1. Read the center-tap (delayed) input leaving the recent ring, then
        //    push the current input sample into the ring.
        int32_t centerL = mInputL[ringPos];
        int32_t centerR = mInputR[ringPos];
        mInputL[ringPos] = *left;
        mInputR[ringPos] = *right;

        // 2. Add the samples entering each scale's window into the running sums.
        for (int j = 0; j < kStageCount; j++) {
            uint32_t idx = (ringPos + kWindowAddOffsets[j]) & 0xff;
            mAccumL[j] += mInputL[idx];
            mAccumR[j] += mInputR[idx];
        }

        // 3. Normalise each running sum into a moving average: avg[j] = sum >> (j+1).
        int32_t avgL[kStageCount];
        int32_t avgR[kStageCount];
        for (int j = 0; j < kStageCount; j++) {
            int shift = j + 1; // always < 32, so the wide-shift branch in the original is dead.
            avgL[j] = static_cast<int32_t>(mAccumL[j] >> shift);
            avgR[j] = static_cast<int32_t>(mAccumR[j] >> shift);
        }

        // 4. Telescope the band contributions, each weighted by its Q25 gain.
        int32_t outL = mulQ25(centerL - avgL[0], mBandGains[0]);
        int32_t outR = mulQ25(mBandGains[0], centerR - avgR[0]);
        for (int j = 1; j < kStageCount; j++) {
            outL += mulQ25(avgL[j - 1] - avgL[j], mBandGains[j]);
            outR += mulQ25(mBandGains[j], avgR[j - 1] - avgR[j]);
        }
        outL += mulQ25(avgL[kStageCount - 1], mBandGains[9]);
        outR += mulQ25(mBandGains[9], avgR[kStageCount - 1]);

        // 5. Subtract the samples leaving each window (from the delayed ring).
        for (int j = 0; j < kStageCount; j++) {
            uint32_t idx = (ringPos + kWindowSubOffsets[j]) & 0xff;
            mAccumL[j] -= mDelayedL[idx];
            mAccumR[j] -= mDelayedR[idx];
        }

        // 6. Write the filtered output.
        *left = outL;
        *right = outR;

        // 7. Age the center-tap input into the delayed ring and advance.
        mDelayedL[ringPos] = centerL;
        mDelayedR[ringPos] = centerR;
        ringPos = (ringPos + 1) & 0xff;
    }

    mRingPos = ringPos;
}
