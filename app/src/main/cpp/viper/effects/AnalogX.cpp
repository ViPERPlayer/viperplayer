#include "AnalogX.h"
#include <cstring>

// Harmonic generator coefficients. The original binary stores three separate
// 10-float tables (one per processing model, at 0xce8b4 / 0xce8dc / 0xce904),
// but all three are byte-for-byte identical, so a single table is faithful.
// 0xce8b4 = 0x3c23d70a = 0.01f
// 0xce8b8 = 0x3ca3d70a = 0.02f
// 0xce8bc = 0x38d1b717 = 0.0001f
// 0xce8c0 = 0x3a83126f = 0.001f  (remaining six entries are 0.0f)
static const float ANALOGX_HARMONICS[] = {
        0.01f,
        0.02f,
        0.0001f,
        0.001f,
        0.0f,
        0.0f,
        0.0f,
        0.0f,
        0.0f,
        0.0f,
};

AnalogX::AnalogX(uint32_t samplingRate) : samplingRate(samplingRate) {
    Reset();
}

void AnalogX::Process(float *samples, uint32_t size) {
    if (this->enable) {
        for (uint32_t i = 0; i < size * 2; i += 2) {
            double inL = samples[i];
            double outL = this->highPass[0].ProcessSample(inL);
            outL = this->harmonic[0].Process(outL);
            outL = this->lowPass[0].ProcessSample(inL + outL * this->gain);
            // 0x1998c7e / 2^25 = 0.8 (post low-pass attenuation)
            outL = this->peak[0].ProcessSample(outL * 0.8);
            samples[i] = (float) outL;

            double inR = samples[i + 1];
            double outR = this->highPass[1].ProcessSample(inR);
            outR = this->harmonic[1].Process(outR);
            outR = this->lowPass[1].ProcessSample(inR + outR * this->gain);
            outR = this->peak[1].ProcessSample(outR * 0.8);
            samples[i + 1] = (float) outR;
        }

        // Mute the output during the first samplingRate/4 samples (0.25 s) so
        // that the filter transients settle silently after a reset.
        if (this->warmupCounter < this->samplingRate / 4) {
            this->warmupCounter += size;
            memset(samples, 0, size * 2 * sizeof(float));
        }
    }
}

void AnalogX::Reset() {
    // High-pass: 240 Hz, Q = 0.717
    // 0x43700000 = 240.0  |  0x3f378d50 = 0.717
    this->highPass[0].RefreshFilter(MultiBiquad::FilterType::HIGH_PASS, 0.0, 240.0, this->samplingRate, 0.717, false);
    this->highPass[1].RefreshFilter(MultiBiquad::FilterType::HIGH_PASS, 0.0, 240.0, this->samplingRate, 0.717, false);

    // Peak: gain 0.58, 633 Hz, Q = 6.28, bandwidth in octaves
    // 0x3f147ae1 = 0.58  |  0x441e4000 = 633.0  |  0x40c8f5c3 = 6.28
    this->peak[0].RefreshFilter(MultiBiquad::FilterType::PEAK, 0.58, 633.0, this->samplingRate, 6.28, true);
    this->peak[1].RefreshFilter(MultiBiquad::FilterType::PEAK, 0.58, 633.0, this->samplingRate, 6.28, true);

    this->harmonic[0].Reset();
    this->harmonic[1].Reset();

    switch (this->processingModel) {
        case 0: {
            this->harmonic[0].SetHarmonics(ANALOGX_HARMONICS);
            this->harmonic[1].SetHarmonics(ANALOGX_HARMONICS);

            // 0x150 = 0x1332618 / 2^25 = 0.6
            this->gain = 0.6;

            // Low-pass: 19650 Hz, Q = 0.717  |  0x46998400 = 19650.0
            this->lowPass[0].RefreshFilter(MultiBiquad::FilterType::LOW_PASS, 0.0, 19650.0, this->samplingRate, 0.717, false);
            this->lowPass[1].RefreshFilter(MultiBiquad::FilterType::LOW_PASS, 0.0, 19650.0, this->samplingRate, 0.717, false);
            break;
        }
        case 1: {
            this->harmonic[0].SetHarmonics(ANALOGX_HARMONICS);
            this->harmonic[1].SetHarmonics(ANALOGX_HARMONICS);

            // 0x150 = 0x266594b / 2^25 = 1.2
            this->gain = 1.2;

            // Low-pass: 18233 Hz, Q = 0.717  |  0x468e7200 = 18233.0
            this->lowPass[0].RefreshFilter(MultiBiquad::FilterType::LOW_PASS, 0.0, 18233.0, this->samplingRate, 0.717, false);
            this->lowPass[1].RefreshFilter(MultiBiquad::FilterType::LOW_PASS, 0.0, 18233.0, this->samplingRate, 0.717, false);
            break;
        }
        case 2: {
            this->harmonic[0].SetHarmonics(ANALOGX_HARMONICS);
            this->harmonic[1].SetHarmonics(ANALOGX_HARMONICS);

            // 0x150 = 0x4ccbfb1 / 2^25 = 2.4
            this->gain = 2.4;

            // Low-pass: 16307 Hz, Q = 0.717  |  0x467ecc00 = 16307.0
            this->lowPass[0].RefreshFilter(MultiBiquad::FilterType::LOW_PASS, 0.0, 16307.0, this->samplingRate, 0.717, false);
            this->lowPass[1].RefreshFilter(MultiBiquad::FilterType::LOW_PASS, 0.0, 16307.0, this->samplingRate, 0.717, false);
            break;
        }
    }

    this->warmupCounter = 0;
}

void AnalogX::SetEnable(bool enable) {
    if (this->enable != enable) {
        if (!this->enable) {
            Reset();
        }
        this->enable = enable;
    }
}

void AnalogX::SetProcessingModel(int processingModel) {
    if (this->processingModel != processingModel) {
        this->processingModel = processingModel;
        Reset();
    }
}

void AnalogX::SetSamplingRate(uint32_t samplingRate) {
    if (this->samplingRate != samplingRate) {
        this->samplingRate = samplingRate;
        Reset();
    }
}
