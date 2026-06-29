#include <cstring>
#include <cmath>
#include "Harmonic.h"

// Default harmonic spectrum: only the fundamental (1st harmonic) at unity gain,
// which makes the waveshaper an identity (Chebyshev T1(x) = x).
// Data @ 0xCE820: { 1.0, 0.0 x9 }
static const float HARMONIC_DEFAULT[] = {
        1.0f,
        0.0f,
        0.0f,
        0.0f,
        0.0f,
        0.0f,
        0.0f,
        0.0f,
        0.0f,
        0.0f,
};

Harmonic::Harmonic() {
    UpdateCoeffs(HARMONIC_DEFAULT);
    Reset();
}

double Harmonic::Process(double sample) {
    double prevLast = this->lastProcessed;

    // Evaluate the waveshaping polynomial coeffs[0] + coeffs[1]*x + ... + coeffs[10]*x^10
    // via Horner's method (x = sample).
    this->lastProcessed =
            (this->coeffs[0] + sample *
            (this->coeffs[1] + sample *
            (this->coeffs[2] + sample *
            (this->coeffs[3] + sample *
            (this->coeffs[4] + sample *
            (this->coeffs[5] + sample *
            (this->coeffs[6] + sample *
            (this->coeffs[7] + sample *
            (this->coeffs[8] + sample *
            (this->coeffs[9] + sample *
            (this->coeffs[10])))))))))));

    // Leaky-integrating differentiator: removes the DC/low-frequency component the
    // waveshaper introduces. 0.999 = 0x1ff7cee / 2^25 (Q25 leak coefficient in the original).
    this->prevOut = (this->lastProcessed + this->prevOut * 0.999) - prevLast;

    // Suppress output during the initial warm-up window so transients settle first.
    if (this->sampleCounter < this->warmupSamples) {
        this->sampleCounter++;
        return 0.0;
    }

    return this->prevOut;
}

void Harmonic::Reset() {
    this->lastProcessed = 0.0;
    this->sampleCounter = 0;
    this->prevOut = 0.0;
}

void Harmonic::SetHarmonics(const float *amplitudes) {
    UpdateCoeffs(amplitudes);
    Reset();
}

// Builds the power-series coefficients of the waveshaping polynomial
//     P(x) = sum_{n=1..10} a_n * T_n(x)
// from the requested per-harmonic amplitudes a_n, where T_n is the n-th Chebyshev
// polynomial of the first kind (cos(n*theta) = T_n(cos theta)). Driving the shaper
// with cos(theta) therefore synthesises the harmonic n at amplitude a_n.
void Harmonic::UpdateCoeffs(const float *amplitudes) {
    // normAmps[1..10] hold the (normalised) harmonic amplitudes a_1..a_10; index 0 stays 0.
    float normAmps[11];
    // prevCoeffs is the T_{n-1} buffer carried through the Chebyshev recurrence.
    float prevCoeffs[11];

    memset(normAmps, 0, 11 * sizeof(float));

    float maxAmplitude = 0.0f;
    float amplitudeSum = 0.0f;
    for (uint32_t i = 0; i < 10; i++) {
        float absAmplitude = fabsf(amplitudes[i]);
        amplitudeSum += absAmplitude;
        if (absAmplitude > maxAmplitude) {
            maxAmplitude = absAmplitude;
        }
    }
    // Warm-up length scales with the loudest harmonic. 10000.0 = 0x461c4000.
    this->warmupSamples = (uint32_t) (maxAmplitude * 10000.0f);

    memcpy(normAmps + 1, amplitudes, 10 * sizeof(float));

    // Keep the total gain <= 1 to avoid clipping; only attenuate, never boost.
    float normScale = 1.0f;
    if (amplitudeSum > 1.0f) {
        normScale = 1.0f / amplitudeSum;
    }
    for (uint32_t i = 1; i < 11; i++) {
        normAmps[i] *= normScale;
    }

    memset(this->coeffs, 0, 11 * sizeof(float));
    memset(prevCoeffs, 0, 11 * sizeof(float));

    // Accumulate sum(a_n * T_n) using the recurrence T_{n+1}(x) = 2*x*T_n(x) - T_{n-1}(x).
    this->coeffs[10] = normAmps[10];

    for (uint32_t i = 2; i < 11; i++) {
        for (uint32_t j = 0; j < i; j++) {
            float prev = prevCoeffs[i - j];
            prevCoeffs[i - j] = this->coeffs[i - j];
            this->coeffs[i - j] = this->coeffs[i - j - 1] * 2.0f - prev;
        }
        float folded = normAmps[10 - i + 1] - prevCoeffs[0];
        prevCoeffs[0] = this->coeffs[0];
        this->coeffs[0] = folded;
    }

    for (uint32_t i = 1; i < 11; i++) {
        this->coeffs[10 - i + 1] = this->coeffs[10 - i] - prevCoeffs[10 - i + 1];
    }

    // Halve the constant term per the standard Chebyshev-series convention (a_0/2).
    this->coeffs[0] = normAmps[0] / 2.0f - prevCoeffs[0];
}
