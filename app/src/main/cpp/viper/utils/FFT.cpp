#include "FFT.h"
#include <cstring>
#include <fftw3.h>
#include <algorithm>

namespace viper {
namespace utils {

struct FFT::Impl {
    int n;
    float *realBuf;
    fftwf_complex *complexBuf;
    fftwf_plan pForward;
    fftwf_plan pInverse;

    Impl(int size) : n(size) {
        realBuf = fftwf_alloc_real(n);
        complexBuf = fftwf_alloc_complex(n / 2 + 1);

        // Create plans
        // We use FFTW_ESTIMATE to avoid expensive tuning at startup.
        // For Android/Real-time, this is usually sufficient and consistent.
        pForward = fftwf_plan_dft_r2c_1d(n, realBuf, complexBuf, FFTW_ESTIMATE);
        pInverse = fftwf_plan_dft_c2r_1d(n, complexBuf, realBuf, FFTW_ESTIMATE);
    }

    ~Impl() {
        if (pForward) fftwf_destroy_plan(pForward);
        if (pInverse) fftwf_destroy_plan(pInverse);
        if (realBuf) fftwf_free(realBuf);
        if (complexBuf) fftwf_free(complexBuf);
    }
};

FFT::FFT(int n) : n(n), impl(std::make_unique<Impl>(n)) {}

FFT::~FFT() = default;

void FFT::Forward(const float *input, std::complex<float> *output) {
    if (!impl) return;

    // Copy to aligned buffer
    std::memcpy(impl->realBuf, input, n * sizeof(float));

    // Execute
    fftwf_execute(impl->pForward);

    // Copy to output
    // fftw_complex is binary compatible with std::complex<float> standard-wise in C++11+
    std::memcpy(output, impl->complexBuf, (n / 2 + 1) * sizeof(std::complex<float>));
}

void FFT::Inverse(const std::complex<float> *input, float *output) {
    if (!impl) return;

    // Copy to aligned buffer
    std::memcpy(impl->complexBuf, input, (n / 2 + 1) * sizeof(std::complex<float>));

    // Execute
    fftwf_execute(impl->pInverse);

    // Scaling (FFTW is unnormalized)
    float scale = 1.0f / n;
    // We can use NEON here manually or let compiler vectorize loop
    // Since we are copying anyway, we could scale during copy?
    // But memcpy is fast.
    // Let's optimize: scale in place then copy, OR copy then scale.
    // Scaling aligned buffer might be faster with auto-vectorization.
    for (int i = 0; i < n; ++i) {
        impl->realBuf[i] *= scale;
    }

    // Copy to output
    std::memcpy(output, impl->realBuf, n * sizeof(float));
}

} // namespace utils
} // namespace viper
