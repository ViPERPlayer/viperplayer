#include "TubeSimulator.h"

// TubeSimulator::Reset @ 0006a06c
void TubeSimulator::Reset() {
    this->lowpassState[0] = 0.0;
    this->lowpassState[1] = 0.0;
    this->enable = false;
}

void TubeSimulator::SetEnable(bool enable) {
    if (this->enable != enable) {
        // Clear the filter state when turning the unit on so it does not
        // resume from a stale previous output.
        if (!this->enable) {
            Reset();
        }
        this->enable = enable;
    }
}

// TubeSimulator::TubeProcess @ 00069ff8
//
// Original inner loop (per stereo frame), with state at this+0 / this+4:
//   state0 = (state0 + L) >> 1; L = state0
//   state1 = (state1 + R) >> 1; R = state1
// i.e. a one-pole averaging lowpass per channel. Reproduced here in float.
void TubeSimulator::TubeProcess(float *buffer, uint32_t size) {
    if (!this->enable) return;

    for (uint32_t i = 0; i < size; i++) {
        this->lowpassState[0] = (this->lowpassState[0] + buffer[i * 2]) / 2.0;
        this->lowpassState[1] = (this->lowpassState[1] + buffer[i * 2 + 1]) / 2.0;
        buffer[i * 2] = (float) this->lowpassState[0];
        buffer[i * 2 + 1] = (float) this->lowpassState[1];
    }
}
