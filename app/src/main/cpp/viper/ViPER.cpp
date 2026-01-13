#include "ViPER.h"
#include <cstring>
#include <constants.h>
#include <log/log.h>

ViPER::ViPER() :
    adaptiveBuffer(2, 4096),
    waveBuffer(2, 4096),
    iirFilter(10),
    frameCount(0),
    samplingRate(VIPER_DEFAULT_SAMPLING_RATE),
    gainL(1.0),
    gainR(1.0) {

    this->convolver.SetEnable(false);
    this->convolver.SetSamplingRate(this->samplingRate);
    this->convolver.Reset();

    this->vhe.SetEnable(false);
    this->vhe.SetSamplingRate(this->samplingRate);
    this->vhe.Reset();

    this->viperDdc.SetEnable(false);
    this->viperDdc.SetSamplingRate(this->samplingRate);
    this->viperDdc.Reset();

    this->spectrumExtend.SetEnable(false);
    this->spectrumExtend.SetSamplingRate(this->samplingRate);
    this->spectrumExtend.SetReferenceFrequency(7600);
    this->spectrumExtend.SetExciter(0);
    this->spectrumExtend.Reset();

    this->iirFilter.SetEnable(false);
    this->iirFilter.SetSamplingRate(this->samplingRate);
    this->iirFilter.Reset();

    this->colorfulMusic.SetEnable(false);
    this->colorfulMusic.SetSamplingRate(this->samplingRate);
    this->colorfulMusic.Reset();

    this->reverberation.SetEnable(false);
    this->reverberation.Reset();

    this->playbackGain.SetEnable(false);
    this->playbackGain.SetSamplingRate(this->samplingRate);
    this->playbackGain.Reset();

    this->fetCompressor.SetParameter(FETCompressor::ENABLE, 0.0);
    this->fetCompressor.SetSamplingRate(this->samplingRate);
    this->fetCompressor.Reset();

    this->dynamicSystem.SetEnable(false);
    this->dynamicSystem.SetSamplingRate(this->samplingRate);
    this->dynamicSystem.Reset();

    this->viperBass.SetSamplingRate(this->samplingRate);
    this->viperBass.Reset();

    this->viperClarity.SetSamplingRate(this->samplingRate);
    this->viperClarity.Reset();

    this->diffSurround.SetEnable(false);
    this->diffSurround.SetSamplingRate(this->samplingRate);
    this->diffSurround.Reset();

    this->cure.SetEnable(false);
    this->cure.SetSamplingRate(this->samplingRate);
    this->cure.Reset();

    this->tubeSimulator.SetEnable(false);
    this->tubeSimulator.Reset();

    this->analogX.SetEnable(false);
    this->analogX.SetSamplingRate(this->samplingRate);
    this->analogX.SetProcessingModel(0);
    this->analogX.Reset();

    this->speakerCorrection.SetEnable(false);
    this->speakerCorrection.SetSamplingRate(this->samplingRate);
    this->speakerCorrection.Reset();

    for (auto &softwareLimiter: this->softwareLimiters) {
        softwareLimiter.Reset();
    }
}

void ViPER::process(float *buffer, uint32_t size) {
    uint32_t ret;
    float *tmpBuf;
    uint32_t tmpBufSize;

    if (this->convolver.GetEnabled() || this->vhe.GetEnabled()) {
//        ALOGD("Convolver or VHE is enable, use wave buffer");

        if (!this->waveBuffer.PushSamples(buffer, size)) {
            this->waveBuffer.Reset();
            return;
        }

        float *ptr = this->waveBuffer.GetBuffer();
        ret = this->convolver.Process(ptr, ptr, size);
        ret = this->vhe.Process(ptr, ptr, ret);
        this->waveBuffer.SetBufferOffset(ret);

        if (!this->adaptiveBuffer.PushZero(ret)) {
            this->waveBuffer.Reset();
            this->adaptiveBuffer.FlushBuffer();
            return;
        }

        ptr = this->adaptiveBuffer.GetBuffer();
        ret = this->waveBuffer.PopSamples(ptr, ret, true);
        this->adaptiveBuffer.SetBufferOffset(ret);

        tmpBuf = ptr;
        tmpBufSize = ret;
    } else {
//        ALOGD("Convolver and VHE are disabled, use adaptive buffer");

        if (this->adaptiveBuffer.PushFrames(buffer, size)) {
            this->adaptiveBuffer.SetBufferOffset(size);

            tmpBuf = this->adaptiveBuffer.GetBuffer();
            tmpBufSize = size;
        } else {
            this->adaptiveBuffer.FlushBuffer();
            return;
        }
    }

//    ALOGD("Process buffer size: %d", tmpBufSize);
    if (tmpBufSize != 0) {
        this->viperDdc.Process(tmpBuf, size);
        this->spectrumExtend.Process(tmpBuf, size);
        this->iirFilter.Process(tmpBuf, tmpBufSize);
        this->colorfulMusic.Process(tmpBuf, tmpBufSize);
        this->diffSurround.Process(tmpBuf, tmpBufSize);
        this->reverberation.Process(tmpBuf, tmpBufSize);
        this->speakerCorrection.Process(tmpBuf, tmpBufSize);
        this->playbackGain.Process(tmpBuf, tmpBufSize);
        this->fetCompressor.Process(tmpBuf, tmpBufSize); // TODO: enable check
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

    this->waveBuffer.Reset();

    this->convolver.SetSamplingRate(this->samplingRate);
    this->convolver.Reset();

    this->vhe.SetSamplingRate(this->samplingRate);
    this->vhe.Reset();

    this->viperDdc.SetSamplingRate(this->samplingRate);
    this->viperDdc.Reset();

    this->spectrumExtend.SetSamplingRate(this->samplingRate);
    this->spectrumExtend.Reset();

    this->iirFilter.SetSamplingRate(this->samplingRate);
    this->iirFilter.Reset();

    this->colorfulMusic.SetSamplingRate(this->samplingRate);
    this->colorfulMusic.Reset();

    this->reverberation.Reset();

    this->playbackGain.SetSamplingRate(this->samplingRate);
    this->playbackGain.Reset();

    this->fetCompressor.SetSamplingRate(this->samplingRate);
    this->fetCompressor.Reset();

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
