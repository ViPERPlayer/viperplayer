#include "SpeakerCorrection.h"

// Verified against ViPER4Android v2505 (libv4a_fx_jb_NEON.so).
// Topology per channel (SpeakerCorrection::Process @ 0x69e0c):
//   out = lowPass -> highPass -> (out / 2) ; out += bandPass(out)
// The original uses FixedBiquad (Q24 fixed-point) for low/band pass and a
// MultiBiquad for the high pass; this reconstruction uses the double-precision
// Biquad/MultiBiquad equivalents. The "/ 2" matches the Q24 halving
// ((x << 24 + 2^24) >> 25) in the original.

SpeakerCorrection::SpeakerCorrection(uint32_t samplingRate) : samplingRate(samplingRate) {
    Reset();
}

void SpeakerCorrection::Process(float *samples, uint32_t size) {
    if (!this->enable) return;

    for (uint32_t i = 0; i < size * 2; i += 2) {
        double outL = samples[i];
        outL = this->lowPass[0].ProcessSample(outL);
        outL = this->highPass[0].ProcessSample(outL);
        outL /= 2.0;
        outL += this->bandPass[0].ProcessSample(outL);
        samples[i] = (float) outL;

        double outR = samples[i + 1];
        outR = this->lowPass[1].ProcessSample(outR);
        outR = this->highPass[1].ProcessSample(outR);
        outR /= 2.0;
        outR += this->bandPass[1].ProcessSample(outR);
        samples[i + 1] = (float) outR;
    }
}

void SpeakerCorrection::Reset() {
    this->lowPass[0].Reset();
    this->lowPass[1].Reset();
    this->bandPass[0].Reset();
    this->bandPass[1].Reset();

    // High pass: 80 Hz, Q = 1.0, 0 dB gain (RefreshFilter type 1 = HIGH_PASS).
    // 0x42a00000 = 80.0f (freq), 0x3f800000 = 1.0f (Q), gain 0.0, octaves=false.
    this->highPass[0].RefreshFilter(MultiBiquad::FilterType::HIGH_PASS, 0.0, 80.0, this->samplingRate, 1.0, false);
    this->highPass[1].RefreshFilter(MultiBiquad::FilterType::HIGH_PASS, 0.0, 80.0, this->samplingRate, 1.0, false);
    // Low pass: 13500 Hz, Q = 1.0. 0x4652f000 = 13500.0f, 0x3f800000 = 1.0f.
    this->lowPass[0].SetLowPassParameter(13500.0, this->samplingRate, 1.0);
    this->lowPass[1].SetLowPassParameter(13500.0, this->samplingRate, 1.0);
    // Band pass: 420 Hz, Q = 3.88. 0x43d20000 = 420.0f, 0x407851ec = 3.88f.
    this->bandPass[0].SetBandPassParameter(420.0, this->samplingRate, 3.88);
    this->bandPass[1].SetBandPassParameter(420.0, this->samplingRate, 3.88);
}

void SpeakerCorrection::SetEnable(bool enable) {
    if (this->enable != enable) {
        if (enable) {
            Reset();
        }
        this->enable = enable;
    }
}

void SpeakerCorrection::SetSamplingRate(uint32_t samplingRate) {
    if (this->samplingRate != samplingRate) {
        this->samplingRate = samplingRate;
        Reset();
    }
}
