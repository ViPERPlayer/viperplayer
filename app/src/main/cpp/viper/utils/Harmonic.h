#pragma once

#include <cstdint>

class Harmonic {
public:
    Harmonic();

    double Process(double sample);
    void Reset();
    void SetHarmonics(const float *amplitudes);
    void UpdateCoeffs(const float *amplitudes);

private:
    float coeffs[11];       // power-series coefficients of the waveshaping polynomial
    double lastProcessed;   // previous polynomial output (for the differentiator)
    double prevOut;         // leaky-integrated differentiator state / returned value
    uint32_t warmupSamples; // number of initial samples to mute while transients settle
    uint32_t sampleCounter; // samples processed so far, capped at warmupSamples
};


