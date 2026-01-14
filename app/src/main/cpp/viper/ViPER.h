#pragma once

#include "utils/WaveBuffer.h"
#include "effects/SpectrumExtend.h"
#include "effects/Reverberation.h"
#include "effects/DynamicSystem.h"
#include "effects/ViPERClarity.h"
#include "effects/SpeakerCorrection.h"
#include "effects/AnalogX.h"
#include "effects/TubeSimulator.h"
#include "effects/Cure.h"
#include "effects/DiffSurround.h"
#include "utils/AdaptiveBuffer.h"
#include "effects/ViPERDDC.h"
#include "effects/IIRFilter.h"
#include "effects/ColorfulMusic.h"
#include "effects/ViPERBass.h"
#include "effects/SoftwareLimiter.h"
#include <array>

class ViPER {
public:
    ViPER(uint32_t samplingRate);

    void process(float *buffer, uint32_t size);
    void reset();
    void setSamplingRate(uint32_t samplingRate);
    void setGain(float gainL, float gainR);
    void setThresholdLimit(float thresholdLimit);

    // Effects
    AdaptiveBuffer adaptiveBuffer = AdaptiveBuffer(2, 4096);
    ViPERDDC viperDdc;
    SpectrumExtend spectrumExtend;
    IIRFilter iirFilter;
    ColorfulMusic colorfulMusic;
    Reverberation reverberation;
    DynamicSystem dynamicSystem;
    ViPERBass viperBass;
    ViPERClarity viperClarity;
    DiffSurround diffSurround;
    Cure cure;
    TubeSimulator tubeSimulator;
    AnalogX analogX;
    SpeakerCorrection speakerCorrection;

private:
    std::array<SoftwareLimiter, 2> softwareLimiters;
    uint32_t samplingRate;
    float gainL = 1;
    float gainR = 1;
};
