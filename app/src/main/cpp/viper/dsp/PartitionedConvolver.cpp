#include "PartitionedConvolver.h"
#include <algorithm>
#include <cstring>

namespace viper {
namespace dsp {

PartitionedConvolver::PartitionedConvolver() : blockSize(0), segments(0) {}

PartitionedConvolver::~PartitionedConvolver() { ReleaseResources(); }

void PartitionedConvolver::ReleaseResources() {
  kernelPartitions.clear();
  inputPartitions.clear();
  overlapBuffer.clear();
  fft.reset();
  blockSize = 0;
  segments = 0;
}

void PartitionedConvolver::Reset() {
  if (overlapBuffer.size() > 0)
    std::fill(overlapBuffer.begin(), overlapBuffer.end(), 0.0f);
  for (auto &part : inputPartitions) {
    std::fill(part.begin(), part.end(), std::complex<float>(0.0f, 0.0f));
  }
}

bool PartitionedConvolver::LoadKernel(const float *kernel, uint32_t length,
                                      uint32_t blockSize) {
  ReleaseResources();

  if (!kernel || length == 0 || blockSize == 0)
    return false;

  this->blockSize = blockSize;

  // Calculate number of segments
  this->segments = (length + blockSize - 1) / blockSize;

  // Initialize FFT for size 2*blockSize
  fft = std::make_unique<viper::utils::FFT>(blockSize * 2);

  // Resize storage
  kernelPartitions.resize(segments);
  inputPartitions.resize(segments);

  // Resize Overlap-Add buffer
  overlapBuffer.resize(blockSize, 0.0f);

  // Prepare kernel partitions
  std::vector<float> timeDomainBuffer(blockSize * 2);

  for (uint32_t i = 0; i < segments; i++) {
    // Clear buffer
    std::fill(timeDomainBuffer.begin(), timeDomainBuffer.end(), 0.0f);

    // Copy kernel segment (pad with zeros)
    uint32_t srcStart = i * blockSize;
    uint32_t copyLen = 0;
    if (srcStart < length) {
      copyLen = std::min(blockSize, length - srcStart);
      std::memcpy(timeDomainBuffer.data(), kernel + srcStart,
                  copyLen * sizeof(float));
    }

    // FFT [Size 2N]
    kernelPartitions[i].resize(blockSize + 1);
    fft->Forward(timeDomainBuffer.data(), kernelPartitions[i].data());

    // Initialize input partitions
    inputPartitions[i].resize(blockSize + 1, std::complex<float>(0.0f, 0.0f));
  }

  return true;
}

void PartitionedConvolver::ProcessBlock(const float *inBlock, float *outBlock) {
  if (!fft || blockSize == 0) {
    // No-op if not ready
    return;
  }

  // 1. Prepare time domain buffer (Input block padded with zeros)
  // Size 2N
  std::vector<float> timeDomain(blockSize * 2, 0.0f);
  std::memcpy(timeDomain.data(), inBlock, blockSize * sizeof(float));

  // 2. FFT current block -> F[0]
  std::vector<std::complex<float>> currentPartition(blockSize + 1);
  fft->Forward(timeDomain.data(), currentPartition.data());

  // 3. Shift input partitions history
  // inputPartitions[0] is most recent
  for (int i = segments - 1; i > 0; i--) {
    inputPartitions[i] = inputPartitions[i - 1];
  }
  inputPartitions[0] = currentPartition;

  // 4. Frequency Domain Convolution
  std::vector<std::complex<float>> resultFreq(blockSize + 1, {0.0f, 0.0f});

  for (uint32_t i = 0; i < segments; i++) {
    // Accumulate product
    for (uint32_t k = 0; k < blockSize + 1; k++) {
      resultFreq[k] += inputPartitions[i][k] * kernelPartitions[i][k];
    }
  }

  // 5. IFFT
  std::vector<float> resultTime(blockSize * 2);
  fft->Inverse(resultFreq.data(), resultTime.data());

  // 6. Overlap-Add
  for (uint32_t i = 0; i < blockSize; i++) {
    outBlock[i] = resultTime[i] + overlapBuffer[i];
    overlapBuffer[i] = resultTime[i + blockSize];
  }
}

} // namespace dsp
} // namespace viper
