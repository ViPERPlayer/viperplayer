#pragma once

#include <cmath>
#include <complex>
#include <memory>
#include <vector>

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
  
  // FFTW Plans
  struct Impl;
  std::unique_ptr<Impl> impl;
};

} // namespace utils
} // namespace viper
