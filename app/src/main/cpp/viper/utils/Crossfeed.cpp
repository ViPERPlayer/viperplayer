#include <cstring>
#include <cmath>
#include "Crossfeed.h"

// Basically Bauer-to-Stereophonic Binaural filter
// See: http://bs2b.sourceforge.net/

Crossfeed::Crossfeed(uint32_t samplingRate) : samplingRate(samplingRate) {
    this->a0_lo = 0.f;
    this->b1_lo = 0.f;
    this->a0_hi = 0.f;
    this->a1_hi = 0.f;
    this->b1_hi = 0.f;
    this->gain = 0.f;
    memset(&this->lfs, 0, 6 * sizeof(float));
    // Default preset packed as 0x2d02bc: low 16 bits = cutoff (0x2bc = 700 Hz),
    // high 16 bits = feedback (0x2d = 45, i.e. 4.5 in tenths-of-dB units).
    this->preset.cutoff = 700;
    this->preset.feedback = 45;
    Reset();
}

#define lo_filter(in, out_1) (this->a0_lo * (in) + this->b1_lo * (out_1))
#define hi_filter(in, in_1, out_1) (this->a0_hi * (in) + this->a1_hi * (in_1) + this->b1_hi * (out_1))

void Crossfeed::FilterSample(float *sample) {
    this->lfs.lo[0] = lo_filter(sample[0], this->lfs.lo[0]);
    this->lfs.lo[1] = lo_filter(sample[1], this->lfs.lo[1]);

    this->lfs.hi[0] = hi_filter(sample[0], this->lfs.asis[0], this->lfs.hi[0]);
    this->lfs.hi[1] = hi_filter(sample[1], this->lfs.asis[1], this->lfs.hi[1]);
    this->lfs.asis[0] = sample[0];
    this->lfs.asis[1] = sample[1];

    sample[0] = (this->lfs.hi[0] + this->lfs.lo[1]) * this->gain;
    sample[1] = (this->lfs.hi[1] + this->lfs.lo[0]) * this->gain;
}

uint16_t Crossfeed::GetCutoff() {
    return this->preset.cutoff;
}

float Crossfeed::GetFeedback() {
    return (float) this->preset.feedback / 10.0f;
}

float Crossfeed::GetLevelDelay() {
    // Original: `if (cutoff - 300u < 0x6a5)` -> valid range is [300, 2000] inclusive.
    uint16_t cutoff = this->preset.cutoff;
    if (cutoff >= 300 && cutoff <= 2000) {
        // 0x0006e8e4 = 18700.0f
        return (float) ((18700.0 / (double) cutoff) * 10.0);
    } else {
        // 0x0006e8e8 = 0.0f
        return 0.0f;
    }
}

struct Crossfeed::Preset Crossfeed::GetPreset() {
    return this->preset;
}

void Crossfeed::ProcessFrames(float *buffer, uint32_t size) {
    for (uint32_t i = 0; i < size * 2; i += 2) {
        FilterSample(buffer + i);
    }
}

void Crossfeed::Reset() {
    // The original ARM build stores the five filter coefficients and the gain as
    // Q25 fixed-point integers (coeff = round(value * 0x0006e800), where
    // 0x0006e800 = 2^25 = 33554432.0) and applies them with a 64-bit multiply
    // followed by `(x + 2^24) >> 25`. This port keeps them as float; the
    // coefficient math below is identical (bs2b), only the quantization differs.
    uint16_t cutoff = this->preset.cutoff;
    double level = this->preset.feedback / 10.0; // 45 -> 4.5 (keep the fraction)

    double GB_lo = level * -5.0 / 6.0 - 3.0;
    double GB_hi = level / 6.0 - 3.0;

    double G_lo = pow(10, GB_lo / 20.0);
    double G_hi = 1.0 - pow(10, GB_hi / 20.0);
    double Fc_hi = cutoff * pow(2.0, (GB_lo - 20.0 * log10(G_hi)) / 12.0);

    double x = exp(-2.0 * M_PI * cutoff / this->samplingRate);
    this->b1_lo = (float) x;
    this->a0_lo = (float) (G_lo * (1.0 - x));

    x = exp(-2.0 * M_PI * Fc_hi / this->samplingRate);
    this->b1_hi = (float) x;
    this->a0_hi = (float) (1.0 - G_hi * (1.0 - x));
    this->a1_hi = (float) -x;

    this->gain = (float) (1.0 / (1.0 - G_hi + G_lo));
    memset(&this->lfs, 0, 6 * sizeof(float));
}

void Crossfeed::SetCutoff(uint16_t cutoff) {
    this->preset.cutoff = cutoff;
    Reset();
}

void Crossfeed::SetFeedback(float feedback) {
    this->preset.feedback = (uint16_t) (feedback * 10.0f);
    Reset();
}

void Crossfeed::SetPreset(struct Crossfeed::Preset preset) {
    this->preset = preset;
    Reset();
}

void Crossfeed::SetSamplingRate(uint32_t samplingRate) {
    if (this->samplingRate != samplingRate) {
        this->samplingRate = samplingRate;
        Reset();
    }
}
