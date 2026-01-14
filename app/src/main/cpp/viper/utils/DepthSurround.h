#pragma once

#include <cstdint>
#include "Biquad.h"
#include "TimeConstDelay.h"

class DepthSurround {
public:
    DepthSurround(uint32_t samplingRate);

    void Process(float *samples, uint32_t size);
    void RefreshStrength();
    void SetSamplingRate(uint32_t samplingRate);
    void SetStrength(short strength);

private:
    short strength = 0;
    bool enabled = false;
    bool strengthAtLeast500 = false;
    float gain = 0;
    float prev[2] = {0, 0};
    TimeConstDelay timeConstDelay[2];
    Biquad highpass;
};
