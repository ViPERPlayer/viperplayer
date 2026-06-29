#include "ViPERBass.h"
#include <cmath>

// Reconstructed from ViPER4Android v2505 (ViPERBass @ 0x0006bf00 .. 0x0006c318).
// The original engine is fixed-point (int* Q25 samples, FixedBiquad, WaveBuffer_I32);
// this is a math-equivalent float port. All scalars below are taken verbatim from
// the binary's data/literal pool.

ViPERBass::ViPERBass(uint32_t samplingRate)
        : polyphase(samplingRate, 2),
          subwoofer(samplingRate),
          waveBuffer(1, 4096), // 0x6c024: WaveBuffer_I32(channels=1, frames=0x1000)
          samplingRate(samplingRate),
          samplingRatePeriod(1.0f / (float) samplingRate) {
    Reset();
}

void ViPERBass::Process(float *samples, uint32_t size) {
    if (!this->enable) return;
    if (size == 0) return;

    // Anti-pop volume ramp: while antiPop < 1.0, attenuate every frame and ramp
    // antiPop towards 1.0 by samplingRatePeriod (== 1/samplingRate) per sample, so
    // it reaches unity after one second. Matches the fixed-point ramp at 0x6c318
    // (antiPop and step held in Q25; step clamped at 0x2000000 == 1.0).
    if (this->antiPop < 1.0f) {
        for (uint32_t i = 0; i < size * 2; i += 2) {
            samples[i] *= this->antiPop;
            samples[i + 1] *= this->antiPop;

            float x = this->antiPop + this->samplingRatePeriod;
            if (x > 1.0f) x = 1.0f;
            this->antiPop = x;
        }
    }

    switch (this->processMode) {
        case ProcessMode::NATURAL_BASS: {
            // Single biquad on the (L+R)/2 mono mix, same bass added to both channels.
            for (uint32_t i = 0; i < size * 2; i += 2) {
                float mono = (samples[i] + samples[i + 1]) * 0.5f;
                float bass = (float) this->biquad.ProcessSample(mono) * this->bassFactor;
                samples[i] += bass;
                samples[i + 1] += bass;
            }
            break;
        }
        case ProcessMode::PURE_BASS_PLUS: {
            // Mono delay buffer storing the mono-mixed filtered bass, aligned with the
            // polyphase group delay so the added bass stays phase-locked to the program.
            if (this->waveBuffer.PushSamples(samples, size)) {
                float *buffer = this->waveBuffer.GetBuffer();
                uint32_t off = this->waveBuffer.GetBufferOffset(); // flat == frame for mono

                for (uint32_t k = 0; k < size; k++) {
                    float mono = (samples[2 * k] + samples[2 * k + 1]) * 0.5f;
                    buffer[off - size + k] = (float) this->biquad.ProcessSample(mono);
                }

                if (this->polyphase.Process(samples, size) == size) {
                    for (uint32_t k = 0; k < size; k++) {
                        float bass = buffer[k] * this->bassFactor;
                        samples[2 * k] += bass;
                        samples[2 * k + 1] += bass;
                    }
                    this->waveBuffer.PopSamples(size, true);
                }
            }
            break;
        }
        case ProcessMode::SUBWOOFER: {
            this->subwoofer.Process(samples, size);
            break;
        }
    }
}

void ViPERBass::Reset() {
    this->polyphase.SetSamplingRate(this->samplingRate);
    this->polyphase.Reset();
    this->waveBuffer.Reset();
    this->waveBuffer.PushZeros(this->polyphase.GetLatency());
    this->subwoofer.SetBassGain(this->samplingRate, this->bassFactor);
    this->biquad.SetLowPassParameter((float) this->speaker, this->samplingRate, 0.53f); // 0.53 = low-pass Q/bandwidth (code literal)
    this->samplingRatePeriod = 1.0f / (float) this->samplingRate;
    this->antiPop = 0.0f;
}

void ViPERBass::SetBassFactor(float bassFactor) {
    // 0x6c2a0: native ignores changes whose magnitude is <= 0.1 (deadband),
    // otherwise it requantises and reconfigures the subwoofer gain.
    // 0x0006c310 = 0.1
    if (std::fabs(this->bassFactor - bassFactor) <= 0.1f) return;
    this->bassFactor = bassFactor;
    this->subwoofer.SetBassGain(this->samplingRate, this->bassFactor);
}

void ViPERBass::SetEnable(bool enable) {
    if (this->enable != enable) {
        if (enable) Reset();
        this->enable = enable;
    }
}

void ViPERBass::SetProcessMode(ProcessMode processMode) {
    if (this->processMode != processMode) {
        this->processMode = processMode;
        Reset();
    }
}

void ViPERBass::SetSamplingRate(uint32_t samplingRate) {
    if (this->samplingRate != samplingRate) {
        this->samplingRate = samplingRate;
        this->samplingRatePeriod = 1.0f / (float) samplingRate;
        this->polyphase.SetSamplingRate(this->samplingRate);
        this->biquad.SetLowPassParameter((float) this->speaker, this->samplingRate, 0.53f);
        this->subwoofer.SetBassGain(this->samplingRate, this->bassFactor);
    }
}

void ViPERBass::SetSpeaker(uint32_t speaker) {
    if (this->speaker != speaker) {
        this->speaker = speaker;
        this->biquad.SetLowPassParameter((float) this->speaker, this->samplingRate, 0.53f);
    }
}
