#pragma once

#include <cstdint>
#include "../utils/NoiseSharpening.h"
#include "../utils/HiFi.h"
#include "../utils/HighShelf.h"

// Verified against ViPER4Android v2505 (ARM NEON decompilation).

class ViPERClarity {
public:
    enum class ClarityMode : uint8_t {
        NATURAL = 0,
        OZONE,
        XHIFI
    };

    ViPERClarity(uint32_t samplingRate);

    void Process(float *samples, uint32_t size);
    void Reset();
    void SetClarity(float gainPercent);
    void SetClarityToFilter();
    void SetEnable(bool enable);
    void SetProcessMode(ClarityMode processMode);
    void SetSamplingRate(uint32_t samplingRate);

private:
    NoiseSharpening noiseSharpening;
    HighShelf highShelf[2];
    HiFi hifi;
    bool enable = false;
    ClarityMode processMode = ClarityMode::NATURAL;
    uint32_t samplingRate;
    float clarityGainPercent = 0;
};


