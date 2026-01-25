#include "CircularBuffer.h"
#include <algorithm>
#include <cstring>

namespace viper {
namespace utils {

CircularBuffer::CircularBuffer(uint32_t channels, uint32_t capacityFrames)
    : channels(channels), capacityFrames(capacityFrames), readIndex(0),
      writeIndex(0), sizeFrames(0) {
  buffer.resize(capacityFrames * channels, 0.0f);
}

CircularBuffer::~CircularBuffer() {}

void CircularBuffer::Reset() {
  readIndex = 0;
  writeIndex = 0;
  sizeFrames = 0;
}

uint32_t CircularBuffer::GetSize() const { return sizeFrames; }

uint32_t CircularBuffer::GetCapacity() const { return capacityFrames; }

uint32_t CircularBuffer::Push(const float *data, uint32_t frameCount) {
  if (frameCount == 0)
    return 0;

  uint32_t framesToWrite = std::min(frameCount, capacityFrames - sizeFrames);
  if (framesToWrite == 0)
    return 0;

  uint32_t firstChunkFrames =
      std::min(framesToWrite, capacityFrames - writeIndex);
  uint32_t secondChunkFrames = framesToWrite - firstChunkFrames;

  // Copy first chunk
  std::memcpy(buffer.data() + writeIndex * channels, data,
              firstChunkFrames * channels * sizeof(float));

  // Copy second chunk (wrap around)
  if (secondChunkFrames > 0) {
    std::memcpy(buffer.data(), data + firstChunkFrames * channels,
                secondChunkFrames * channels * sizeof(float));
  }

  writeIndex = (writeIndex + framesToWrite) % capacityFrames;
  sizeFrames += framesToWrite;

  return framesToWrite;
}

uint32_t CircularBuffer::Pop(float *data, uint32_t frameCount) {
  if (frameCount == 0 || sizeFrames == 0)
    return 0;

  uint32_t framesToRead = std::min(frameCount, sizeFrames);

  uint32_t firstChunkFrames =
      std::min(framesToRead, capacityFrames - readIndex);
  uint32_t secondChunkFrames = framesToRead - firstChunkFrames;

  // Read first chunk
  if (data) {
    std::memcpy(data, buffer.data() + readIndex * channels,
                firstChunkFrames * channels * sizeof(float));
  }

  // Read second chunk
  if (secondChunkFrames > 0) {
    if (data) {
      std::memcpy(data + firstChunkFrames * channels, buffer.data(),
                  secondChunkFrames * channels * sizeof(float));
    }
  }

  readIndex = (readIndex + framesToRead) % capacityFrames;
  sizeFrames -= framesToRead;

  return framesToRead;
}

uint32_t CircularBuffer::Peek(float *data, uint32_t frameCount) const {
  if (frameCount == 0 || sizeFrames == 0 || data == nullptr)
    return 0;

  uint32_t framesToRead = std::min(frameCount, sizeFrames);

  uint32_t firstChunkFrames =
      std::min(framesToRead, capacityFrames - readIndex);
  uint32_t secondChunkFrames = framesToRead - firstChunkFrames;

  // Read first chunk
  std::memcpy(data, buffer.data() + readIndex * channels,
              firstChunkFrames * channels * sizeof(float));

  // Read second chunk
  if (secondChunkFrames > 0) {
    std::memcpy(data + firstChunkFrames * channels, buffer.data(),
                secondChunkFrames * channels * sizeof(float));
  }

  return framesToRead;
}

} // namespace utils
} // namespace viper
