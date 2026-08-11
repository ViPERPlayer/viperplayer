#include "Convolver.h"
#include <cstring>

namespace viper {
namespace dsp {

Convolver::Convolver()
    // 0xac44 = 44100 default sampling rate (Convolver::Convolver @ 00063cd8)
    : enabled(false), samplingRate(44100), inputBuffer(2, CONVOLVER_BLOCK_SIZE * 4), outputBuffer(2, CONVOLVER_BLOCK_SIZE * 4), crossChannelLevel(0.0f), crossChannelEnabled(false) {

  // convLeft/convRight are already default-constructed.

  // Allocate scratch buffers
  scratchInterleaved.resize(CONVOLVER_BLOCK_SIZE * 2);
  scratchInputL.resize(CONVOLVER_BLOCK_SIZE);
  scratchInputR.resize(CONVOLVER_BLOCK_SIZE);
  scratchOutputL.resize(CONVOLVER_BLOCK_SIZE);
  scratchOutputR.resize(CONVOLVER_BLOCK_SIZE);
  scratchOutBlock.resize(CONVOLVER_BLOCK_SIZE * 2);
}

Convolver::~Convolver() {}


void Convolver::SetEnable(bool enable) {
  std::lock_guard<std::mutex> lock(mutex);
  // Matches Convolver::SetEnable @ 000640c4: when transitioning from disabled
  // to enabled the state is flushed via Reset() *before* the flag is raised;
  // disabling does not reset.
  if (!this->enabled) {
    if (!enable)
      return;
    ResetLocked();
  }
  this->enabled = enable;
}

bool Convolver::GetEnabled() const {
  std::lock_guard<std::mutex> lock(mutex);
  return enabled;
}

void Convolver::SetSamplingRate(uint32_t samplingRate) {
  std::lock_guard<std::mutex> lock(mutex);
  if (this->samplingRate != samplingRate) {
    this->samplingRate = samplingRate;
    ResetLocked();
  }
}

void Convolver::LoadKernelMono(const float *kernel, uint32_t samples) {
  std::lock_guard<std::mutex> lock(mutex);
  if (!kernel || samples == 0)
    return;

  if (samples < MIN_KERNEL_SAMPLES)
    return;

  // Apply same kernel to both channels
  convLeft.LoadKernel(kernel, samples, CONVOLVER_BLOCK_SIZE);
  convRight.LoadKernel(kernel, samples, CONVOLVER_BLOCK_SIZE);
  ResetLocked();
}

void Convolver::LoadKernelStereo(const float *kernelL, const float *kernelR,
                                 uint32_t samples) {
  std::lock_guard<std::mutex> lock(mutex);
  LoadKernelStereoLocked(kernelL, kernelR, samples);
}

void Convolver::LoadKernelStereoLocked(const float *kernelL,
                                       const float *kernelR, uint32_t samples) {
  if (!kernelL || !kernelR || samples < MIN_KERNEL_SAMPLES)
    return;

  convLeft.LoadKernel(kernelL, samples, CONVOLVER_BLOCK_SIZE);
  convRight.LoadKernel(kernelR, samples, CONVOLVER_BLOCK_SIZE);
  ResetLocked();
}

void Convolver::LoadKernelStereoInterleaved(const float *kernelInterleaved,
                                            uint32_t frames) {
  std::lock_guard<std::mutex> lock(mutex);
  if (!kernelInterleaved || frames == 0)
    return;

  std::vector<float> kernelL(frames);
  std::vector<float> kernelR(frames);

  for (uint32_t i = 0; i < frames; i++) {
    kernelL[i] = kernelInterleaved[i * 2];
    kernelR[i] = kernelInterleaved[i * 2 + 1];
  }

  LoadKernelStereoLocked(kernelL.data(), kernelR.data(), frames);
}

void Convolver::UnloadKernel() {
  std::lock_guard<std::mutex> lock(mutex);
  convLeft.ReleaseResources();
  convRight.ReleaseResources();
  ResetLocked();
}

bool Convolver::IsKernelLoaded() const {
  std::lock_guard<std::mutex> lock(mutex);
  return IsKernelLoadedLocked();
}

bool Convolver::IsKernelLoadedLocked() const {
  return convLeft.GetBlockSize() > 0;
}

void Convolver::SetCrossChannel(float level) {
  std::lock_guard<std::mutex> lock(mutex);
  // Convolver::SetCrossChannel @ 00064018: clamp to [0,1] and only arm the
  // mixing once the level rises above the enable threshold.
  if (level < 0.0f) {
    crossChannelLevel = 0.0f;
    crossChannelEnabled = false;
  } else if (level > 1.0f) {
    crossChannelLevel = 1.0f; // 0x3f800000 = 1.0f
    crossChannelEnabled = true;
  } else {
    crossChannelLevel = level;
    crossChannelEnabled = level > CROSS_CHANNEL_THRESHOLD;
  }
}

void Convolver::Reset() {
  std::lock_guard<std::mutex> lock(mutex);
  ResetLocked();
}

void Convolver::ResetLocked() {
  convLeft.Reset();
  convRight.Reset();
  inputBuffer.Reset();
  outputBuffer.Reset();
}

// Dry passthrough for the buffers this stage does not convolve. ViPER::process convolves in place
// (input == output), and memcpy with identical pointers is undefined behaviour, so only copy when
// the buffers really are distinct.
static inline void PassThrough(const float *input, float *output,
                               uint32_t frameCount) {
  if (output != input) {
    std::memcpy(output, input, frameCount * 2 * sizeof(float));
  }
}

void Convolver::Process(float *input, float *output, uint32_t frameCount) {
  // Never block here. This runs on the audio thread, while LoadKernel* holds the same mutex for as
  // long as it takes to partition an impulse response and build its FFT plans — easily longer than
  // a buffer period, which used to mean loading a convolver kernel could stall the render and drop
  // audio. Passing through dry for the buffers that overlap a load is the same trade-off
  // IIREqualizer, VHE and ViPERDDC already make.
  std::unique_lock<std::mutex> lock(mutex, std::try_to_lock);
  if (!lock.owns_lock()) {
    PassThrough(input, output, frameCount);
    return;
  }

  if (!enabled || !IsKernelLoadedLocked()) {
    PassThrough(input, output, frameCount);
    return;
  }

  // Push input samples (interleaved) into the staging buffer.
  inputBuffer.Push(input, frameCount);

  // Drain whole blocks while enough input has accumulated.
  while (inputBuffer.GetSize() >= CONVOLVER_BLOCK_SIZE) {
    // Pop one block of interleaved stereo.
    inputBuffer.Pop(scratchInterleaved.data(), CONVOLVER_BLOCK_SIZE);

    // Deinterlace
    for (uint32_t i = 0; i < CONVOLVER_BLOCK_SIZE; i++) {
      scratchInputL[i] = scratchInterleaved[i * 2];
      scratchInputR[i] = scratchInterleaved[i * 2 + 1];
    }

    // Process Convolution
    convLeft.ProcessBlock(scratchInputL.data(), scratchOutputL.data());
    convRight.ProcessBlock(scratchInputR.data(), scratchOutputR.data());

    // Apply Cross Channel Mixing (outL = L + level*R, outR = R + level*L)
    if (crossChannelEnabled) {
      for (uint32_t i = 0; i < CONVOLVER_BLOCK_SIZE; i++) {
        float l = scratchOutputL[i];
        float r = scratchOutputR[i];
        scratchOutputL[i] = l + r * crossChannelLevel;
        scratchOutputR[i] = r + l * crossChannelLevel;
      }
    }

    // Interlace Output
    for (uint32_t i = 0; i < CONVOLVER_BLOCK_SIZE; i++) {
      scratchOutBlock[i * 2] = scratchOutputL[i];
      scratchOutBlock[i * 2 + 1] = scratchOutputR[i];
    }

    outputBuffer.Push(scratchOutBlock.data(), CONVOLVER_BLOCK_SIZE);
  }

  // Pop processed samples to output
  uint32_t outPopped = outputBuffer.Pop(output, frameCount);
  if (outPopped < frameCount) {
    // Fill remaining with zeros (latency/underrun)
    std::memset(output + outPopped * 2, 0,
                (frameCount - outPopped) * 2 * sizeof(float));
  }
}

} // namespace dsp
} // namespace viper
