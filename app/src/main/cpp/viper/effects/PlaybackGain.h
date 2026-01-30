#pragma once

#include "SoftwareLimiter.h"
#include <vector>

namespace viper {
namespace effects {

class PlaybackGain {
public:
    PlaybackGain(uint32_t samplingRate);
    ~PlaybackGain();

    void SetSamplingRate(uint32_t samplingRate);
    void Reset();

    void Process(float *buffer, uint32_t size);

    void SetEnable(bool enable);
    void SetStrength(int strength); // 1-3
    void SetMaxGain(int maxGain); // 1-10, >10 = Infinite
    void SetOutputThreshold(float threshold); // dB

private:
    bool enabled;
    uint32_t samplingRate;
    int strength; // 1: Weak, 2: Moderate, 3: Strong
    int maxGain; // Factor
    float outputThreshold; // Linear scale

    // AGC State
    double currentGain;
    double targetGain;
    
    // Time constants (based on strength)
    float attackTime;
    float releaseTime;

    // Limiter for output protection
    SoftwareLimiter limiterL;
    SoftwareLimiter limiterR;
    
    // Internal helpers
    void UpdateTimeConstants();
};

} // namespace effects
} // namespace viper
