#pragma once

#include <cstdint>
#include <vector>
#include <array>
#include <complex>

/**
 * FIR Equalizer
 * 
 * Implements a high-quality Linear Phase FIR Equalizer using the Frequency Sampling Method.
 * 
 * Features:
 * - 10, 15, or 31 band configuration.
 * - Linear Phase (constant group delay).
 * - Spline interpolation for smooth frequency response.
 * - Overlap-Add / Overlap-Save convolution (Stubbed for now, using direct form or simple block for initial implementation).
 * - Windowing (Blackman-Harris) to minimize spectral leakage.
 */
class FIREqualizer {
public:
    FIREqualizer();
    ~FIREqualizer();

    void SetSamplingRate(uint32_t samplingRate);
    void SetBandCount(uint32_t count);
    void SetBandGain(uint32_t index, float gainDb);
    void SetEnable(bool enable);
    void Reset();

    void Process(float *samples, uint32_t count);

private:
    // Internal FFT Interface (Stubs)
    void StubFFT(std::vector<std::complex<float>>& data, bool inverse);

    // Filter Generation
    void UpdateCoefficients();
    std::vector<float> GenerateImpulseResponse();
    float InterpolateResponse(float freq);

    // State
    bool enabled = false;
    uint32_t samplingRate = 44100;
    uint32_t bandCount = 10;
    std::array<float, 31> bandGainsDb; // Max 31 bands
    std::vector<float> kernel;
    std::vector<float> buffer; // For convolution overlap
    
    // FIR Configuration
    static const int KERNEL_SIZE = 4096; // N for FFT (frequency resolution)
    bool dirty = false;
};
