#pragma once

#include <cstdint>
#include "../utils/MultiBiquad.h"
#include "../utils/Biquad.h"
#include <array>

// Verified against ViPER4Android v2505. Original default sampling rate is
// 44100 (0xac44) set in the no-arg constructor; here it is supplied by the
// caller, matching the convention used by the other effect units.

class SpeakerCorrection {
public:
    SpeakerCorrection(uint32_t samplingRate);

    void Process(float *samples, uint32_t size);
    void Reset();
    void SetEnable(bool enable);
    void SetSamplingRate(uint32_t samplingRate);

private:
    uint32_t samplingRate;
    bool enable = false;
    std::array<MultiBiquad, 2> highPass;
    std::array<Biquad, 2> lowPass;
    std::array<Biquad, 2> bandPass;
};
