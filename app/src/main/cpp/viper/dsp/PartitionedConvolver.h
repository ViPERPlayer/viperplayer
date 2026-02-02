#pragma once

#include "../utils/FFT.h"
#include <complex>
#include <memory>
#include <vector>

namespace viper {
namespace dsp {

class PartitionedConvolver {
public:
  PartitionedConvolver();
  ~PartitionedConvolver();

  void Reset();
  void ReleaseResources();
  bool LoadKernel(const float *kernel, uint32_t length, uint32_t blockSize);

  // Process a single block of size 'blockSize'
  // inBlock and outBlock must be at least 'blockSize' elements.
  // They can point to the same buffer (in-place processing).
  void ProcessBlock(const float *inBlock, float *outBlock);

  uint32_t GetBlockSize() const { return blockSize; }

private:
  uint32_t blockSize;
  uint32_t segments;

  viper::utils::FFT fft;

  // Kernel partitions: [segment][frequency_bin]
  std::vector<std::vector<std::complex<float>>> kernelPartitions;

  // Input partitions history: [segment][frequency_bin]
  // Treated as a circular buffer or just shifted
  std::vector<std::vector<std::complex<float>>> inputPartitions;

  // Overlap-Add buffer
  std::vector<float> overlapBuffer;

  // Scratch buffers
  std::vector<float> processTimeDomain;
  std::vector<std::complex<float>> processPartition;
  std::vector<std::complex<float>> processResultFreq;
  std::vector<float> processResultTime;
};

} // namespace dsp
} // namespace viper
