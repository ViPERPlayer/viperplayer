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
  //
  // Called on the audio thread, so it never waits on the mutex: if a kernel load
  // holds it, this buffer passes through dry rather than blocking the render.
  // input and output may alias (ViPER::process convolves in place).
  void Process(float *input, float *output, uint32_t frameCount);

private:
  // Internals that assume the caller already holds `mutex`. Splitting these out is what lets
  // `mutex` be a plain std::mutex, which is what Process() needs in order to try_lock: a
  // recursive_mutex would let Process() re-enter its own held lock, but it cannot express
  // "give up if somebody else holds this".
  void ResetLocked();
  bool IsKernelLoadedLocked() const;
  void LoadKernelStereoLocked(const float *kernelL, const float *kernelR,
                              uint32_t samples);

  bool enabled;
  uint32_t samplingRate;

  PartitionedConvolver convLeft;
  PartitionedConvolver convRight;

  // Buffering for block processing
  viper::utils::CircularBuffer inputBuffer;
  viper::utils::CircularBuffer outputBuffer;

  // Cross channel mixing: outL = L + level*R, outR = R + level*L.
  // SetCrossChannel clamps level to [0,1]; mixing is only applied when the
  // level exceeds CROSS_CHANNEL_THRESHOLD (tracked by crossChannelEnabled),
  // matching the original Convolver::SetCrossChannel.
  float crossChannelLevel;
  bool crossChannelEnabled;

  // 0x1000 in Convolver::Convolver / SetKernel (PConvSingle block size argument)
  static const uint32_t CONVOLVER_BLOCK_SIZE = 4096;

  // Original requires a kernel of at least 16 samples per channel
  // (Convolver::SetKernel: `if (param_2 < 0x10) return;`).
  static const uint32_t MIN_KERNEL_SAMPLES = 16;

  // 0x00064074 = 0x38d1b717 = 9.999999747e-05f (cross-channel enable threshold)
  static constexpr float CROSS_CHANNEL_THRESHOLD = 9.999999747e-05f;

  // Scratch buffers for Process loop
  std::vector<float> scratchInterleaved;
  std::vector<float> scratchInputL;
  std::vector<float> scratchInputR;
  std::vector<float> scratchOutputL;
  std::vector<float> scratchOutputR;
  std::vector<float> scratchOutBlock;

  // Guards the kernels, the staging buffers and the cross-channel parameters. The audio thread
  // (Process) try-locks and falls back to dry passthrough; every other caller blocks.
  mutable std::mutex mutex;
};

} // namespace dsp
} // namespace viper
