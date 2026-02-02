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
