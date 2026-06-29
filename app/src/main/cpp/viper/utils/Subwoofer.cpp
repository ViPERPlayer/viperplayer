#include "Subwoofer.h"
#include <cmath>

Subwoofer::Subwoofer(uint32_t samplingRate) {
    this->peak[0].RefreshFilter(MultiBiquad::FilterType::PEAK, 0.0, 37.0, samplingRate, 1.0, false);
    this->peak[1].RefreshFilter(MultiBiquad::FilterType::PEAK, 0.0, 37.0, samplingRate, 1.0, false);
    this->peakLow[0].RefreshFilter(MultiBiquad::FilterType::PEAK, 0.0, 75.0, samplingRate, 1.0, false);
    this->peakLow[1].RefreshFilter(MultiBiquad::FilterType::PEAK, 0.0, 75.0, samplingRate, 1.0, false);
    this->lowpass[0].RefreshFilter(MultiBiquad::FilterType::LOW_PASS, 0.0, 200.0, samplingRate, 1.0, false);
    this->lowpass[1].RefreshFilter(MultiBiquad::FilterType::LOW_PASS, 0.0, 200.0, samplingRate, 1.0, false);
}

void Subwoofer::Process(float *samples, uint32_t size) {
    for (uint32_t i = 0; i < size * 2; i += 2) {
        double tmp;

        tmp = this->peak[0].ProcessSample(samples[i]);
        tmp = this->peakLow[0].ProcessSample(tmp);
        tmp = this->lowpass[0].ProcessSample(tmp - samples[i]);
        samples[i] = (samples[i] * 0.5f) + ((float) tmp * 0.6f);

        tmp = this->peak[1].ProcessSample(samples[i + 1]);
        tmp = this->peakLow[1].ProcessSample(tmp);
        tmp = this->lowpass[1].ProcessSample(tmp - samples[i + 1]);
        samples[i + 1] = (samples[i + 1] * 0.5f) + ((float) tmp * 0.6f);
    }
}

void Subwoofer::SetBassGain(uint32_t samplingRate, float gainDb) {
    // 0x6bc28: the linear bass factor is converted to dB only when it is above a
    // small positive threshold; otherwise the boost is held at 0 dB. The guard also
    // keeps log10 away from gainDb<=0 (e.g. gainDb=0 at construction) which would
    // otherwise produce -inf/NaN coefficients.
    // 0x0006bda0 = 0.0001 (compared as double), 0x0006bda8 = 0.0 (fallback)
    float gain, gainLower;
    if ((double) gainDb > 0.0001) {
        gain = (float) (20.0 * log10((double) gainDb));
        gainLower = (float) (20.0 * log10((double) gainDb / 8.0));
    } else {
        gain = gainLower = 0.0f;
    }

    this->peak[0].RefreshFilter(MultiBiquad::FilterType::PEAK, gain, 44.0, samplingRate, 0.75, true);
    this->peak[1].RefreshFilter(MultiBiquad::FilterType::PEAK, gain, 44.0, samplingRate, 0.75, true);
    this->peakLow[0].RefreshFilter(MultiBiquad::FilterType::PEAK, gainLower, 80.0, samplingRate, 0.2, true);
    this->peakLow[1].RefreshFilter(MultiBiquad::FilterType::PEAK, gainLower, 80.0, samplingRate, 0.2, true);
    this->lowpass[0].RefreshFilter(MultiBiquad::FilterType::LOW_PASS, 0.0, 380.0, samplingRate, 0.6, false);
    this->lowpass[1].RefreshFilter(MultiBiquad::FilterType::LOW_PASS, 0.0, 380.0, samplingRate, 0.6, false);
}
