#include "ViPERClarity.h"

// Verified against ViPER4Android v2505 (ARM NEON decompilation).

ViPERClarity::ViPERClarity(uint32_t samplingRate) : noiseSharpening(samplingRate), hifi(samplingRate), samplingRate(samplingRate) {
    Reset();
}

void ViPERClarity::Process(float *samples, uint32_t size) {
    if (!this->enable) return;

    switch (this->processMode) {
        case ClarityMode::NATURAL: {
            this->noiseSharpening.Process(samples, size);
            break;
        }
        case ClarityMode::OZONE: {
            for (uint32_t i = 0; i < size * 2; i += 2) {
                samples[i] = (float) this->highShelf[0].Process(samples[i]);
                samples[i + 1] = (float) this->highShelf[1].Process(samples[i + 1]);
            }
            break;
        }
        case ClarityMode::XHIFI: {
            this->hifi.Process(samples, size);
            break;
        }
    }
}

void ViPERClarity::Reset() {
    this->noiseSharpening.SetSamplingRate(this->samplingRate);
    this->noiseSharpening.Reset();
    SetClarityToFilter();
    for (auto &highShelf : this->highShelf) {
        highShelf.SetFrequency(8250.0f); // 0x4600e800 = 8250.0
        // Matches the original ordering (SetFrequency -> SetQuality -> SetSamplingRate).
        // HighShelf stores the quality value but never reads it (SetSamplingRate only uses
        // frequency + gain), so this is functionally a no-op but kept for API fidelity.
        highShelf.SetQuality(100.0f); // 0x42c80000 = 100.0
        highShelf.SetSamplingRate(this->samplingRate);
    }
    this->hifi.SetSamplingRate(this->samplingRate);
    this->hifi.Reset();
}

void ViPERClarity::SetClarity(float gainPercent) {
    this->clarityGainPercent = gainPercent;
    if (this->processMode == ClarityMode::OZONE) {
        Reset();
    } else {
        SetClarityToFilter();
    }
}

void ViPERClarity::SetClarityToFilter() {
    // NoiseSharpening receives the raw clarity amount; the high-shelf / HiFi paths receive
    // clarity + 1.0 so that a clarity of 0 maps to unity gain (0 dB shelf, HiFi gain 1.0).
    this->noiseSharpening.SetGain(this->clarityGainPercent);
    this->highShelf[0].SetGain(this->clarityGainPercent + 1.0f);
    this->highShelf[1].SetGain(this->clarityGainPercent + 1.0f);
    this->hifi.SetClarity(this->clarityGainPercent + 1.0f);
}

void ViPERClarity::SetEnable(bool enable) {
    if (this->enable != enable) {
        if (enable) {
            Reset();
        }
        this->enable = enable;
    }
}

void ViPERClarity::SetProcessMode(ClarityMode processMode) {
    if (this->processMode != processMode) {
        this->processMode = processMode;
        Reset();
    }
}

void ViPERClarity::SetSamplingRate(uint32_t samplingRate) {
    if (this->samplingRate != samplingRate) {
        this->samplingRate = samplingRate;
        Reset();
    }
}
