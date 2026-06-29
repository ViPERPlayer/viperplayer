#include "SpectrumExtend.h"

// Filter quality factor shared by the high-pass and low-pass stages.
// 0x3f378d50 = 0.7170000076f
static constexpr float SPECTRUM_FILTER_Q = 0.717f;
// Low-pass cutoff is placed this far below Nyquist. 0x44fa0000 = 2000.0f
static constexpr float SPECTRUM_LOWPASS_MARGIN = 2000.0f;

// Target harmonic spectrum: the odd harmonics (1st, 3rd, 5th, 7th, 9th) at 0.02 each.
// 0.02f = 0x3ca3d70a. Data @ 0xCE848.
static const float SPECTRUM_HARMONICS[10] = {
        0.02f,
        0.0f,
        0.02f,
        0.0f,
        0.02f,
        0.0f,
        0.02f,
        0.0f,
        0.02f,
        0.0f,
};

SpectrumExtend::SpectrumExtend(uint32_t samplingRate) : samplingRate(samplingRate) {
    Reset();
}

void SpectrumExtend::Process(float *samples, uint32_t size) {
    if (!this->enable) return;

    for (uint32_t i = 0; i < size * 2; i += 2) {
        double tmp;

        tmp = this->highpass[0].ProcessSample(samples[i]);
        tmp = this->harmonics[0].Process(tmp);
        tmp = this->lowpass[0].ProcessSample(tmp * this->exciter);
        samples[i] = samples[i] + (float) tmp;

        tmp = this->highpass[1].ProcessSample(samples[i + 1]);
        tmp = this->harmonics[1].Process(tmp);
        tmp = this->lowpass[1].ProcessSample(tmp * this->exciter);
        samples[i + 1] = samples[i + 1] + (float) tmp;
    }
}

void SpectrumExtend::Reset() {
    // High-pass at the reference frequency isolates the upper band that gets re-harmonised.
    this->highpass[0].RefreshFilter(MultiBiquad::FilterType::HIGH_PASS, 0.0, (float) this->referenceFreq, this->samplingRate,
                                    SPECTRUM_FILTER_Q, false);
    this->highpass[1].RefreshFilter(MultiBiquad::FilterType::HIGH_PASS, 0.0, (float) this->referenceFreq, this->samplingRate,
                                    SPECTRUM_FILTER_Q, false);

    // Low-pass just below Nyquist keeps the generated harmonics from aliasing.
    this->lowpass[0].RefreshFilter(MultiBiquad::FilterType::LOW_PASS, 0.0, (float) this->samplingRate / 2.0f - SPECTRUM_LOWPASS_MARGIN,
                                   this->samplingRate, SPECTRUM_FILTER_Q, false);
    this->lowpass[1].RefreshFilter(MultiBiquad::FilterType::LOW_PASS, 0.0, (float) this->samplingRate / 2.0f - SPECTRUM_LOWPASS_MARGIN,
                                   this->samplingRate, SPECTRUM_FILTER_Q, false);

    this->harmonics[0].Reset();
    this->harmonics[1].Reset();

    this->harmonics[0].SetHarmonics(SPECTRUM_HARMONICS);
    this->harmonics[1].SetHarmonics(SPECTRUM_HARMONICS);
}

void SpectrumExtend::SetEnable(bool enable) {
    if (this->enable != enable) {
        if (enable) {
            Reset();
        }
        this->enable = enable;
    }
}

void SpectrumExtend::SetExciter(float value) {
    this->exciter = value;
}

void SpectrumExtend::SetReferenceFrequency(uint32_t freq) {
    if (this->samplingRate / 2 - 100 < freq) {
        freq = this->samplingRate / 2 - 100;
    }
    this->referenceFreq = freq;
    Reset();
}

void SpectrumExtend::SetSamplingRate(uint32_t samplingRate) {
    if (this->samplingRate != samplingRate) {
        this->samplingRate = samplingRate;
        if (samplingRate / 2 - 100 < this->referenceFreq) {
            this->referenceFreq = samplingRate / 2 - 100;
        }
        Reset();
    }
}
