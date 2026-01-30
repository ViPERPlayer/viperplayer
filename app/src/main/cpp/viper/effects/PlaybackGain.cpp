#include "PlaybackGain.h"
#include <cmath>
#include <algorithm>

namespace viper {
namespace effects {

PlaybackGain::PlaybackGain(uint32_t samplingRate) 
    : samplingRate(samplingRate), enabled(false),
      strength(1), maxGain(1), outputThreshold(1.0f),
      currentGain(1.0), targetGain(1.0) {
    UpdateTimeConstants();
    Reset();
}

PlaybackGain::~PlaybackGain() {
}

void PlaybackGain::SetSamplingRate(uint32_t samplingRate) {
    this->samplingRate = samplingRate;
    UpdateTimeConstants();
    Reset();
}

void PlaybackGain::Reset() {
    currentGain = 1.0;
    targetGain = 1.0;
    limiterL.Reset();
    limiterR.Reset();
    // Re-apply threshold to limiters
    limiterL.SetGate(outputThreshold);
    limiterR.SetGate(outputThreshold);
}

void PlaybackGain::SetEnable(bool enable) {
    if (this->enabled != enable) {
        this->enabled = enable;
        Reset();
    }
}

void PlaybackGain::SetStrength(int strength) {
    this->strength = strength;
    UpdateTimeConstants();
}

void PlaybackGain::SetMaxGain(int maxGain) {
    this->maxGain = maxGain;
    // maxGain > 10 means Infinite, handled in Process logic
}

void PlaybackGain::SetOutputThreshold(float thresholdDb) {
    // Convert dB to linear
    if (thresholdDb > 0.0f) thresholdDb = 0.0f; 
    this->outputThreshold = powf(10.0f, thresholdDb / 20.0f);
    limiterL.SetGate(this->outputThreshold);
    limiterR.SetGate(this->outputThreshold);
}

void PlaybackGain::UpdateTimeConstants() {
    // Basic AGC time constants based on "strength"
    // 1: Slow attack/release (Weak)
    // 2: Medium
    // 3: Fast (Strong)
    
    switch (strength) {
        case 3: // Strong
            attackTime = 0.05f;
            releaseTime = 0.5f;
            break;
        case 2: // Moderate
            attackTime = 0.1f;
            releaseTime = 1.0f;
            break;
        case 1: // Weak
        default:
            attackTime = 0.2f;
            releaseTime = 2.0f;
            break;
    }
    
    // Adjust for sampling rate if needed (this is a simplified conceptual mapping)
    // Real DSP implementation would calculate coefs here.
}

void PlaybackGain::Process(float *buffer, uint32_t size) {
    if (!enabled) return;

    // Simple AGC + Limiter Implementation
    // This is a placeholder for a robust algorithm.
    // Logic:
    // 1. Detect peak input signal
    // 2. Calculate needed gain to reach threshold (up to maxGain)
    // 3. Smoothly adjust currentGain towards needed gain (Attack/Release)
    // 4. Apply gain
    // 5. Hard limit output to threshold

    float maxGainFactor = (maxGain > 10) ? 100.0f : (float)maxGain; // 100x for "Infinite"

    // Coefficients for single-pole smoothing
    float attackCoef = 1.0f - expf(-1.0f / (attackTime * samplingRate));
    float releaseCoef = 1.0f - expf(-1.0f / (releaseTime * samplingRate));

    for (uint32_t i = 0; i < size * 2; i += 2) {
        float L = buffer[i];
        float R = buffer[i+1];
        
        float inputPeak = std::max(std::abs(L), std::abs(R));
        
        // Calculate desired gain
        // We want inputPeak * desiredGain = outputThreshold (ideally)
        // But we amplify low signals.
        // Let's assume we want to boost quiet signals up to reasonable level.
        
        float desiredGain = 1.0f;
        if (inputPeak < 0.00001f) {
             desiredGain = maxGainFactor;
        } else {
             // Target: Normalize to outputThreshold
             desiredGain = outputThreshold / inputPeak;
             if (desiredGain > maxGainFactor) desiredGain = maxGainFactor;
             if (desiredGain < 1.0f) desiredGain = 1.0f; // Don't attenuate active signal (compressor behavior is handled by limiter)
        }
        
        // Smooth gain
        if (desiredGain > currentGain) {
            currentGain += (desiredGain - currentGain) * releaseCoef; // Gain increase (Release from compression view, or Attack for AGC)
        } else {
            currentGain += (desiredGain - currentGain) * attackCoef; // Gain reduction
        }
        
        // Apply gain
        L *= (float)currentGain;
        R *= (float)currentGain;
        
        // Output Limiter
        // Use reuse SoftwareLimiter
        L = limiterL.Process(L);
        R = limiterR.Process(R);
        
        buffer[i] = L;
        buffer[i+1] = R;
    }
}

} // namespace effects
} // namespace viper
