#include "ViPERDDC.h"
#include <cstring>
#include <algorithm>

ViPERDDC::ViPERDDC(uint32_t samplingRate) :
    enable(false),
    setCoeffsOk(false),
    samplingRate(samplingRate),
    arrSize(0) {}

void ViPERDDC::Process(float *samples, uint32_t size) {
    if (!this->setCoeffsOk || this->arrSize == 0) return;
    if (!this->enable) return;

    auto it = this->coeffsMap.find(this->samplingRate);
    if (it == this->coeffsMap.end()) return;

    const std::vector<std::array<float, 5>> *coeffsArr = &it->second;

    // Stereo, interleaved. The two channels share no state, so processing both within
    // one section loop is equivalent to the original's two separate per-channel passes
    // (@0006df90: left cascade first, then right cascade).
    for (uint32_t i = 0; i < size * 2; i += 2) {
        float sampleL = samples[i];
        float sampleR = samples[i + 1];

        // Cascade of biquads: each section's output becomes the next section's input.
        for (uint32_t j = 0; j < this->arrSize; j++) {
            const std::array<float, 5> *coeffs = &(*coeffsArr)[j];

            // Coefficient layout { b0, b1, b2, a1, a2 }; a1/a2 are pre-negated so the
            // Direct-Form-I difference equation below is purely additive (matches the
            // original's piVar7[0..4] usage).
            float b0 = (*coeffs)[0];
            float b1 = (*coeffs)[1];
            float b2 = (*coeffs)[2];
            float a1 = (*coeffs)[3];
            float a2 = (*coeffs)[4];

            float outL =
                    sampleL * b0 +
                    x1L[j] * b1 +
                    x2L[j] * b2 +
                    y1L[j] * a1 +
                    y2L[j] * a2;

            x2L[j] = x1L[j];
            x1L[j] = sampleL;
            y2L[j] = y1L[j];
            y1L[j] = outL;

            sampleL = outL;

            float outR =
                    sampleR * b0 +
                    x1R[j] * b1 +
                    x2R[j] * b2 +
                    y1R[j] * a1 +
                    y2R[j] * a2;

            x2R[j] = x1R[j];
            x1R[j] = sampleR;
            y2R[j] = y1R[j];
            y1R[j] = outR;

            sampleR = outR;
        }

        samples[i] = sampleL;
        samples[i + 1] = sampleR;
    }
}

void ViPERDDC::ReleaseResources() {
    this->setCoeffsOk = false;
    this->coeffsMap.clear();

    this->x1L.clear();
    this->x1R.clear();
    this->x2L.clear();
    this->x2R.clear();
    this->y1L.clear();
    this->y1R.clear();
    this->y2L.clear();
    this->y2R.clear();
}

void ViPERDDC::Reset() {
    if (!this->setCoeffsOk) return;
    if (this->arrSize == 0) return;

    // Fidelity: the original Reset (@0006dbb0) clears ONLY the x1 input-history
    // buffers (0x14 = x1L, 0x18 = x1R); the x2/y1/y2 buffers are intentionally left
    // untouched. This is preserved verbatim so behaviour matches v2505 exactly. (The
    // full buffers are still zero-initialised once when the cascade is first sized in
    // AddCoeffs, so a fresh filter still starts from silence.)
    std::fill(this->x1L.begin(), this->x1L.end(), 0.0f);
    std::fill(this->x1R.begin(), this->x1R.end(), 0.0f);
}

void ViPERDDC::ClearCoeffs() {
    ReleaseResources();
}

void ViPERDDC::AddCoeffs(uint32_t rate, const std::vector<std::array<float, 5>>& coeffs) {
    if (coeffs.empty()) return;

    // If this is the first set of coeffs added, set up the buffer size
    if (this->coeffsMap.empty()) {
        this->arrSize = (uint32_t)coeffs.size();
        if (this->arrSize == 0) return;

        this->x1L.resize(this->arrSize);
        this->x1R.resize(this->arrSize);
        this->x2L.resize(this->arrSize);
        this->x2R.resize(this->arrSize);
        this->y1L.resize(this->arrSize);
        this->y1R.resize(this->arrSize);
        this->y2L.resize(this->arrSize);
        this->y2R.resize(this->arrSize);

        // resize() value-initialises every history element to 0.0f, so the whole
        // cascade starts from silence (the original memset's all eight state arrays
        // when SetCoeffs allocates them @0006dc6c).
        this->setCoeffsOk = true;
    } else {
        // Validation: subsequent rates must have same size
        if (coeffs.size() != this->arrSize) {
           return; 
        }
    }

    this->coeffsMap[rate] = coeffs;
}

void ViPERDDC::SetEnable(bool enable) {
    if (this->enable != enable) {
        this->enable = enable;
        if (enable) {
            Reset();
        }
    }
}

void ViPERDDC::SetSamplingRate(uint32_t samplingRate) {
    if (this->samplingRate != samplingRate) {
        this->samplingRate = samplingRate;
        Reset();
    }
}


