#include <cmath>
#include <cstring>
#include "IIRFilter.h"

// Iscle: Verified with the latest version at 13/12/2022

IIRFilter::IIRFilter(uint32_t samplingRate, uint32_t bands) : samplingRate(samplingRate) {
    if (bands == 10 || bands == 15 || bands == 25 || bands == 31) {
        this->bands = bands;
        this->minPhaseIirCoeffs.UpdateCoeffs(this->bands, this->samplingRate);
    } else {
        this->bands = 0;
    }

    this->bandGainLinear.fill(0.636f);

    Reset();
}

void IIRFilter::Process(float *samples, uint32_t size) {
    if (!this->enable) return;

    double *coeffs = this->minPhaseIirCoeffs.GetCoefficients();
    if (coeffs == nullptr || size == 0) return;

    for (uint32_t i = 0; i < size; i++) {
        for (uint32_t j = 0; j < 2; j++) {
            double sample = samples[i * 2 + j];
            double accumulated = 0.0;

            for (uint32_t k = 0; k < this->bands; k++) {
                uint32_t bufIdx = this->writeIndex + j * 8 + k * 16;
                this->state[bufIdx] = sample;

                double coeff1 = coeffs[k * 4];
                double coeff2 = coeffs[k * 4 + 1];
                double coeff3 = coeffs[k * 4 + 2];

                double a = coeff3 * this->state[bufIdx + ((this->delay1Index + 3) - this->writeIndex)];
                double b = coeff2 * (sample - this->state[bufIdx + (this->delay2Index - this->writeIndex)]);
                double c = coeff1 * this->state[bufIdx + ((this->delay2Index - this->writeIndex) + 3)];

                double tmp = (a + b) - c;

                this->state[bufIdx + 3] = tmp;
                accumulated += tmp * this->bandGainLinear[k];
            }

            samples[i * 2 + j] = (float) accumulated;
        }

        this->writeIndex = (this->writeIndex + 1) % 3;
        this->delay1Index = (this->delay1Index + 1) % 3;
        this->delay2Index = (this->delay2Index + 1) % 3;
    }
}

void IIRFilter::Reset() {
    memset(this->state, 0, sizeof(state));
    this->writeIndex = 2;
    this->delay1Index = 1;
    this->delay2Index = 0;
}

void IIRFilter::SetBandLevel(uint32_t band, float level) {
    if (band > 30) return;
    double bandLevel = pow(10.0, (double) level / 20.0);
    this->bandGainLinear[band] = (float) (bandLevel * 0.636);
}

void IIRFilter::SetEnable(bool enable) {
    if (this->enable != enable) {
        if (enable) {
            Reset();
        }
        this->enable = enable;
    }
}

void IIRFilter::SetSamplingRate(uint32_t samplingRate) {
    if (this->samplingRate != samplingRate) {
        this->samplingRate = samplingRate;
        if (this->bands != 0) {
            this->minPhaseIirCoeffs.UpdateCoeffs(this->bands, samplingRate);
        }
        Reset();
    }
}
