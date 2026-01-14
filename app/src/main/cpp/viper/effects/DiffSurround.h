#pragma once

#include <cstdint>
#include "../utils/WaveBuffer.h"
#include <array>

class DiffSurround {
public:
    DiffSurround(uint32_t samplingRate);

    void Process(float *samples, uint32_t size);
    void Reset();
    void SetDelayTime(float delayTime);
    void SetEnable(bool enable);
    void SetSamplingRate(uint32_t samplingRate);

    uint32_t samplingRate;
    bool enable = false;
    float delayTime = 0;
    std::array<WaveBuffer, 2> buffers;
};


