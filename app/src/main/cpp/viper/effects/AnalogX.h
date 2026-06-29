#pragma once

#include <cstdint>
#include "../utils/Harmonic.h"
#include "../utils/MultiBiquad.h"
#include <array>

class AnalogX {
public:
    AnalogX(uint32_t samplingRate);

    void Process(float *samples, uint32_t size);
    void Reset();
    void SetEnable(bool enable);
    void SetProcessingModel(int processingModel);
    void SetSamplingRate(uint32_t samplingRate);

private:
    std::array<MultiBiquad, 2> highPass;
    std::array<Harmonic, 2> harmonic;
    std::array<MultiBiquad, 2> lowPass;
    std::array<MultiBiquad, 2> peak;

    float gain = 0.0;
    // Counts processed samples while the output is muted after a reset; the
    // mute lasts for the first samplingRate/4 samples (0.25 s).
    uint32_t warmupCounter = 0;
    int processingModel = 0;
    uint32_t samplingRate;
    bool enable = false;
};


