#include "FFT.h"
#include <cmath>

namespace viper {
namespace utils {

FFT::FFT(int n) : n(n) {
  logN = 0;
  while ((1 << logN) < n)
    logN++;
  ComputeTable();
}

FFT::~FFT() {}

void FFT::ComputeTable() {
  bitReversalTable.resize(n);
  for (int i = 0; i < n; i++) {
    int rev = 0;
    for (int j = 0; j < logN; j++) {
      if ((i >> j) & 1)
        rev |= (1 << (logN - 1 - j));
    }
    bitReversalTable[i] = rev;
  }

  w.resize(n / 2);
  for (int i = 0; i < n / 2; i++) {
    float angle = -2.0f * M_PI * i / n;
    w[i] = std::complex<float>(cos(angle), sin(angle));
  }
}

// Simple Cooley-Tukey implementation
// Note: This is an unoptimized implementation for demonstration/compatibility.
// For production, linking against Oboe / KISS FFT / FFTS is recommended.
void FFT::Transform(std::complex<float> *data, bool inverse) {
  for (int i = 0; i < n; i++) {
    if (i < bitReversalTable[i]) {
      std::swap(data[i], data[bitReversalTable[i]]);
    }
  }

  for (int len = 2; len <= n; len <<= 1) {
    int halfLen = len >> 1;
    int step = n / len;
    for (int i = 0; i < n; i += len) {
      for (int j = 0; j < halfLen; j++) {
        std::complex<float> u = data[i + j];
        std::complex<float> v = data[i + j + halfLen];
        std::complex<float> root =
            inverse ? std::conj(w[j * step]) : w[j * step];
        v *= root;
        data[i + j] = u + v;
        data[i + j + halfLen] = u - v;
      }
    }
  }

  if (inverse) {
    for (int i = 0; i < n; i++) {
      data[i] /= (float)n;
    }
  }
}

void FFT::Forward(const float *input, std::complex<float> *output) {
  // Pack real input into complex buffer
  std::vector<std::complex<float>> buffer(n);
  for (int i = 0; i < n; i++) {
    buffer[i] = std::complex<float>(input[i], 0.0f);
  }

  Transform(buffer.data(), false);

  // Store n/2 + 1 complex coefficients
  for (int i = 0; i <= n / 2; i++) {
    output[i] = buffer[i];
  }
}

void FFT::Inverse(const std::complex<float> *input, float *output) {
  // Reconstruct full Hermitian symmetric spectrum
  std::vector<std::complex<float>> buffer(n);
  for (int i = 0; i <= n / 2; i++) {
    buffer[i] = input[i];
  }
  for (int i = n / 2 + 1; i < n; i++) {
    buffer[i] = std::conj(input[n - i]);
  }

  Transform(buffer.data(), true);

  for (int i = 0; i < n; i++) {
    output[i] = buffer[i].real();
  }
}

} // namespace utils
} // namespace viper
