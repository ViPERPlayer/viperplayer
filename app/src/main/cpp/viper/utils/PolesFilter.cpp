#include "PolesFilter.h"
#include <cstring>
#include <cmath>

PolesFilter::PolesFilter(uint32_t samplingRate) : samplingRate(samplingRate) {
    Reset();
}

void PolesFilter::Reset() {
    UpdateCoeff();
}

void PolesFilter::UpdateCoeff() {
    memset(&this->channels[0], 0, sizeof(ChannelState));
    memset(&this->channels[1], 0, sizeof(ChannelState));

    // Original PolesFilter::UpdateCoeff: real coeff = 2*sin(pi*f/fs) (Q25 in binary)
    float lc = (float) (2.0 * sin(M_PI * (double) this->lowCutFreq / (double) this->samplingRate));
    float hc = (float) (2.0 * sin(M_PI * (double) this->highCutFreq / (double) this->samplingRate));
    this->channels[0].lowPoleCoeff = this->channels[1].lowPoleCoeff = lc;
    this->channels[0].highPoleCoeff = this->channels[1].highPoleCoeff = hc;
}

static inline void DoFilterSide(ChannelState *channel, float inputSample, float *lowOut, float *highOut, float *bandOut) {
    float oldestSampleIn = channel->inputDelay[2];
    channel->inputDelay[2] = channel->inputDelay[1];
    channel->inputDelay[1] = channel->inputDelay[0];
    channel->inputDelay[0] = inputSample;

    channel->lowPole[0] += channel->lowPoleCoeff * (inputSample - channel->lowPole[0]);
    channel->lowPole[1] += channel->lowPoleCoeff * (channel->lowPole[0] - channel->lowPole[1]);
    channel->lowPole[2] += channel->lowPoleCoeff * (channel->lowPole[1] - channel->lowPole[2]);
    channel->lowPole[3] += channel->lowPoleCoeff * (channel->lowPole[2] - channel->lowPole[3]);

    channel->highPole[0] += channel->highPoleCoeff * (inputSample - channel->highPole[0]);
    channel->highPole[1] += channel->highPoleCoeff * (channel->highPole[0] - channel->highPole[1]);
    channel->highPole[2] += channel->highPoleCoeff * (channel->highPole[1] - channel->highPole[2]);
    channel->highPole[3] += channel->highPoleCoeff * (channel->highPole[2] - channel->highPole[3]);

    *lowOut = channel->lowPole[3];
    *highOut = oldestSampleIn - channel->highPole[3];
    *bandOut = channel->highPole[3] - channel->lowPole[3];
}

void PolesFilter::DoFilterLeft(float inputSample, float *lowOut, float *highOut, float *bandOut) {
    DoFilterSide(&this->channels[0], inputSample, lowOut, highOut, bandOut);
}

void PolesFilter::DoFilterRight(float inputSample, float *lowOut, float *highOut, float *bandOut) {
    DoFilterSide(&this->channels[1], inputSample, lowOut, highOut, bandOut);
}

void PolesFilter::SetPassFilter(uint32_t lowCutFreq, uint32_t highCutFreq) {
    this->lowCutFreq = lowCutFreq;
    this->highCutFreq = highCutFreq;
    UpdateCoeff();
}

void PolesFilter::SetSamplingRate(uint32_t samplingRate) {
    this->samplingRate = samplingRate;
    UpdateCoeff();
}