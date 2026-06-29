#pragma once

#include <cstdint>
#include "../utils/CRevModel.h"

class Reverberation {
public:
    Reverberation();

    void Process(float *buffer, uint32_t size);
    void Reset();
    void SetSamplingRate(uint32_t samplingRate);
    void SetDamp(float value);
    void SetDry(float value);
    void SetEnable(bool enable);
    void SetRoomSize(float value);
    void SetWet(float value);
    void SetWidth(float value);

private:
    CRevModel model;
    // Cached sampling rate (this+0x2e4). Default 0xac44 = 44100 (ctor @ 00068d3c).
    uint32_t samplingRate = 44100;
    bool enable = false;
};



