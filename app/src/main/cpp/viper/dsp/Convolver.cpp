#include "Convolver.h"
#include <cstring>

namespace viper {
namespace dsp {

Convolver::Convolver()
    : enabled(false), samplingRate(44100), crossChannelLevel(0.0f) {

  convLeft = std::make_unique<PartitionedConvolver>();
  convRight = std::make_unique<PartitionedConvolver>();

  // Using 2 channels for input/output buffers
  // Size should be large enough to handle bursts
  inputBuffer = std::make_unique<viper::utils::CircularBuffer>(
      2, CONVOLVER_BLOCK_SIZE * 4);
  outputBuffer = std::make_unique<viper::utils::CircularBuffer>(
      2, CONVOLVER_BLOCK_SIZE * 4);

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
  std::lock_guard<std::recursive_mutex> lock(mutex);
  if (this->enabled != enable) {
    this->enabled = enable;
    Reset();
  }
}

bool Convolver::GetEnabled() const { return enabled; }

void Convolver::SetSamplingRate(uint32_t samplingRate) {
  std::lock_guard<std::recursive_mutex> lock(mutex);
  if (this->samplingRate != samplingRate) {
    this->samplingRate = samplingRate;
    Reset();
  }
}

void Convolver::LoadKernelMono(const float *kernel, uint32_t samples) {
  std::lock_guard<std::recursive_mutex> lock(mutex);
  if (!kernel || samples == 0)
    return;

  // Apply same kernel to both channels
  convLeft->LoadKernel(kernel, samples, CONVOLVER_BLOCK_SIZE);
  convRight->LoadKernel(kernel, samples, CONVOLVER_BLOCK_SIZE);
  Reset();
}

void Convolver::LoadKernelStereo(const float *kernelL, const float *kernelR,
                                 uint32_t samples) {
  std::lock_guard<std::recursive_mutex> lock(mutex);
  if (!kernelL || !kernelR || samples == 0)
    return;

  convLeft->LoadKernel(kernelL, samples, CONVOLVER_BLOCK_SIZE);
  convRight->LoadKernel(kernelR, samples, CONVOLVER_BLOCK_SIZE);
  Reset();
}

void Convolver::LoadKernelStereoInterleaved(const float *kernelInterleaved,
                                            uint32_t frames) {
  std::lock_guard<std::recursive_mutex> lock(mutex);
  if (!kernelInterleaved || frames == 0)
    return;

  std::vector<float> kernelL(frames);
  std::vector<float> kernelR(frames);

  for (uint32_t i = 0; i < frames; i++) {
    kernelL[i] = kernelInterleaved[i * 2];
    kernelR[i] = kernelInterleaved[i * 2 + 1];
  }

  LoadKernelStereo(kernelL.data(), kernelR.data(), frames);
}

void Convolver::UnloadKernel() {
  std::lock_guard<std::recursive_mutex> lock(mutex);
  convLeft->ReleaseResources();
  convRight->ReleaseResources();
  Reset();
}

bool Convolver::IsKernelLoaded() const {
  std::lock_guard<std::recursive_mutex> lock(mutex);
  return convLeft->GetBlockSize() > 0;
}

void Convolver::SetCrossChannel(float level) {
  std::lock_guard<std::recursive_mutex> lock(mutex);
  this->crossChannelLevel = level;
}

void Convolver::Reset() {
  std::lock_guard<std::recursive_mutex> lock(mutex);
  if (convLeft)
    convLeft->Reset();
  if (convRight)
    convRight->Reset();
  if (inputBuffer)
    inputBuffer->Reset();
  if (outputBuffer)
    outputBuffer->Reset();
}

void Convolver::Process(float *input, float *output, uint32_t frameCount) {
  std::lock_guard<std::recursive_mutex> lock(mutex);
  if (!enabled || !IsKernelLoaded()) {
    std::memcpy(output, input, frameCount * 2 * sizeof(float));
    return;
  }
  
  // Push input samples (Interleaved)
  inputBuffer->Push(input, frameCount);
//... (rest of method follows)

  while (inputBuffer->GetSize() >= CONVOLVER_BLOCK_SIZE) {
    // Pop a block
    // Pop a block
    inputBuffer->Pop(scratchInterleaved.data(), CONVOLVER_BLOCK_SIZE);

    // Deinterlace
    for (uint32_t i = 0; i < CONVOLVER_BLOCK_SIZE; i++) {
      scratchInputL[i] = scratchInterleaved[i * 2];
      scratchInputR[i] = scratchInterleaved[i * 2 + 1];
    }

    // Process Convolution
    convLeft->ProcessBlock(scratchInputL.data(), scratchOutputL.data());
    convRight->ProcessBlock(scratchInputR.data(), scratchOutputR.data());

    // Apply Cross Channel Mixing
    if (crossChannelLevel > 0.0001f) {
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

    outputBuffer->Push(scratchOutBlock.data(), CONVOLVER_BLOCK_SIZE);
  }

  // Pop processed samples to output
  uint32_t outPopped = outputBuffer->Pop(output, frameCount);
  if (outPopped < frameCount) {
    // Fill remaining with zeros (latency/underrun)
    std::memset(output + outPopped * 2, 0,
                (frameCount - outPopped) * 2 * sizeof(float));
  }
}

} // namespace dsp
} // namespace viper
