#include "DynamicBass.h"

DynamicBass::DynamicBass(uint32_t samplingRate) : samplingRate(samplingRate), filterX(samplingRate),
                                                  filterY(samplingRate) {
    this->qPeak = 0;
    // bassGain/sideGainX/sideGainY default to 0x02000000 (Q25 1.0) in the binary;
    // this float port keeps the equivalent linear gain of 1.0.
    this->bassGain = 1.f;
    this->sideGainX = 1.f;
    this->sideGainY = 1.f;
    // filterX spans 120 Hz .. samplingRate/4; filterY spans 40 Hz .. 80 Hz.
    this->lowFreqX = 120;                      // 0x78
    this->highFreqX = this->samplingRate / 4;  // iVar1 >> 2
    this->lowFreqY = 40;                       // 0x28
    this->highFreqY = 80;                      // 0x50

    this->filterX.SetPassFilter(this->lowFreqX, this->highFreqX);
    this->filterY.SetPassFilter(this->lowFreqY, this->highFreqY);
    // 55.0 = 0x425c0000, 666.0 = 0x44268000 @ 0x60320; qFactor = qPeak/666 + 0.5
    this->lowPass.SetLowPassParameter(55.f, this->samplingRate, this->qPeak / 666.f + 0.5f);
}

void DynamicBass::FilterSamples(float *samples, uint32_t size) {
    // DoFilterLeft/DoFilterRight yield (low, high, band) outputs in that order.
    if (this->lowFreqX <= 120) {
        // Bypass path (lowFreqX < 0x79): a single low-pass biquad on the mono
        // sum is mixed back into both channels.
        for (uint32_t i = 0; i < size; i++) {
            float left = samples[2 * i];
            float right = samples[2 * i + 1];
            float bass = (float) this->lowPass.ProcessSample(left + right);
            samples[2 * i] = left + bass;
            samples[2 * i + 1] = right + bass;
        }
    } else {
        // Full two-stage poles-filter path.
        for (uint32_t i = 0; i < size; i++) {
            float xLowL, xHighL, xBandL, xLowR, xHighR, xBandR;
            float yLowL, yHighL, yBandL, yLowR, yHighR, yBandR;

            // Stage X splits each channel into low/high/band.
            this->filterX.DoFilterLeft(samples[2 * i], &xLowL, &xHighL, &xBandL);
            this->filterX.DoFilterRight(samples[2 * i + 1], &xLowR, &xHighR, &xBandR);
            // Stage Y re-splits the bass-gained low band.
            this->filterY.DoFilterLeft(this->bassGain * xLowL, &yLowL, &yHighL, &yBandL);
            this->filterY.DoFilterRight(this->bassGain * xLowR, &yLowR, &yHighR, &yBandR);

            samples[2 * i] = xHighL + yBandL + this->sideGainX * yHighL +
                             this->sideGainY * yLowL + xBandL;
            samples[2 * i + 1] = xHighR + yBandR + this->sideGainX * yHighR +
                                 this->sideGainY * yLowR + xBandR;
        }
    }
}

void DynamicBass::Reset() {
    this->filterX.Reset();
    this->filterY.Reset();
    this->lowPass.SetLowPassParameter(55.0f, this->samplingRate, this->qPeak / 666.0f + 0.5f);
}

void DynamicBass::SetBassGain(float gain) {
    // Binary stores round(gain * 2^25) in Q25 (0x60314 = 33554432.0); the float
    // port keeps the linear gain directly.
    this->bassGain = gain;

    // 0x60318 = 1600.0. Truncate toward zero (binary: (int)(short)(int)fVar2),
    // then clamp to <= 1600 (compared against 0x641 = 1601).
    int peak = (int) (((gain - 1.0f) / 20.0f) * 1600.0f);
    if (peak > 1600) {
        peak = 1600;
    }
    this->qPeak = peak;

    // 55.0 = 0x425c0000, 666.0 = 0x44268000 @ 0x60320; qFactor = qPeak/666 + 0.5
    this->lowPass.SetLowPassParameter(55.0f, this->samplingRate, this->qPeak / 666.0f + 0.5f);
}

void DynamicBass::SetFilterXPassFrequency(uint32_t low, uint32_t high) {
    this->lowFreqX = low;
    this->highFreqX = high;

    this->filterX.SetPassFilter(low, high);
    this->filterX.SetSamplingRate(this->samplingRate);
    // The X variant (00060774) re-derives the low-pass biquad; the Y variant does not.
    this->lowPass.SetLowPassParameter(55.0f, this->samplingRate, this->qPeak / 666.0f + 0.5f);
}

void DynamicBass::SetFilterYPassFrequency(uint32_t low, uint32_t high) {
    this->lowFreqY = low;
    this->highFreqY = high;

    // Matches SetFilterYPassFrequency @ 00060224: only the Y poles filter is
    // updated, the low-pass biquad is intentionally left untouched.
    this->filterY.SetPassFilter(low, high);
    this->filterY.SetSamplingRate(this->samplingRate);
}

void DynamicBass::SetSamplingRate(uint32_t samplingRate) {
    this->samplingRate = samplingRate;
    this->filterX.SetSamplingRate(samplingRate);
    this->filterY.SetSamplingRate(samplingRate);
    this->lowPass.SetLowPassParameter(55.0f, samplingRate, this->qPeak / 666.0f + 0.5f);
}

void DynamicBass::SetSideGain(float gainX, float gainY) {
    this->sideGainX = gainX;
    this->sideGainY = gainY;
}
