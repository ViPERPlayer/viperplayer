#pragma once

#include <cstdint>
#include "../utils/Biquad.h"
#include "../utils/Subwoofer.h"
#include "../utils/WaveBuffer.h"
#include "../utils/Polyphase.h"

// Iscle: Verified with the latest version at 13/12/2022

class ViPERBass {
public:
    enum class ProcessMode : uint8_t {
        NATURAL_BASS = 0,
        PURE_BASS_PLUS,
        SUBWOOFER,
    };

    ViPERBass(uint32_t samplingRate);

    void Process(float *samples, uint32_t size);
    void Reset();
    void SetBassFactor(float bassFactor);
    void SetEnable(bool enable);
    void SetProcessMode(ProcessMode processMode);
    void SetSamplingRate(uint32_t samplingRate);
    void SetSpeaker(uint32_t speaker);

private:
    Polyphase polyphase;
    Biquad biquad[2];
    Subwoofer subwoofer;
    WaveBuffer waveBuffer;
    bool enable = false;
    ProcessMode processMode = ProcessMode::NATURAL_BASS;
    uint32_t samplingRate;
    float samplingRatePeriod;
    float antiPop = 0;
    uint32_t speaker = 60;
    float bassFactor = 0;
};
