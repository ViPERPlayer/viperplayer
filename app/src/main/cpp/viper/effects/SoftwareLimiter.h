#pragma once

#include <cstdint>

// Lookahead brick-wall limiter (the master output limiter of ViPER4Android).
//
// The original v2505 binary (SoftwareLimiter @ 0x67788) processed Q25 fixed
// point samples (1.0 == 0x2000000 == 2^25). This port keeps the identical
// algorithm/topology but operates on the float samples used by the rest of the
// engine, so SetGate/Process/Reset take and return floats directly.
class SoftwareLimiter {
public:
    SoftwareLimiter();

    float Process(float sample);
    void Reset();
    void SetGate(float gate);

private:
    // Runs the smoothed limiter-gain envelope on a delayed sample and returns
    // the limited output. Shared by the limiting and pass-through paths.
    float ApplyGainEnvelope(float delayed, float gainTarget);

    float gate;            // +0x10 threshold above which limiting engages
    float targetGain;      // +0x14 gain ratio used when not actively limiting (1.0)
    float gainEnvelope;    // +0x18 fast gain-reduction envelope (released toward 1.0)
    float envelopeMemory;  // +0x1c smoothed gain-ratio memory
    float lookaheadBuffer[256]; // +0x20 circular delay line (255-sample lookahead)
    float peakTree[512];        // +0x420 binary max-tree over the lookahead window
    uint32_t writePos;     // +0xc20 write cursor into lookaheadBuffer
    bool limitingActive;   // +0xc24 true while the limiter is engaged
};
