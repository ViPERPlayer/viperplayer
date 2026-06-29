#include "FIREqualizer.h"
#include <cstring>

// ---------------------------------------------------------------------------
// Tap offsets into the 256-deep history ring, one per dyadic stage (j = 0..8):
// the "add" stage reads the sample entering each window, the "subtract" stage
// the sample leaving it. Recovered from the original's two adjacent 9-int
// .rodata tables (Ghidra 0xce550 add, 0xce574 sub):
//   add[j] = +2^j  -> { 1,  2,  4,  8,  16,  32,  64,  128,  256 }
//   sub[j] = -2^j  -> {-1, -2, -4, -8, -16, -32, -64, -128, -256 }
// (Indexing is (ringPos + offset) & 0xff, so the 256 entries wrap to the center.)
// ---------------------------------------------------------------------------
static const int32_t kWindowAddOffsets[9] = {1, 2, 4, 8, 16, 32, 64, 128, 256};
static const int32_t kWindowSubOffsets[9] = {-1, -2, -4, -8, -16, -32, -64, -128, -256};

FIREqualizer::FIREqualizer() {
    // Original: SetBandLevel(i, 0.0f) for i = 0..9 -> unity (flat) gain in every band.
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
    // Positive gains are doubled before being taken around unity (asymmetric
    // boost/cut curve), matching the original.
    if (level > 0.0f) {
        level = level + level;
    }
    // Linear gain around unity. The original encodes round((level + 1) * 2^25) in
    // Q25 and clamps negatives to 0; here the 2^25 scale cancels with the Q25
    // multiply in Process(), so we store the linear value directly.
    float gain = level + 1.0f;
    if (gain < 0.0f) {
        gain = 0.0f;
    }
    mBandGains[9 - band] = gain;
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

void FIREqualizer::Process(float *samples, int32_t frameCount) {
    if (samples == nullptr || !mEnabled || frameCount <= 0) {
        return;
    }

    uint32_t ringPos = mRingPos;

    for (int32_t frame = 0; frame < frameCount; frame++) {
        float *left = &samples[frame * 2];
        float *right = &samples[frame * 2 + 1];

        // 1. Read the center-tap (delayed) input leaving the recent ring, then
        //    push the current input sample into the ring.
        float centerL = mInputL[ringPos];
        float centerR = mInputR[ringPos];
        mInputL[ringPos] = *left;
        mInputR[ringPos] = *right;

        // 2. Add the samples entering each scale's window into the running sums.
        for (int j = 0; j < kStageCount; j++) {
            uint32_t idx = (ringPos + kWindowAddOffsets[j]) & 0xff;
            mAccumL[j] += mInputL[idx];
            mAccumR[j] += mInputR[idx];
        }

        // 3. Normalise each running sum into a moving average: avg[j] = sum / 2^(j+1).
        float avgL[kStageCount];
        float avgR[kStageCount];
        for (int j = 0; j < kStageCount; j++) {
            float scale = 1.0f / static_cast<float>(1u << (j + 1)); // 1/2 .. 1/512
            avgL[j] = static_cast<float>(mAccumL[j]) * scale;
            avgR[j] = static_cast<float>(mAccumR[j]) * scale;
        }

        // 4. Telescope the band contributions, each weighted by its band gain.
        float outL = (centerL - avgL[0]) * mBandGains[0];
        float outR = (centerR - avgR[0]) * mBandGains[0];
        for (int j = 1; j < kStageCount; j++) {
            outL += (avgL[j - 1] - avgL[j]) * mBandGains[j];
            outR += (avgR[j - 1] - avgR[j]) * mBandGains[j];
        }
        outL += avgL[kStageCount - 1] * mBandGains[9];
        outR += avgR[kStageCount - 1] * mBandGains[9];

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
