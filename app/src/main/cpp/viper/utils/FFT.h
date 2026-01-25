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

  // Perform Forward FFT (Real to Complex)
  // Input: real array of size n
  // Output: complex array of size n/2 + 1
  void Forward(const float *input, std::complex<float> *output);

  // Perform Inverse FFT (Complex to Real)
  // Input: complex array of size n/2 + 1
  // Output: real array of size n
  void Inverse(const std::complex<float> *input, float *output);

private:
  int n;
  int logN;
  std::vector<int> bitReversalTable;
  std::vector<std::complex<float>> w;

  void ComputeTable();
  void Transform(std::complex<float> *data, bool inverse);
};

} // namespace utils
} // namespace viper
