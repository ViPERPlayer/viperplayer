#pragma once

#include <cstdint>
#include <vector>
#include <array>
#include <map>

// ViPERDDC - "Digital Drive Control" convolution/parametric stage from ViPER4Android.
//
// It is a cascade of biquad (second-order IIR) sections, one cascade per channel
// (left/right), driven by per-sample-rate coefficient tables loaded from a VDC file.
//
// FIDELITY NOTE (original v2505 @ 0006d9d8..0006df90):
//   The original ran an INTEGER datapath: each float coefficient was quantized to a
//   Q25 fixed-point integer in SetCoeffs (coeff * 2^25, rounded to nearest):
//       0006df8c = 33554432.0f   (2^25 coefficient scale)
//   and Process accumulated the five products in a 64-bit integer, added a rounding
//   bias of 0x1000000 (2^24, i.e. half an LSB) and arithmetic-shifted right by 25
//   (>>0x19) to return to Q0. The original also only supported exactly two rates,
//   stored in two fixed coefficient slots: 0xac44 (44100) and 48000.
//
//   This reconstruction targets the engine's FLOAT sample pipeline (ViPER::process
//   passes a float* buffer, normalised to [-1, 1]), so the integer datapath does not
//   apply: the biquad runs directly in float using the same topology, coefficient
//   layout and state-update order as the original. Coefficients are kept per-rate in
//   a map keyed by sample rate, generalising the original's two fixed slots while
//   producing identical results for 44100/48000.
//
// Each coefficient set is laid out as { b0, b1, b2, a1, a2 } (the a-coefficients are
// stored already negated so the difference equation is purely additive), matching the
// original's piVar7[0..4] indexing.
class ViPERDDC {
public:
    ViPERDDC(uint32_t samplingRate);

    void Process(float *samples, uint32_t size);
    void Reset();
    void ClearCoeffs();
    void AddCoeffs(uint32_t rate, const std::vector<std::array<float, 5>>& coeffs);
    void SetEnable(bool enable);
    void SetSamplingRate(uint32_t samplingRate);

private:
    bool enable = false;
    bool setCoeffsOk;
    uint32_t samplingRate;
    uint32_t arrSize;       // number of biquad sections per channel
    std::map<uint32_t, std::vector<std::array<float, 5>>> coeffsMap;
    // Per-section biquad history, one element per cascade section.
    std::vector<float> x1L;
    std::vector<float> x1R;
    std::vector<float> x2L;
    std::vector<float> x2R;
    std::vector<float> y1L;
    std::vector<float> y1R;
    std::vector<float> y2L;
    std::vector<float> y2R;

    void ReleaseResources();
};


