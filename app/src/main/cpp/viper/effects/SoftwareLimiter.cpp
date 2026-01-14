#include <cstring>
#include <cmath>
#include "SoftwareLimiter.h"

#ifndef uint
#define uint unsigned int
#endif

SoftwareLimiter::SoftwareLimiter() {
    this->limitingActive = false;
    this->writePos = 0;
    this->gainEnvelope = 1.0;
    this->gate = 0.999999;
    this->envelopeMemory = 1.0;
    this->targetGain = 1.0;

    Reset();
}

float SoftwareLimiter::Process(float sample) {
    bool bVar1;
    uint uVar3;
    uint uVar4;
    int iVar5;
    uint uVar6;
    uint uVar8;
    float fVar9;
    float fVar10;
    float abs_sample;

    abs_sample = std::abs(sample);
    if (abs_sample < this->gate) {
        if (this->limitingActive) goto LAB_0006d86c;
        uVar8 = this->writePos;
    }
    else {
        if (!this->limitingActive) {
            memset(this->peakTree, 0, sizeof(this->peakTree));
            this->limitingActive = true;
        }
LAB_0006d86c:
        uVar3 = 8;
        uVar8 = this->writePos;
        uVar4 = uVar8;
        do {
            iVar5 = 2 << (uVar3 & 0xff);
            uVar6 = uVar4 ^ 1;
            this->peakTree[512 + uVar4 - iVar5] = abs_sample;
            uVar4 = uVar4 / 2;
            if (abs_sample < this->peakTree[512 + uVar6 - iVar5]) {
                abs_sample = this->peakTree[512 + uVar6 - iVar5];
            }
            uVar3 = uVar3 - 1;
        } while (uVar3 != 0);
        if (this->gate < abs_sample) {
            bVar1 = this->limitingActive;
            fVar10 = this->targetGain;
            uVar4 = (uVar8 + 1) % 256;
            this->lookaheadBuffer[uVar8] = sample;
            this->writePos = uVar4;
            if (bVar1) {
                fVar10 = this->gate / abs_sample;
            }
            abs_sample = this->lookaheadBuffer[uVar4];
            goto LAB_0006d8fc;
        }
        this->limitingActive = false;
    }
    fVar10 = this->targetGain;
    this->lookaheadBuffer[uVar8] = sample;
    uVar8 = (uVar8 + 1) % 256;
    this->writePos = uVar8;
    abs_sample = this->lookaheadBuffer[uVar8];
LAB_0006d8fc:
    fVar9 = this->gainEnvelope * 0.9999 + 0.0001;
    fVar10 = fVar10 * 0.0999 + this->envelopeMemory * 0.8999;
    bVar1 = fVar10 < fVar9;
    this->envelopeMemory = fVar10;
    if (bVar1) {
        fVar9 = fVar10;
    }
    if (bVar1) {
        this->gainEnvelope = fVar10;
    }
    if (!bVar1) {
        this->gainEnvelope = fVar9;
    }
    fVar9 = abs_sample * fVar9;
    fVar10 = std::abs(fVar9);
    if (this->gate <= fVar10) {
        fVar9 = this->gate / std::abs(abs_sample);
    }
    if (this->gate <= fVar10) {
        this->gainEnvelope = fVar9;
        fVar9 = abs_sample * fVar9;
    }
    return fVar9;
}

void SoftwareLimiter::Reset() {
    memset(this->lookaheadBuffer, 0, sizeof(this->lookaheadBuffer));
    memset(this->peakTree, 0, sizeof(this->peakTree));
    this->limitingActive = false;
    this->writePos = 0;
    this->gainEnvelope = 1.0;
    this->envelopeMemory = 1.0;
}

void SoftwareLimiter::SetGate(float gate) {
    this->gate = gate;
}
