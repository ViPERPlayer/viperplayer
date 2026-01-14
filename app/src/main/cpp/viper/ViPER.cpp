#include "ViPER.h"
#include <cstring>

#define DEFAULT_SAMPLING_RATE 44100

ViPER::ViPER(uint32_t samplingRate) :
    viperDdc(samplingRate),
    spectrumExtend(samplingRate),
    iirFilter(samplingRate, 10),
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

    this->spectrumExtend.SetReferenceFrequency(7600);
    this->spectrumExtend.Reset();
}

void ViPER::process(float *buffer, uint32_t size) {
    uint32_t ret;
    float *tmpBuf;
    uint32_t tmpBufSize;

    if (this->adaptiveBuffer.PushFrames(buffer, size)) {
        this->adaptiveBuffer.SetBufferOffset(size);

        tmpBuf = this->adaptiveBuffer.GetBuffer();
        tmpBufSize = size;
    } else {
        this->adaptiveBuffer.FlushBuffer();
        return;
    }

    if (tmpBufSize != 0) {
        this->viperDdc.Process(tmpBuf, size);
        this->spectrumExtend.Process(tmpBuf, size);
        this->iirFilter.Process(tmpBuf, tmpBufSize);
        this->colorfulMusic.Process(tmpBuf, tmpBufSize);
        this->diffSurround.Process(tmpBuf, tmpBufSize);
        this->reverberation.Process(tmpBuf, tmpBufSize);
        this->speakerCorrection.Process(tmpBuf, tmpBufSize);
        this->dynamicSystem.Process(tmpBuf, tmpBufSize);
        this->viperBass.Process(tmpBuf, tmpBufSize);
        this->viperClarity.Process(tmpBuf, tmpBufSize);
        this->cure.Process(tmpBuf, tmpBufSize);
        this->tubeSimulator.TubeProcess(tmpBuf, size);
        this->analogX.Process(tmpBuf, tmpBufSize);

        if (this->gainL != 1.0f || this->gainR != 1.0f) {
            this->adaptiveBuffer.SetGain(this->gainL, this->gainR);
        }

        for (uint32_t i = 0; i < tmpBufSize * 2; i += 2) {
            tmpBuf[i] = this->softwareLimiters[0].Process(tmpBuf[i]);
            tmpBuf[i + 1] = this->softwareLimiters[1].Process(tmpBuf[i + 1]);
        }

        if (!this->adaptiveBuffer.PopFrames(buffer, tmpBufSize)) {
            this->adaptiveBuffer.FlushBuffer();
            return;
        }

        if (size <= tmpBufSize) {
            return;
        }
    }

    memmove(buffer + (size - tmpBufSize) * 2, buffer, tmpBufSize * sizeof(float));
    memset(buffer, 0, (size - tmpBufSize) * sizeof(float));
}

void ViPER::reset() {
    this->adaptiveBuffer.FlushBuffer();

    this->viperDdc.SetSamplingRate(this->samplingRate);
    this->viperDdc.Reset();

    this->spectrumExtend.SetSamplingRate(this->samplingRate);
    this->spectrumExtend.Reset();

    this->iirFilter.SetSamplingRate(this->samplingRate);
    this->iirFilter.Reset();

    this->colorfulMusic.SetSamplingRate(this->samplingRate);
    this->colorfulMusic.Reset();

    this->reverberation.Reset();

    this->dynamicSystem.SetSamplingRate(this->samplingRate);
    this->dynamicSystem.Reset();

    this->viperBass.SetSamplingRate(this->samplingRate);
    this->viperBass.Reset();

    this->viperClarity.SetSamplingRate(this->samplingRate);
    this->viperClarity.Reset();

    this->diffSurround.SetSamplingRate(this->samplingRate);
    this->diffSurround.Reset();

    this->cure.SetSamplingRate(this->samplingRate);
    this->cure.Reset();

    this->tubeSimulator.Reset();

    this->analogX.SetSamplingRate(this->samplingRate);
    this->analogX.Reset();

    this->speakerCorrection.SetSamplingRate(this->samplingRate);
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
    this->gainL = gainL;
    this->gainR = gainR;
}

void ViPER::setThresholdLimit(float thresholdLimit) {
    for (auto &softwareLimiter: softwareLimiters) {
        softwareLimiter.SetGate(thresholdLimit);
    }
}
