#include "FFT.h"
#include <cstring>
#include <algorithm>
// fftw3.h is included in FFT.h

namespace viper {
namespace utils {

FFT::FFT(int n) : n(n) {
    realBuf = fftwf_alloc_real(n);
    complexBuf = fftwf_alloc_complex(n / 2 + 1);

    // Create plans
    // We use FFTW_ESTIMATE to avoid expensive tuning at startup.
    // For Android/Real-time, this is usually sufficient and consistent.
    pForward = fftwf_plan_dft_r2c_1d(n, realBuf, complexBuf, FFTW_ESTIMATE);
    pInverse = fftwf_plan_dft_c2r_1d(n, complexBuf, realBuf, FFTW_ESTIMATE);
}

FFT::~FFT() {
    if (pForward) fftwf_destroy_plan(pForward);
    if (pInverse) fftwf_destroy_plan(pInverse);
    if (realBuf) fftwf_free(realBuf);
    if (complexBuf) fftwf_free(complexBuf);
}

void FFT::Forward(const float *input, std::complex<float> *output) {
    // Copy to aligned buffer
    std::memcpy(realBuf, input, n * sizeof(float));

    // Execute
    fftwf_execute(pForward);

    // Copy to output
    // fftw_complex is binary compatible with std::complex<float> standard-wise in C++11+
    std::memcpy(output, complexBuf, (n / 2 + 1) * sizeof(std::complex<float>));
}

void FFT::Inverse(const std::complex<float> *input, float *output) {
    // Copy to aligned buffer
    std::memcpy(complexBuf, input, (n / 2 + 1) * sizeof(std::complex<float>));

    // Execute
    fftwf_execute(pInverse);

    // Scaling (FFTW is unnormalized)
    float scale = 1.0f / n;
    for (int i = 0; i < n; ++i) {
        realBuf[i] *= scale;
    }

    // Copy to output
    std::memcpy(output, realBuf, n * sizeof(float));
}

} // namespace utils
} // namespace viper
