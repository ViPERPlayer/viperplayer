#pragma once

#include <cmath>
#include <complex>
#include <memory>
#include <vector>
#include <fftw3.h>

namespace viper {
namespace utils {

class FFT {
public:
  FFT(int n);
  ~FFT();

  // Owns fftwf plans/buffers: must not be value-copied (shallow copy would
  // free the plans twice and leave dangling pointers). Hold via pointer.
  FFT(const FFT &) = delete;
  FFT &operator=(const FFT &) = delete;

  void Forward(const float *input, std::complex<float> *output);
  void Inverse(const std::complex<float> *input, float *output);

private:
  int n;
  float *realBuf;
  fftwf_complex *complexBuf;
  fftwf_plan pForward;
  fftwf_plan pInverse;
};

} // namespace utils
} // namespace viper
