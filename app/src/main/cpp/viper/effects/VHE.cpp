#include "VHE.h"
#include <cstring>

// Real per-(level,rate,ear) HRIR/BRIR kernel blobs, extracted bit-exact from the
// ViPER4Android v2505 .so .rodata (the DAT_* pointers VHE::Reset loads). Indexed
// kVheKernelBits[level*4 + rateIndex*2 + ear]; rateIndex 0 = 44100, 1 = 48000;
// ear 0 = left, 1 = right.
#include "VHEKernels.inc"

// Per-level / per-rate make-up gain. These are the float immediates ("iVar3")
// VHE::Reset passes to PConvSingle_F32::LoadKernel, which bakes the gain into the
// kernel at load time. Recovered exactly from their IEEE-754 bit patterns; the
// same gain is applied to both ears. [level][rateIndex], rateIndex 0 = 44100 Hz,
// 1 = 48000 Hz.
const float VHE::kEffectGain[5][2] = {
        // 0x403c8a72 = 2.9459500 (both rates)
        {2.9459500f, 2.9459500f},
        // 0x3f71adfb = 0.9440610 (both rates)
        {0.9440610f, 0.9440610f},
        // 0x3fc5b4dd = 1.5445820 / 0x3fc408b7 = 1.5315160
        {1.5445820f, 1.5315160f},
        // 0x3fcac8ef = 1.5842570 / 0x3fc8ad4f = 1.5677890
        {1.5842570f, 1.5677890f},
        // 0x3fbbbc34 = 1.4666810 / 0x3fbe5d74 = 1.4872270
        {1.4666810f, 1.4872270f},
};

// Per-level kernel length ("iVar4" in VHE::Reset): 0x1000 = 4096 for every level
// except level 1 which is 0x7ff = 2047.
const uint32_t VHE::kKernelLength[5] = {4096, 2047, 4096, 4096, 4096};

VHE::VHE()
        : convLeft(),
          convRight(),
          inputBuffer(2, kBlockSize * 4),
          outputBuffer(2, kBlockSize * 4),
          samplingRate(44100), // original default 0xac44
          effectLevel(0),
          enabled(false),
          kernelLoaded(false) {
    scratchInterleaved.resize(kBlockSize * 2);
    scratchL.resize(kBlockSize);
    scratchR.resize(kBlockSize);
    scratchOutL.resize(kBlockSize);
    scratchOutR.resize(kBlockSize);
    scratchOut.resize(kBlockSize * 2);

    Reset();
}

void VHE::Process(float *samples, uint32_t size) {
    // Pass-through when disabled, no kernels loaded (unsupported rate), or empty.
    if (!enabled || !kernelLoaded || size == 0) {
        return;
    }

    // Queue the incoming interleaved stereo frames.
    inputBuffer.Push(samples, size);

    // Drain whole blocks: de-interleave, convolve each ear, re-interleave.
    // The original convolves both convolvers over the same interleaved block in
    // place (PConvSingle_F32::ConvolveInterleaved, one per channel); here the
    // equivalent de-interleave -> per-ear convolve -> re-interleave is used.
    while (inputBuffer.GetSize() >= kBlockSize) {
        inputBuffer.Pop(scratchInterleaved.data(), kBlockSize);

        for (uint32_t i = 0; i < kBlockSize; i++) {
            scratchL[i] = scratchInterleaved[i * 2];
            scratchR[i] = scratchInterleaved[i * 2 + 1];
        }

        convLeft.ProcessBlock(scratchL.data(), scratchOutL.data());
        convRight.ProcessBlock(scratchR.data(), scratchOutR.data());

        for (uint32_t i = 0; i < kBlockSize; i++) {
            scratchOut[i * 2] = scratchOutL[i];
            scratchOut[i * 2 + 1] = scratchOutR[i];
        }

        outputBuffer.Push(scratchOut.data(), kBlockSize);
    }

    // Emit. Until the first block has been produced the output queue underruns;
    // zero-fill the tail (one-block startup latency), matching the buffered
    // behaviour of the original WaveBuffer pop.
    uint32_t popped = outputBuffer.Pop(samples, size);
    if (popped < size) {
        std::memset(samples + popped * 2, 0,
                    (size - popped) * 2 * sizeof(float));
    }
}

void VHE::Reset() {
    // Mirrors VHE::Reset: reset the queues, unload the convolvers, then reload
    // the kernels for the current (effectLevel, samplingRate) pair.
    inputBuffer.Reset();
    outputBuffer.Reset();

    convLeft.Reset();
    convLeft.ReleaseResources();
    convRight.Reset();
    convRight.ReleaseResources();

    kernelLoaded = false;

    LoadKernelsForCurrentState();
}

void VHE::LoadKernelsForCurrentState() {
    // Original supports only 44100 and 48000; any other rate leaves the
    // convolvers unloaded (=> Process pass-through), exactly as the switch's
    // "if (rate != 48000) return;" fall-throughs.
    int rateIndex;
    if (samplingRate == 44100) {
        rateIndex = 0;
    } else if (samplingRate == 48000) {
        rateIndex = 1;
    } else {
        return;
    }

    const int level = effectLevel;
    const uint32_t length = kKernelLength[level];
    const float gain = kEffectGain[level][rateIndex];

    // Select the left/right kernel blobs for this (level, rate) pair and bake in
    // the per-level make-up gain, exactly as PConvSingle_F32::LoadKernel does at
    // load time (the convolver applies its own FFT normalisation on top).
    const int baseIndex = level * 4 + rateIndex * 2;
    std::vector<float> kernelL(length);
    std::vector<float> kernelR(length);
    LoadKernelBlob(baseIndex + 0, length, gain, kernelL);
    LoadKernelBlob(baseIndex + 1, length, gain, kernelR);

    bool okL = convLeft.LoadKernel(kernelL.data(), length, kBlockSize);
    bool okR = convRight.LoadKernel(kernelR.data(), length, kBlockSize);
    kernelLoaded = okL && okR;
}

void VHE::LoadKernelBlob(int kernelIndex, uint32_t length, float gain,
                         std::vector<float> &out) const {
    out.resize(length);
    for (uint32_t i = 0; i < length; i++) {
        float sample;
        const uint32_t bits = kVheKernelBits[kernelIndex][i];
        std::memcpy(&sample, &bits, sizeof(float));
        out[i] = sample * gain;
    }
}

void VHE::SetEnable(bool enable) {
    // VHE::SetEnable: when transitioning from disabled to enabled, Reset() first
    // (reload kernels / clear queues), then latch the new state.
    if (!enabled) {
        if (!enable) {
            return;
        }
        Reset();
    }
    if (enable == enabled) {
        return;
    }
    enabled = enable;
}

void VHE::SetSamplingRate(uint32_t newSamplingRate) {
    if (samplingRate == newSamplingRate) {
        return;
    }
    samplingRate = newSamplingRate;
    Reset();
}

void VHE::SetEffectLevel(int level) {
    if (effectLevel == level) {
        return;
    }
    if (level < 0 || level > kMaxLevel) { // original: reject (uint)level > 4
        return;
    }
    effectLevel = level;
    Reset();
}
