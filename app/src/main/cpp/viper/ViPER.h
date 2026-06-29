#pragma once

#include "utils/WaveBuffer.h"
#include "effects/SpectrumExtend.h"
#include "effects/Reverberation.h"
#include "effects/DynamicSystem.h"
#include "effects/FETCompressor.h"
#include "effects/VHE.h"
#include "effects/ViPERClarity.h"
#include "effects/SpeakerCorrection.h"
#include "effects/AnalogX.h"
#include "effects/TubeSimulator.h"
#include "effects/Cure.h"
#include "effects/DiffSurround.h"
#include "utils/AdaptiveBuffer.h"
#include "effects/ViPERDDC.h"
#include "dsp/Convolver.h"
#include "dsp/IIREqualizer.h"
#include "effects/ColorfulMusic.h"
#include "effects/ViPERBass.h"
#include "effects/SoftwareLimiter.h"
#include "effects/PlaybackGain.h"
#include <array>

class ViPER {
public:
    ViPER(uint32_t samplingRate);

    void process(float *buffer, uint32_t size);
    void reset();
    void setSamplingRate(uint32_t samplingRate);
    uint32_t getSamplingRate() const { return samplingRate; }
    void setGain(float gainL, float gainR);
    void setThresholdLimit(float thresholdLimit);

    // Effects
    ViPERDDC viperDdc;
    viper::dsp::Convolver convolver;
    VHE vhe;
    SpectrumExtend spectrumExtend;
    viper::dsp::IIREqualizer iirEqualizer;
    ColorfulMusic colorfulMusic;
    Reverberation reverberation;
    FETCompressor fetCompressor;
    DynamicSystem dynamicSystem;
    ViPERBass viperBass;
    ViPERClarity viperClarity;
    DiffSurround diffSurround;
    Cure cure;
    TubeSimulator tubeSimulator;
    AnalogX analogX;
    viper::effects::PlaybackGain playbackGain; // Added
    SpeakerCorrection speakerCorrection;

private:
    std::vector<SoftwareLimiter> softwareLimiters = {SoftwareLimiter(), SoftwareLimiter()}; // Changed to std::vector and initialized
    uint32_t samplingRate;
    float leftGain = 1;
    float rightGain = 1;
};
