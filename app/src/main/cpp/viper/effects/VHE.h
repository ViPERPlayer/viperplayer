#pragma once

#include <cstdint>
#include <vector>
#include <mutex>
#include "../dsp/PartitionedConvolver.h"
#include "../utils/CircularBuffer.h"

// Headphone Surround+  (ViPER's Headphone Engine, native class "VHE")
// =============================================================================
// Reverse-engineered from ViPER4Android v2505 (VHE.c / PConvSingle_F32.c /
// WaveBuffer_R32.c). It is a headphone virtual-surround / HRTF-style processor
// driven by a single quality / effect-level parameter (0..4).
//
// TOPOLOGY (recovered, faithful):
//   * Two partitioned FFT convolvers (PConvSingle_F32 in the original; here the
//     project's viper::dsp::PartitionedConvolver) - one per output ear.
//   * Block-based: interleaved stereo is buffered, and whenever >= kBlockSize
//     (4096) frames are queued, one block is de-interleaved, the left channel
//     is convolved with the left kernel and the right channel with the right
//     kernel, re-interleaved and queued to the output (block size 4096, FFT
//     size 8192). This mirrors VHE::Process exactly (PushSamples -> while
//     GetBufferOffset >= blockSize: ConvolveInterleaved x2 -> PushSamples ->
//     PopSamples). Output therefore lags input by one block (latency fill).
//   * Reset() re-selects the kernels for the current (effectLevel, samplingRate)
//     pair; only 44100 Hz and 48000 Hz are supported (any other rate leaves the
//     convolvers unloaded => pass-through), exactly as the original switch.
//
// RECOVERED COEFFICIENTS (NOT guessed - exact from the binary):
//   * Per-level make-up gain kEffectGain[level][rate] (the float "iVar3" loaded
//     in VHE::Reset, decoded from its IEEE-754 bit pattern - hex noted in .cpp).
//   * Per-level kernel length kKernelLength[level] = {4096, 2047, 4096, 4096,
//     4096} (the "iVar4" length). Block/FFT size, channel counts and buffer
//     sizing are likewise recovered.
//   * The actual HRIR/BRIR impulse-response SAMPLES. These are the large measured
//     kernels in libv4a_fx_*.so .rodata that VHE::Reset references via DAT_*
//     pointers. They were extracted bit-exact from the binary's .rodata (20
//     blobs = 5 levels x 2 rates x 2 ears, tiling 0x86560..0xCE550) and embedded
//     in VHEKernels.inc; LoadKernelBlob() reinterprets them and bakes in the
//     per-level make-up gain, mirroring PConvSingle_F32::LoadKernel.
// =============================================================================

class VHE {
public:
    VHE();

    // Interleaved stereo, in-place (matches the project's effect convention and
    // the original VHE::Process(buf, buf, size)).
    void Process(float *samples, uint32_t size);
    void Reset();
    void SetEnable(bool enable);
    void SetSamplingRate(uint32_t samplingRate);
    void SetEffectLevel(int level); // 0..4 (host command id 65545, "vhs.qual")
    bool GetEnabled() const { return enabled; }

private:
    // Copy the real HRIR/BRIR blob 'kernelIndex' (kVheKernelBits, length samples)
    // out as floats, multiplied by the per-level make-up gain - the gain bake
    // PConvSingle_F32::LoadKernel performs at load time.
    void LoadKernelBlob(int kernelIndex, uint32_t length, float gain,
                        std::vector<float> &out) const;
    void LoadKernelsForCurrentState();

    static constexpr uint32_t kBlockSize = 4096; // frames per convolution block
    static constexpr int kMaxLevel = 4;

    // [level][rateIndex] rateIndex: 0 = 44100 Hz, 1 = 48000 Hz. Exact values
    // recovered from the VHE::Reset float bit patterns (see .cpp for hex).
    static const float kEffectGain[5][2];
    static const uint32_t kKernelLength[5];

    viper::dsp::PartitionedConvolver convLeft;
    viper::dsp::PartitionedConvolver convRight;

    viper::utils::CircularBuffer inputBuffer;
    viper::utils::CircularBuffer outputBuffer;

    uint32_t samplingRate;
    int effectLevel;
    bool enabled;
    bool kernelLoaded;

    // Scratch buffers (sized for one block).
    std::vector<float> scratchInterleaved; // kBlockSize * 2
    std::vector<float> scratchL;           // kBlockSize
    std::vector<float> scratchR;           // kBlockSize
    std::vector<float> scratchOutL;        // kBlockSize
    std::vector<float> scratchOutR;        // kBlockSize
    std::vector<float> scratchOut;         // kBlockSize * 2

    // Guards the convolvers/buffers: Process() (audio thread) try-locks and skips on contention;
    // Reset() (loader thread, on level/rate change) holds it while it reloads the kernels.
    mutable std::mutex mMutex;
};
