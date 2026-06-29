#include "PlaybackGain.h"
#include <cmath>

namespace viper {
namespace effects {

// --- Constants recovered from libv4a_fx_jb_NEON.so (PlaybackGain @ 0x57a50..) ---

// Detection band-pass: f0 = 2200 Hz, Q = 0.33 (ctor/Reset @ 0x57c28-0x57c34).
// 0x45098000 = 2200.0f ; 0x3ea8f5c3 = 0.33f
static constexpr float kBandPassFreq = 2200.0f;
static constexpr float kBandPassQ = 0.33f;

// Level -> dB mapping (Process @ 0x57d44-0x57d74):
//   levelDb = 23 + 10 * (kLog10Scale * ln(energy + eps))
// this+0x4 = 0x3ede5bd8 = 0.434258878f (ctor @ 0x57a88/0x57a90)
static constexpr float kLog10Scale = 0.434258878f;
static constexpr float kLevelOffsetDb = 23.0f;   // vmov.f32 s15, #23.0 @ 0x57d60
static constexpr float kTenScale = 10.0f;        // vmov.f32 s13, #10.0 @ 0x57d58
// 0x57ee8 = 0x2edbe6ff = 1e-10f (energy floor before ln, @ 0x57d40)
static constexpr float kEnergyEpsilon = 1.0e-10f;

// Startup ramp / soft-knee gain shaping (Process @ 0x57d78-0x57db8):
//   a       = (counter / 100) * levelDb * (gainSlope - 1)
//   gainDb  = a - 50 * (a / 100)^2          (= a - a^2 / 200)
//   gain    = 10^(gainDb / 20)
static constexpr int   kCounterMax = 100;        // cmp r3, #99 @ 0x57d78
static constexpr float kCounterMaxF = 100.0f;    // 0x57ef0 = 100.0f
static constexpr float kKneeScale = 50.0f;       // 0x57eec = 50.0f
static constexpr float kKneeDivisor = 100.0f;    // 0x57ef0 = 100.0f
static constexpr float kDbDivisor = 20.0f;       // vmov.f32 s13, #20.0 @ 0x57d84
static constexpr float kGainBase = 10.0f;        // r0 = 0x41200000 = 10.0f @ 0x57d54-0x57d5c

// Gain smoothing (Process @ 0x57dd4-0x57e48):
//   rampLen = max(samplingRate / 40, blockSize)   (~25 ms)
//   step    = (target - gain) / rampLen
//   if (step > 0) step /= 16                       (slow attack, fast release)
static constexpr uint32_t kRampDivisor = 40;     // SR / 0x28 @ 0x57dec-0x57df0
static constexpr float kAttackDivisor = 16.0f;   // asrgt r0, r0, #4 @ 0x57e48

PlaybackGain::PlaybackGain(uint32_t samplingRate)
    : gainSlope(0.5f),        // 1 / (1 + ratio), ratio default 1.0  (0x3f000000)
      counter(0),
      volume(1.0f),           // 0x2000000 Q25 == 1.0
      maxGain(1.0f),          // 0x2000000 Q25 == 1.0
      gainL(1.0f), gainR(1.0f),
      samplingRate(samplingRate),
      enabled(false) {
    Reset();
}

PlaybackGain::~PlaybackGain() {
}

void PlaybackGain::SetSamplingRate(uint32_t samplingRate) {
    if (this->samplingRate == samplingRate) {
        return;
    }
    this->samplingRate = samplingRate;
    Reset();
}

void PlaybackGain::Reset() {
    // SetBandPassParameter re-derives coefficients and clears filter state
    // (matches the binary, which does not call Biquad::Reset here).
    filterL.SetBandPassParameter(kBandPassFreq, samplingRate, kBandPassQ);
    filterR.SetBandPassParameter(kBandPassFreq, samplingRate, kBandPassQ);

    // Binary resets only the counter and the running per-channel gains
    // (volume / maxGain / gainSlope are preserved).
    counter = 0;
    gainL = 1.0f;
    gainR = 1.0f;
}

void PlaybackGain::SetEnable(bool enable) {
    if (enabled == enable) {
        return;
    }
    // Reset only on the disabled -> enabled transition.
    if (!enabled && enable) {
        Reset();
    }
    enabled = enable;
}

void PlaybackGain::SetRatio(float ratio) {
    // Binary: this+0x10 = ratio + 1 (write-only); this+0x0 = 1 / (ratio + 1).
    gainSlope = 1.0f / (ratio + 1.0f);
}

void PlaybackGain::SetVolume(float volume) {
    // Binary stores (int)(volume * 2^25 + 0.5) (Q25); float port keeps it linear.
    this->volume = volume;
}

void PlaybackGain::SetMaxGainFactor(float maxGainFactor) {
    // Binary stores (int)(maxGainFactor * 2^25 + 0.5) (Q25); float port keeps it linear.
    maxGain = maxGainFactor;
}

// Band-pass filters both channels, accumulates squared energy, and returns the
// louder channel's mean-square. (Binary returns max(sumL, sumR) / size; on the
// Q25 int pipeline it then scaled by 2^-50 to undo (Q25)^2 -- with real-valued
// float samples that factor is unnecessary.)
double PlaybackGain::AnalyseWave(const float *buffer, uint32_t size) {
    if (size == 0) {
        return 0.0;
    }

    double sumL = 0.0;
    double sumR = 0.0;
    for (uint32_t i = 0; i < size; i++) {
        double outL = filterL.ProcessSample((double) buffer[i * 2]);
        double outR = filterR.ProcessSample((double) buffer[i * 2 + 1]);
        sumL += outL * outL;
        sumR += outR * outR;
    }

    double maxEnergy = (sumL > sumR) ? sumL : sumR;
    return maxEnergy / (double) size;
}

void PlaybackGain::Process(float *buffer, uint32_t size) {
    if (!enabled || size == 0) {
        return;
    }

    // 1) Estimate level and map it to a target gain.
    double energy = AnalyseWave(buffer, size);
    float levelDb = kLevelOffsetDb +
                    (kLog10Scale * logf((float) energy + kEnergyEpsilon)) * kTenScale;

    if (counter < kCounterMax) {
        counter++;
    }
    float ramp = (float) counter / kCounterMaxF;

    // Ratio-weighted, ramp-scaled level, then soft-knee shaping into dB.
    float a = ramp * levelDb * (gainSlope - 1.0f);
    float gainDb = a - kKneeScale * (a / kKneeDivisor) * (a / kKneeDivisor);
    float gainFactor = powf(kGainBase, gainDb / kDbDivisor);

    float target = gainFactor * volume;

    // 2) Glide the running gain toward the target over ~25 ms, then apply it.
    //    Attack (gain rising) is 16x slower than release; gain is clamped to
    //    [-maxGain, +maxGain]. L and R glide independently.
    uint32_t rampLen = samplingRate / kRampDivisor;
    if (rampLen < size) {
        rampLen = size;
    }

    float *gains[2] = {&gainL, &gainR};
    for (int ch = 0; ch < 2; ch++) {
        float gain = *gains[ch];

        float step = (target - gain) / (float) rampLen;
        if (step > 0.0f) {
            step /= kAttackDivisor;
        }

        for (uint32_t i = 0; i < size; i++) {
            buffer[i * 2 + ch] *= gain;

            float next = gain + step;
            if (next > maxGain) {
                gain = maxGain;
            } else if (next >= -maxGain) {
                gain = next;
            } else {
                gain = -maxGain;
            }
        }

        *gains[ch] = gain;
    }
}

} // namespace effects
} // namespace viper
