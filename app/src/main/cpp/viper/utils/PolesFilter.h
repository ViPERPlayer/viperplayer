#pragma once

#include <cstdint>

typedef struct {
    float lowPoleCoeff;
    float highPoleCoeff;

    float inputDelay[3];
    float lowPole[4];
    float highPole[4];
} ChannelState;


class PolesFilter {
public:
    PolesFilter(uint32_t samplingRate);

    void Reset();

    void UpdateCoeff();

    void DoFilterLeft(float inputSample, float *lowOut, float *highOut, float *bandOut);

    void DoFilterRight(float inputSample, float *lowOut, float *highOut, float *bandOut);

    void SetPassFilter(uint32_t lowCutFreq, uint32_t highCutFreq);

    void SetSamplingRate(uint32_t samplingRate);

    ChannelState channels[2];
    uint32_t lowCutFreq = 160;
    uint32_t highCutFreq = 8000;
    uint32_t samplingRate;
};


