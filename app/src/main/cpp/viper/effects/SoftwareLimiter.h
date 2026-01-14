#pragma once

#include <cstdint>

class SoftwareLimiter {
public:
    SoftwareLimiter();

    float Process(float sample);
    void Reset();
    void SetGate(float gate);

private:
    float gate;
    float targetGain;
    float gainEnvelope;
    float envelopeMemory;
    float lookaheadBuffer[256];
    float peakTree[512];
    uint32_t writePos;
    bool limitingActive;
};


