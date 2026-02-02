#pragma once

#include "../utils/CircularBuffer.h"
#include "PartitionedConvolver.h"
#include <memory>
#include <vector>
#include <mutex>

namespace viper {
namespace dsp {

class Convolver {
public:
  Convolver();
  ~Convolver();

  void SetEnable(bool enable);
  bool GetEnabled() const;

  void SetSamplingRate(uint32_t samplingRate);

  // Load a Mono kernel (applies same response to both channels)
  // kernel: Audio data
  // samples: Number of samples in the kernel
  void LoadKernelMono(const float *kernel, uint32_t samples);

  // Load a Stereo kernel (separate L/R responses)
  // kernelL: Left channel data
  // kernelR: Right channel data
  // samples: Number of samples per channel
  void LoadKernelStereo(const float *kernelL, const float *kernelR,
                        uint32_t samples);

  // Load a Stereo kernel from interleaved data
  // kernelInterleaved: L R L R...
  // frames: Number of frames (L+R pairs)
  void LoadKernelStereoInterleaved(const float *kernelInterleaved,
                                   uint32_t frames);

  void UnloadKernel();
  bool IsKernelLoaded() const;

  void SetCrossChannel(float level);

  void Reset();

  // Process interleaved stereo samples
  // input: L R L R ...
  // output: L R L R ...
  void Process(float *input, float *output, uint32_t frameCount);

private:
  bool enabled;
  uint32_t samplingRate;

  PartitionedConvolver convLeft;
  PartitionedConvolver convRight;

  // Buffering for block processing
  viper::utils::CircularBuffer inputBuffer;
  viper::utils::CircularBuffer outputBuffer;

  // Cross channel handling (simplified for now as per decompiled hint)
  // Decompiled code has SetCrossChannel but logic was a bit obscure.
  // It seems to mix channels if cross channel is set.
  float crossChannelLevel;

  static const uint32_t CONVOLVER_BLOCK_SIZE = 4096;

  // Scratch buffers for Process loop
  std::vector<float> scratchInterleaved;
  std::vector<float> scratchInputL;
  std::vector<float> scratchInputR;
  std::vector<float> scratchOutputL;
  std::vector<float> scratchOutputR;
  std::vector<float> scratchOutBlock;

  mutable std::recursive_mutex mutex;
};

} // namespace dsp
} // namespace viper
