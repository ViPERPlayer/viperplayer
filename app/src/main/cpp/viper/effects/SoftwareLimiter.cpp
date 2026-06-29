#include <cstring>
#include <cmath>
#include "SoftwareLimiter.h"

namespace {
    // ViPER4Android v2505 stores the limiter coefficients in Q25 fixed point
    // (1.0f == 0x2000000 == 2^25, see DAT_000677a4 = 0x4c000000 = 33554432.0f
    // used by SetGate). These are the exact ported coefficient values.
    constexpr float kQ25Scale = 33554432.0f; // 0x2000000 = 2^25

    // Smoothed gain-ratio memory: mem = 0.8999*mem + 0.0999*targetRatio
    constexpr float kSmoothMemoryCoeff = 0x1ccbfb2 / kQ25Scale; // 0x1ccbfb2 = 0.89990002
    constexpr float kSmoothTargetCoeff = 0x0332618 / kQ25Scale; // 0x0332618 = 0.09990001
    // Gain-envelope release toward unity: env = 0.9999*env + 0.0001
    constexpr float kReleaseOffset     = 0x0000d1c / kQ25Scale; // 0x0000d1c = 0.00010002
    constexpr float kReleaseCoeff      = 0x1fff2e5 / kQ25Scale; // 0x1fff2e5 = 0.99990001
    // Default gate threshold installed by the constructor.
    constexpr float kDefaultGate       = 0x1ffffff / kQ25Scale; // 0x1ffffff = 0.99999997
}

SoftwareLimiter::SoftwareLimiter() {
    this->gate = kDefaultGate;
    this->targetGain = 1.0f;
    this->gainEnvelope = 1.0f;
    this->envelopeMemory = 1.0f;
    this->writePos = 0;
    this->limitingActive = false;

    Reset();
}

float SoftwareLimiter::ApplyGainEnvelope(float delayed, float gainTarget) {
    // One-pole release of the gain envelope back toward unity.
    float release = this->gainEnvelope * kReleaseCoeff + kReleaseOffset;
    // One-pole smoothing of the requested gain ratio.
    float smoothed = gainTarget * kSmoothTargetCoeff + this->envelopeMemory * kSmoothMemoryCoeff;
    this->envelopeMemory = smoothed;

    // Take whichever envelope attenuates more.
    float gain = (smoothed < release) ? smoothed : release;
    this->gainEnvelope = gain;

    float out = delayed * gain;
    // Hard safety clamp: if the smoothed gain still let the sample exceed the
    // gate, snap the gain to exactly hit the gate.
    if (this->gate < std::abs(out)) {
        gain = this->gate / std::abs(delayed);
        this->gainEnvelope = gain;
        out = delayed * gain;
    }
    return out;
}

float SoftwareLimiter::Process(float sample) {
    float magnitude = std::abs(sample);

    // Decide whether the lookahead peak tree should be updated this sample.
    // It runs whenever the input exceeds the gate, or while a previous
    // over-gate transient is still being released.
    bool runPeakTree;
    if (this->gate < magnitude) {
        if (!this->limitingActive) {
            // Rising edge into limiting: clear the stale peak tree.
            memset(this->peakTree, 0, sizeof(this->peakTree));
            this->limitingActive = true;
        }
        runPeakTree = true;
    } else {
        runPeakTree = this->limitingActive;
    }

    if (runPeakTree) {
        // Update the binary max-tree and read back the window peak into
        // `magnitude`. Eight levels cover the 256-sample lookahead window.
        uint32_t node = this->writePos;
        for (uint32_t level = 8; level != 0; --level) {
            int span = 2 << level;
            uint32_t sibling = node ^ 1u;
            this->peakTree[512 + (int) node - span] = magnitude;
            node >>= 1;
            float siblingPeak = this->peakTree[512 + (int) sibling - span];
            if (magnitude < siblingPeak) {
                magnitude = siblingPeak;
            }
        }

        if (this->gate < magnitude) {
            // Actively limiting: target gain pulls the window peak down to the
            // gate. (limitingActive is necessarily true on this path.)
            float gainTarget = this->gate / magnitude;
            uint32_t pos = this->writePos;
            this->lookaheadBuffer[pos] = sample;
            pos = (pos + 1) & 0xffu;
            this->writePos = pos;
            float delayed = this->lookaheadBuffer[pos];
            return ApplyGainEnvelope(delayed, gainTarget);
        }

        // Peak fell back to/under the gate: leave limiting.
        this->limitingActive = false;
    }

    // Pass-through path: advance the delay line with unity target gain.
    float gainTarget = this->targetGain;
    uint32_t pos = this->writePos;
    this->lookaheadBuffer[pos] = sample;
    pos = (pos + 1) & 0xffu;
    this->writePos = pos;
    float delayed = this->lookaheadBuffer[pos];
    return ApplyGainEnvelope(delayed, gainTarget);
}

void SoftwareLimiter::Reset() {
    memset(this->lookaheadBuffer, 0, sizeof(this->lookaheadBuffer));
    memset(this->peakTree, 0, sizeof(this->peakTree));
    this->limitingActive = false;
    this->writePos = 0;
    this->gainEnvelope = 1.0f;
    this->envelopeMemory = 1.0f;
}

void SoftwareLimiter::SetGate(float gate) {
    // The original quantised the gate to Q25 (gate * 2^25 + 0.5); in this float
    // port the threshold is kept directly.
    this->gate = gate;
}
