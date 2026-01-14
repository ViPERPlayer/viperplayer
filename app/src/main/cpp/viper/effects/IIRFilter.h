#pragma once

#include <cstdint>
#include "../utils/MinPhaseIIRCoeffs.h"
#include <array>

// Iscle: Verified with the latest version at 13/12/2022

class IIRFilter {
public:
    IIRFilter(uint32_t samplingRate, uint32_t bands);

    void Process(float *samples, uint32_t size);
    void Reset();
    void SetBandLevel(uint32_t band, float level);
    void SetEnable(bool enable);
    void SetSamplingRate(uint32_t samplingRate);

private:
    uint32_t bands;
    uint32_t samplingRate;
    bool enable = false;
    MinPhaseIIRCoeffs minPhaseIirCoeffs;
    double state[496];
    uint32_t writeIndex;
    uint32_t delay1Index;
    uint32_t delay2Index;
    std::array<float, 31> bandGainLinear;
};


