#pragma once

#include "../utils/WaveBuffer.h"
#include "PartitionedConvolver.h"
#include <memory>
#include <vector>

namespace viper {
namespace dsp {

class Convolver {
public:
  Convolver();
  ~Convolver();

  void SetEnable(bool enable);
  bool GetEnabled() const;

  void SetSamplingRate(uint32_t samplingRate);

  // Prepare internal buffers for a kernel of specific length
  void PrepareKernelBuffer(uint32_t kernelId, uint32_t length);

  // Load data into the prepared buffer
  void SetKernelBuffer(uint32_t kernelId, const float *data, uint32_t size);

  // Commit and finalize the kernel
  void CommitKernelBuffer(uint32_t kernelId);

  void SetCrossChannel(float level);

  void Reset();

  // Process interleaved stereo samples
  // input: L R L R ...
  // output: L R L R ...
  void Process(float *input, float *output, uint32_t frameCount);

private:
  bool enabled;
  uint32_t samplingRate;

  std::unique_ptr<PartitionedConvolver> convLeft;
  std::unique_ptr<PartitionedConvolver> convRight;

  // Buffering for block processing
  std::unique_ptr<WaveBuffer> inputBuffer;
  std::unique_ptr<WaveBuffer> outputBuffer;

  // Temporary kernel loading buffer
  std::vector<float> kernelLoadBuffer;
  uint32_t currentKernelId;
  uint32_t kernelLoadIndex;
  uint32_t kernelExpectedLength;

  // Cross channel handling (simplified for now as per decompiled hint)
  // Decompiled code has SetCrossChannel but logic was a bit obscure.
  // It seems to mix channels if cross channel is set.
  float crossChannelLevel;

  static const uint32_t CONVOLVER_BLOCK_SIZE = 4096;
};

} // namespace dsp
} // namespace viper
