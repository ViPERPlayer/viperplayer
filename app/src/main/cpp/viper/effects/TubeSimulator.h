#pragma once

#include <cstdint>
#include <array>

// TubeSimulator @ 00069fe4 (ViPER4Android v2505)
//
// Despite the name, this is simply a per-channel one-pole averaging lowpass:
//   y[n] = (y[n-1] + x[n]) / 2
// applied independently to the left and right channels. The gentle high
// frequency rolloff is what stands in for "tube warmth".
//
// The original operates on interleaved fixed-point samples (TubeProcess(int*,
// int)) and halves via an arithmetic right shift (>> 1). Here the whole engine
// runs in float, so the same average is expressed as a division by 2.0; the
// recurrence is identical in the continuous domain.
//
// SetEnable() does not exist in the binary (the enable byte at this+8 was set
// directly by the owning ProcessUnit); it is added here so the JNI layer can
// toggle the unit, following the same "Reset() on disabled->enabled" idiom used
// by the other effect units.

class TubeSimulator {
public:
    void Reset();
    void SetEnable(bool enable);
    void TubeProcess(float *buffer, uint32_t size);

private:
    // Previous lowpass output per channel ({left, right}); this+0 and this+4 in
    // the original.
    std::array<double, 2> lowpassState = { 0.0, 0.0 };
    // Enable byte at this+8 in the original.
    bool enable = false;
};
