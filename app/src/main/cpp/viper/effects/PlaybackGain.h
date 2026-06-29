#pragma once

#include "../utils/Biquad.h"
#include <cstdint>

namespace viper {
namespace effects {

// Reconstruction of ViPER4Android v2505 "PlaybackGain" (auto gain control).
//
// The original operates on Q25 fixed-point samples (1.0 == 2^25). This port
// runs on a real-valued float pipeline ([-1, 1]), so all of the fixed-point
// scaling artifacts collapse:
//   - the energy was multiplied by 2^-50 to undo (Q25)^2; with float samples
//     the analysed energy is already real-valued mean-square, so the factor is
//     dropped (see PlaybackGain.cpp).
//   - volume / max-gain / running gain were stored as Q25 ints; here they are
//     plain float multipliers (1.0 == unity).
//
// Public method names/signatures must stay stable (called from viperplayer.cpp
// and they mirror the demangled binary symbols).
class PlaybackGain {
public:
    explicit PlaybackGain(uint32_t samplingRate);
    ~PlaybackGain();

    void SetSamplingRate(uint32_t samplingRate);
    void Reset();

    void Process(float *buffer, uint32_t size);

    void SetEnable(bool enable);
    void SetRatio(float ratio);
    void SetVolume(float volume);
    void SetMaxGainFactor(float maxGainFactor);

private:
    // Detection-only band-pass filters (do not affect the output audio); their
    // squared output drives the level estimate. One per channel.
    double AnalyseWave(const float *buffer, uint32_t size);

    Biquad filterL, filterR;          // this+0x28, this+0x4c (FixedBiquad in binary)

    // this+0x0: gain-curve slope, = 1 / (1 + ratio); default 0.5 (ratio = 1).
    float gainSlope;
    // this+0x10 held (1 + ratio) but is never read back by the binary, so it is
    // not retained here (dead state).
    int counter;                      // this+0x14: startup ramp counter, clamps at 100
    float volume;                     // this+0x18: output makeup level (Q25 int in binary)
    float maxGain;                    // this+0x1c: |gain| limit (Q25 int in binary)
    float gainL, gainR;               // this+0x20, this+0x24: running per-channel gain
    uint32_t samplingRate;            // this+0x70
    bool enabled;                     // this+0x74
};

} // namespace effects
} // namespace viper
