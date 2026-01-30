#include "ViPER.h"
#include <cstring>
#include <android/log.h>

ViPER::ViPER(uint32_t samplingRate) :
    viperDdc(samplingRate),
    convolver(),
    spectrumExtend(samplingRate),
    iirEqualizer(),
    colorfulMusic(samplingRate),
    dynamicSystem(samplingRate),
    viperBass(samplingRate),
    viperClarity(samplingRate),
    diffSurround(samplingRate),
    cure(samplingRate),
    analogX(samplingRate),
    speakerCorrection(samplingRate),
    samplingRate(samplingRate) {

    this->viperDdc.SetSamplingRate(this->samplingRate);
    this->viperDdc.Reset();
    
    this->convolver.SetSamplingRate(this->samplingRate);
    this->convolver.Reset();

    this->spectrumExtend.SetReferenceFrequency(7600);
    this->spectrumExtend.Reset();
    
    // Initialize equalizer with default 10 bands
    this->iirEqualizer.configure(samplingRate, viper::dsp::IIREqualizer::BandCount::BANDS_10);
}

void ViPER::process(float *buffer, uint32_t size) {
    uint32_t ret;

    if (size != 0) {
        this->viperDdc.Process(buffer, size);
        this->convolver.Process(buffer, buffer, size);
        this->spectrumExtend.Process(buffer, size);
        
        // IIR EQ Process - updated for new API (interleaved stereo = 2 channels)
        // Note: The buffer here is stereo interleaved float.
        this->iirEqualizer.process(buffer, size, 2);
        
        this->colorfulMusic.Process(buffer, size);
        this->diffSurround.Process(buffer, size);
        this->reverberation.Process(buffer, size);
        this->speakerCorrection.Process(buffer, size);
        this->dynamicSystem.Process(buffer, size);
        this->viperBass.Process(buffer, size);
        this->viperClarity.Process(buffer, size);
        this->cure.Process(buffer, size);
        this->tubeSimulator.TubeProcess(buffer, size);
        this->analogX.Process(buffer, size);

        if (this->leftGain != 1.0f || this->rightGain != 1.0f) {
            for (uint32_t i = 0; i < size * 2; i += 2) {
                buffer[i] *= this->leftGain;
                buffer[i + 1] *= this->rightGain;
            }
        }

        for (uint32_t i = 0; i < size * 2; i += 2) {
            buffer[i] = this->softwareLimiters[0].Process(buffer[i]);
            buffer[i + 1] = this->softwareLimiters[1].Process(buffer[i + 1]);
        }
    }
}

void ViPER::reset() {
    this->viperDdc.Reset();
    this->convolver.Reset();
    this->spectrumExtend.Reset();
    this->iirEqualizer.reset();
    this->colorfulMusic.Reset();
    this->reverberation.Reset();
    this->dynamicSystem.Reset();
    this->viperBass.Reset();
    this->viperClarity.Reset();
    this->diffSurround.Reset();
    this->cure.Reset();
    this->tubeSimulator.Reset();
    this->analogX.Reset();
    this->speakerCorrection.Reset();
    for (auto &softwareLimiter: softwareLimiters) {
        softwareLimiter.Reset();
    }
}

void ViPER::setSamplingRate(uint32_t samplingRate) {
    this->samplingRate = samplingRate;
    // TODO: Set sampling rate to all other effects
}

void ViPER::setGain(float gainL, float gainR) {
    this->leftGain = gainL;
    this->rightGain = gainR;
}

void ViPER::setThresholdLimit(float thresholdLimit) {
    for (auto &softwareLimiter: softwareLimiters) {
        softwareLimiter.SetGate(thresholdLimit);
    }
}
