#pragma once

#include "CCombFilter.h"
#include "CAllpassFilter.h"

class CRevModel {
public:
    CRevModel();
    ~CRevModel();

    void Mute();
    void ProcessReplace(float *bufL, float *bufR, uint32_t size);
    void UpdateCoeffs();
    void Reset();
    void SetRoomSize(float roomSize);
    void SetDamp(float damp);
    void SetWet(float wet);
    void SetDry(float dry);
    void SetWidth(float width);
    void SetMode(float mode);
    float GetRoomSize();
    float GetDamp();
    float GetWet();
    float GetDry();
    float GetWidth();
    float GetMode();

private:
    float gain;
    float roomSize;
    float internalRoomSize;
    float damp;
    float internalDamp;
    float wet;
    // Stereo wet-mix coefficients derived from wet/width in UpdateCoeffs:
    //   wet1 = wet * (width/2 + 0.5)  (same-channel wet gain)
    //   wet2 = wet * (1 - width)/2    (cross-channel wet gain)
    float wet1;
    float wet2;
    float dry;
    float width;
    float mode;

    CCombFilter combL[8];
    CCombFilter combR[8];

    CAllpassFilter allpassL[4];
    CAllpassFilter allpassR[4];

    float *buffers[24];
};



