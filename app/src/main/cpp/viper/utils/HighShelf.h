#pragma once

#include <cstdint>

class HighShelf {
public:
    double Process(double sample);
    void SetFrequency(float freq);
    void SetQuality(float quality);
    void SetGain(float gain);
    void SetSamplingRate(uint32_t samplingRate);

private:
    float frequency;
    // Stored to mirror the original (HighShelf::SetQuality @ 0006c7a8), which keeps quality at
    // offset 4 but never reads it: SetSamplingRate derives the shelf "z" term from sqrt(2*A) only.
    float quality;
    double gain;
    double x_1;
    double x_2;
    double y_1;
    double y_2;
    double b0;
    double b1;
    double b2;
    double a0;
    double a1;
    double a2;
};



