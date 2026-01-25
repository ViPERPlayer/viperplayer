#pragma once

#include <algorithm>
#include <cstdint>
#include <memory>
#include <vector>

namespace viper {
namespace utils {

class CircularBuffer {
public:
  // channels: Number of interleaved channels (e.g., 2 for Stereo)
  // capacityFrames: Maximum number of frames (multi-channel samples) to store
  CircularBuffer(uint32_t channels, uint32_t capacityFrames);
  ~CircularBuffer();

  void Reset();

  // Returns the number of frames currently available to pop
  uint32_t GetSize() const;

  // Returns the maximum capacity in frames
  uint32_t GetCapacity() const;

  // Pushes interleaved samples into the buffer
  // Returns actual frames pushed (min of count and available space)
  uint32_t Push(const float *data, uint32_t frameCount);

  // Pops interleaved samples from the buffer
  // Returns actual frames popped (min of count and available data)
  uint32_t Pop(float *data, uint32_t frameCount);

  // Peeks at data without removing it
  uint32_t Peek(float *data, uint32_t frameCount) const;

private:
  uint32_t channels;
  uint32_t capacityFrames;
  std::vector<float> buffer;

  uint32_t readIndex;  // In frames (0 to capacityFrames)
  uint32_t writeIndex; // In frames (0 to capacityFrames)
  uint32_t sizeFrames; // Current number of frames stored (atomic-ish tracking)
};

} // namespace utils
} // namespace viper
