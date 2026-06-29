#include <cstring>
#include "DiffSurround.h"

// Two mono FIFO buffers, one per channel, each pre-sized to 0x1000 frames
// (matches WaveBuffer_I32(1, 0x1000) in the original ctor).
DiffSurround::DiffSurround(uint32_t samplingRate) : buffers({
    WaveBuffer(1, 0x1000),
    WaveBuffer(1, 0x1000)
}), samplingRate(samplingRate) {
    Reset();
}

// Splits an interleaved stereo stream into two mono FIFOs and re-interleaves
// the FIFO outputs. Reset() primes the right FIFO with a delay's worth of
// zeros, so the right channel comes out delayed relative to the left,
// producing the difference-surround widening effect.
void DiffSurround::Process(float *samples, uint32_t size) {
    if (!this->enable) return;

    // Append `size` zero frames to the tail of each FIFO; the returned
    // pointers address that freshly-pushed (tail) region we write input into.
    float *leftIn = this->buffers[0].PushZerosGetBuffer(size);
    float *rightIn = this->buffers[1].PushZerosGetBuffer(size);

    for (uint32_t frame = 0; frame < size; frame++) {
        leftIn[frame] = samples[frame * 2];
        rightIn[frame] = samples[frame * 2 + 1];
    }

    // The FIFO head holds the (delayed) output frames to emit this block.
    float *leftOut = this->buffers[0].GetBuffer();
    float *rightOut = this->buffers[1].GetBuffer();

    for (uint32_t frame = 0; frame < size; frame++) {
        samples[frame * 2] = leftOut[frame];
        samples[frame * 2 + 1] = rightOut[frame];
    }

    // Consume the `size` frames we just emitted from the head of each FIFO.
    this->buffers[0].PopSamples(size, false);
    this->buffers[1].PopSamples(size, false);
}

void DiffSurround::Reset() {
    this->buffers[0].Reset();
    this->buffers[1].Reset();

    // Pre-fill the right channel FIFO with delayTime worth of silence so it
    // lags the left channel by that many frames.
    // 0x0005d878 = 1000.0 (ms per second divisor).
    this->buffers[1].PushZeros((uint32_t) ((double) this->delayTime / 1000.0 * (double) this->samplingRate));
}

void DiffSurround::SetDelayTime(float delayTime) {
    if (this->delayTime != delayTime) {
        this->delayTime = delayTime;
        this->Reset();
    }
}

void DiffSurround::SetEnable(bool enable) {
    if (this->enable != enable) {
        // Re-prime the delay buffers when transitioning from disabled to
        // enabled (matches the original: Reset() is only run on the
        // disabled->enabled edge).
        if (!this->enable) {
            Reset();
        }
        this->enable = enable;
    }
}

void DiffSurround::SetSamplingRate(uint32_t samplingRate) {
    if (this->samplingRate != samplingRate) {
        this->samplingRate = samplingRate;
        this->Reset();
    }
}
