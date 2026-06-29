#include "ViPER.h"
#include <cstring>
#include <android/log.h>

ViPER::ViPER(uint32_t samplingRate) :
    viperDdc(samplingRate),
    convolver(),
    spectrumExtend(samplingRate),
    iirEqualizer(),
    colorfulMusic(samplingRate),
    fetCompressor(samplingRate),
    dynamicSystem(samplingRate),
    viperBass(samplingRate),
    viperClarity(samplingRate),
    diffSurround(samplingRate),
    cure(samplingRate),
    analogX(samplingRate),
    playbackGain(samplingRate),
    speakerCorrection(samplingRate),
    samplingRate(samplingRate) {

    this->viperDdc.SetSamplingRate(this->samplingRate);
    this->viperDdc.Reset();
    
    this->convolver.SetSamplingRate(this->samplingRate);
    this->convolver.Reset();

    this->vhe.SetSamplingRate(this->samplingRate);
    this->vhe.Reset();

    // ProcessUnit_FX::ProcessUnit_FX seeds SpectrumExtend with reference
    // frequency 0x1db0 = 7600 Hz.
    this->spectrumExtend.SetReferenceFrequency(7600);
    this->spectrumExtend.Reset();

    // Initialize equalizer with default 10 bands (IIRFilter::IIRFilter(10)).
    this->iirEqualizer.configure((int) samplingRate, viper::dsp::IIREqualizer::BandCount::BANDS_10);
}

void ViPER::process(float *buffer, uint32_t size) {
    if (size == 0) {
        return;
    }

    // Effect chain order is taken verbatim from
    // ProcessUnit_FX::processBuffer @ 00072460 for FX type 1 (the music /
    // headphone path, this+0x7c == 1). In the original the Convolver and VHE
    // run first through a resampling WaveBuffer stage; here we operate directly
    // on the interleaved float buffer, so they are simply applied in-place at
    // the head of the chain. SpeakerCorrection is intentionally absent: the
    // original only invokes it in FX type 2 (the speaker path), not in the
    // music path this engine reproduces.
    this->convolver.Process(buffer, buffer, size);
    this->vhe.Process(buffer, size);
    this->viperDdc.Process(buffer, size);
    this->spectrumExtend.Process(buffer, size);
    // IIRFilter::Process — interleaved stereo (channelCount = 2).
    this->iirEqualizer.process(buffer, size, 2);
    this->colorfulMusic.Process(buffer, size);
    this->diffSurround.Process(buffer, size);
    this->reverberation.Process(buffer, size);
    this->playbackGain.Process(buffer, size);
    // FETCompressor gates itself internally on its own enable flag (mirrors the
    // original's this+0x88 guard around FETCompressor::Process).
    this->fetCompressor.Process(buffer, size);
    this->dynamicSystem.Process(buffer, size);
    this->viperBass.Process(buffer, size);
    this->viperClarity.Process(buffer, size);
    this->cure.Process(buffer, size);
    this->tubeSimulator.TubeProcess(buffer, size);
    this->analogX.Process(buffer, size);

    // Master output gain: corresponds to AdaptiveBuffer_FPI32::ScaleFrames /
    // PanFrames in the original, applied after the chain and before the limiter.
    // The original keeps these in Q25 (unity = 0x2000000 = 2^25); here unity is
    // simply 1.0f in the float domain.
    if (this->leftGain != 1.0f || this->rightGain != 1.0f) {
        for (uint32_t i = 0; i < size * 2; i += 2) {
            buffer[i] *= this->leftGain;
            buffer[i + 1] *= this->rightGain;
        }
    }

    // SoftwareLimiter on each channel, always applied (final stage).
    for (uint32_t i = 0; i < size * 2; i += 2) {
        buffer[i] = this->softwareLimiters[0].Process(buffer[i]);
        buffer[i + 1] = this->softwareLimiters[1].Process(buffer[i + 1]);
    }
}

void ViPER::reset() {
    this->viperDdc.Reset();
    this->convolver.Reset();
    this->vhe.Reset();
    this->spectrumExtend.Reset();
    this->iirEqualizer.reset();
    this->colorfulMusic.Reset();
    this->reverberation.Reset();
    this->fetCompressor.Reset();
    this->dynamicSystem.Reset();
    this->viperBass.Reset();
    this->viperClarity.Reset();
    this->diffSurround.Reset();
    this->cure.Reset();
    this->tubeSimulator.Reset();
    this->analogX.Reset();
    this->playbackGain.Reset();
    this->speakerCorrection.Reset();
    for (auto &softwareLimiter: softwareLimiters) {
        softwareLimiter.Reset();
    }
}

void ViPER::setSamplingRate(uint32_t samplingRate) {
    // Mirrors ProcessUnit_FX::ResetAllEffects @ 000703b0: push the new sampling
    // rate to every effect that depends on it, then reset the whole chain.
    this->samplingRate = samplingRate;

    this->convolver.SetSamplingRate(samplingRate);
    this->vhe.SetSamplingRate(samplingRate);
    this->viperDdc.SetSamplingRate(samplingRate);
    this->spectrumExtend.SetSamplingRate(samplingRate);
    this->colorfulMusic.SetSamplingRate(samplingRate);
    this->fetCompressor.SetSamplingRate(samplingRate);
    this->dynamicSystem.SetSamplingRate(samplingRate);
    this->viperBass.SetSamplingRate(samplingRate);
    this->viperClarity.SetSamplingRate(samplingRate);
    this->diffSurround.SetSamplingRate(samplingRate);
    this->cure.SetSamplingRate(samplingRate);
    this->analogX.SetSamplingRate(samplingRate);
    this->playbackGain.SetSamplingRate(samplingRate);
    this->speakerCorrection.SetSamplingRate(samplingRate);

    // IIREqualizer has no rate-only setter; re-configure it for the new rate,
    // preserving the currently selected band count.
    viper::dsp::IIREqualizer::BandCount bandCount;
    switch (this->iirEqualizer.getBandCount()) {
        case 15: bandCount = viper::dsp::IIREqualizer::BandCount::BANDS_15; break;
        case 31: bandCount = viper::dsp::IIREqualizer::BandCount::BANDS_31; break;
        case 10:
        default: bandCount = viper::dsp::IIREqualizer::BandCount::BANDS_10; break;
    }
    this->iirEqualizer.configure((int) samplingRate, bandCount);

    this->reverberation.SetSamplingRate(samplingRate);

    this->reset();
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
