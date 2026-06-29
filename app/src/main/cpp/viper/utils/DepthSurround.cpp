#include "DepthSurround.h"
#include <cmath>

DepthSurround::DepthSurround(uint32_t samplingRate) {
    this->SetSamplingRate(samplingRate);
    this->RefreshStrength();
}

void DepthSurround::Process(float *samples, uint32_t size) {
    if (this->enabled) {
        if (!this->strengthAtLeast500) {
            for (uint32_t i = 0; i < size; i++) {
                float sampleLeft = samples[2 * i];
                float sampleRight = samples[2 * i + 1];

                // Both cross-feedback delay inputs are formed from the previous
                // iteration's feedback samples (the original loads prev[0] and
                // prev[1] before processing either delay line).
                float prevLeft = this->prev[0];
                float prevRight = this->prev[1];
                this->prev[0] = this->gain * this->timeConstDelay[0].ProcessSample(sampleLeft + prevRight);
                this->prev[1] = this->gain * this->timeConstDelay[1].ProcessSample(sampleRight + prevLeft);

                float l = this->prev[0] + sampleLeft;
                float r = this->prev[1] + sampleRight;

                float diff = (l - r) / 2.f;
                float avg = (l + r) / 2.f;
                float avgOut = (float) this->highpass.ProcessSample(diff);
                samples[2 * i] = avg + (diff - avgOut);
                samples[2 * i + 1] = avg - (diff - avgOut);
            }
        } else {
            for (uint32_t i = 0; i < size; i++) {
                float sampleLeft = samples[2 * i];
                float sampleRight = samples[2 * i + 1];

                // Same as above, but the right feedback channel is inverted when
                // strength >= 500 (the original negates the second delay output).
                float prevLeft = this->prev[0];
                float prevRight = this->prev[1];
                this->prev[0] = this->gain * this->timeConstDelay[0].ProcessSample(sampleLeft + prevRight);
                this->prev[1] = -this->gain * this->timeConstDelay[1].ProcessSample(sampleRight + prevLeft);

                float l = this->prev[0] + sampleLeft;
                float r = this->prev[1] + sampleRight;

                float diff = (l - r) / 2.f;
                float avg = (l + r) / 2.f;
                float avgOut = (float) this->highpass.ProcessSample(diff);
                samples[2 * i] = avg + (diff - avgOut);
                samples[2 * i + 1] = avg - (diff - avgOut);
            }
        }
    }
}

void DepthSurround::RefreshStrength() {
    this->strengthAtLeast500 = this->strength >= 500;
    this->enabled = this->strength != 0;
    if (this->strength != 0) {
        // gain = 10 ^ (((strength / 1000) * 10 - 15) / 20)
        // base 10.0, divisor 1000.0 (// 0x5fe30 = 1000.0), 10.0/15.0/20.0 immediates.
        float gain = (float) pow(10.0, ((this->strength / 1000.0) * 10.0 - 15.0) / 20.0);
        // The original stores gain in Q6.25 fixed point (1.0 == 2^25, // 0x5fe28
        // (double) = 33554432.0 = 2^25) and saturates the int32 conversion at
        // INT_MAX, i.e. an effective gain ceiling of (2^31-1)/2^25 (~63.99999997).
        // It does NOT clamp at 1.0.
        constexpr float gainCeiling = 2147483647.0f / 33554432.0f;
        if (gain > gainCeiling) {
            gain = gainCeiling;
        }
        this->gain = gain;
    } else {
        this->gain = 0.0;
    }
}

void DepthSurround::SetSamplingRate(uint32_t samplingRate) {
    this->timeConstDelay[0].SetParameters(samplingRate, 0.02);   // 0x3ca3d70a = 0.02f
    this->timeConstDelay[1].SetParameters(samplingRate, 0.014);  // 0x3c656042 = 0.014f
    // 0x44480000 = 800.0f, 0xc1300000 = -11.0f, 0x3f3851ec = 0.72f, last arg 0.0
    this->highpass.SetHighPassParameter(800.0f, samplingRate, -11.0f, 0.72f, 0.0);
    for (auto &prev : this->prev) {
        prev = 0.0f;
    }
}

void DepthSurround::SetStrength(short strength) {
    this->strength = strength;
    this->RefreshStrength();
}