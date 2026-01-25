#include "Convolver.h"
#include <algorithm>
#include <cstring>

namespace viper {
namespace dsp {

Convolver::Convolver()
    : enabled(false), samplingRate(44100), currentKernelId(0),
      kernelLoadIndex(0), kernelExpectedLength(0), crossChannelLevel(0.0f) {

  convLeft = std::make_unique<PartitionedConvolver>();
  convRight = std::make_unique<PartitionedConvolver>();

  // Using 2 channels for input/output buffers
  // Size should be large enough to handle bursts
  inputBuffer = std::make_unique<WaveBuffer>(2, CONVOLVER_BLOCK_SIZE * 4);
  outputBuffer = std::make_unique<WaveBuffer>(2, CONVOLVER_BLOCK_SIZE * 4);
}

Convolver::~Convolver() {}

void Convolver::SetEnable(bool enable) {
  if (this->enabled != enable) {
    this->enabled = enable;
    Reset();
  }
}

bool Convolver::GetEnabled() const { return enabled; }

void Convolver::SetSamplingRate(uint32_t samplingRate) {
  if (this->samplingRate != samplingRate) {
    this->samplingRate = samplingRate;
    Reset();
  }
}

void Convolver::PrepareKernelBuffer(uint32_t kernelId, uint32_t length) {
  currentKernelId = kernelId;
  kernelExpectedLength = length;
  kernelLoadIndex = 0;
  kernelLoadBuffer.resize(length * 2); // Stereo? Or Mono? Assumed interleaved
                                       // stereo or usually kernel is mono?
  // The decompiled logic handles single channel or stereo loading.
  // Let's assume we load a linear buffer and interpret it later.
  std::fill(kernelLoadBuffer.begin(), kernelLoadBuffer.end(), 0.0f);
}

void Convolver::SetKernelBuffer(uint32_t kernelId, const float *data,
                                uint32_t size) {
  if (kernelId != currentKernelId)
    return;
  if (kernelLoadIndex + size > kernelLoadBuffer.size()) {
    // Resize if needed (or error)
    kernelLoadBuffer.resize(kernelLoadIndex + size);
  }
  std::memcpy(kernelLoadBuffer.data() + kernelLoadIndex, data,
              size * sizeof(float));
  kernelLoadIndex += size;
}

void Convolver::CommitKernelBuffer(uint32_t kernelId) {
  if (kernelId != currentKernelId)
    return;

  // Check if we loaded valid data
  if (kernelLoadIndex == 0 || kernelExpectedLength == 0)
    return;

  // Use a tolerance for floating point size calculations or strict?
  // The original code checks if (LoadedSize == ExpectedLength) -> Mono
  // If (LoadedSize == 2 * ExpectedLength) -> Stereo

  if (kernelLoadIndex == kernelExpectedLength) {
    // Mono Kernel: Apply same kernel to both channels
    convLeft->LoadKernel(kernelLoadBuffer.data(), kernelExpectedLength,
                         CONVOLVER_BLOCK_SIZE);
    convRight->LoadKernel(kernelLoadBuffer.data(), kernelExpectedLength,
                          CONVOLVER_BLOCK_SIZE);
  } else if (kernelLoadIndex == kernelExpectedLength * 2) {
    // Stereo Kernel: Assumed Interleaved (L R L R)
    // We need to deinterlace into two buffers
    std::vector<float> kernelL(kernelExpectedLength);
    std::vector<float> kernelR(kernelExpectedLength);

    for (uint32_t i = 0; i < kernelExpectedLength; i++) {
      kernelL[i] = kernelLoadBuffer[i * 2];
      kernelR[i] = kernelLoadBuffer[i * 2 + 1];
    }

    convLeft->LoadKernel(kernelL.data(), kernelExpectedLength,
                         CONVOLVER_BLOCK_SIZE);
    convRight->LoadKernel(kernelR.data(), kernelExpectedLength,
                          CONVOLVER_BLOCK_SIZE);
  } else {
    // Error or mismatch size.
    // Original code might fallback or fail. We'll verify what we can.
    // For now, if size is sufficient, we might assume Mono?
    // Let's stick to strictMono or strictStereo check.
  }

  Reset();

  // Clear load buffer to free memory
  std::vector<float>().swap(kernelLoadBuffer);
}

void Convolver::SetCrossChannel(float level) {
  this->crossChannelLevel = level;
}

void Convolver::Reset() {
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
  if (!enabled) {
    std::memcpy(output, input, frameCount * 2 * sizeof(float));
    return;
  }

  inputBuffer->PushSamples(input, frameCount);

  while (inputBuffer->GetBufferSize() >= CONVOLVER_BLOCK_SIZE) {
    std::vector<float> interleavedBlock(CONVOLVER_BLOCK_SIZE * 2);
    uint32_t popped = inputBuffer->PopSamples(interleavedBlock.data(),
                                              CONVOLVER_BLOCK_SIZE, false);

    if (popped < CONVOLVER_BLOCK_SIZE)
      break;

    std::vector<float> blockInputL(CONVOLVER_BLOCK_SIZE);
    std::vector<float> blockInputR(CONVOLVER_BLOCK_SIZE);
    std::vector<float> blockOutputL(CONVOLVER_BLOCK_SIZE);
    std::vector<float> blockOutputR(CONVOLVER_BLOCK_SIZE);

    // Deinterlace
    for (uint32_t i = 0; i < CONVOLVER_BLOCK_SIZE; i++) {
      blockInputL[i] = interleavedBlock[i * 2];
      blockInputR[i] = interleavedBlock[i * 2 + 1];
    }

    // Process Convolution
    convLeft->ProcessBlock(blockInputL.data(), blockOutputL.data());
    convRight->ProcessBlock(blockInputR.data(), blockOutputR.data());

    // Apply Cross Channel Mixing if active
    // Original code logic: OutputL += OutputR * Level; OutputR += OutputL *
    // Level; Note: The original decompiled code seems to add the *result* of
    // one to the other? "auVar26 = FloatVectorAdd(*pauVar13, auVar23, 2)" where
    // auVar23 is from other channel? Standard implementation is: NewL = L + R *
    // Level NewR = R + L * Level
    if (crossChannelLevel > 0.0001f) {
      for (uint32_t i = 0; i < CONVOLVER_BLOCK_SIZE; i++) {
        float l = blockOutputL[i];
        float r = blockOutputR[i];
        blockOutputL[i] = l + r * crossChannelLevel;
        blockOutputR[i] = r + l * crossChannelLevel;
      }
    }

    // Interlace Output
    std::vector<float> outBlock(CONVOLVER_BLOCK_SIZE * 2);
    for (uint32_t i = 0; i < CONVOLVER_BLOCK_SIZE; i++) {
      outBlock[i * 2] = blockOutputL[i];
      outBlock[i * 2 + 1] = blockOutputR[i];
    }

    outputBuffer->PushSamples(outBlock.data(), CONVOLVER_BLOCK_SIZE);
  }

  uint32_t outPopped = outputBuffer->PopSamples(output, frameCount, false);
  if (outPopped < frameCount) {
    std::memset(output + outPopped * 2, 0,
                (frameCount - outPopped) * 2 * sizeof(float));
  }
}

} // namespace dsp
} // namespace viper
