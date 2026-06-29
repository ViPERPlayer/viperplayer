#include "IIREqualizer.h"
#include <cmath>

namespace viper {
namespace dsp {

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

    // --- Biquad Implementation ---

    void IIREqualizer::Biquad::setCoefficients(double freq, double Q, double gainDb, int fs) {
        if (fs <= 0) return;
        
        double A = std::pow(10.0, gainDb / 40.0);
        double w0 = 2.0 * M_PI * freq / fs;
        double alpha = std::sin(w0) / (2.0 * Q);
        double cosW0 = std::cos(w0);

        // Peaking EQ coefficients (RBJ Audio EQ Cookbook)
        double b0_raw = 1.0 + alpha * A;
        double b1_raw = -2.0 * cosW0;
        double b2_raw = 1.0 - alpha * A;
        double a0_raw = 1.0 + alpha / A;
        double a1_raw = -2.0 * cosW0;
        double a2_raw = 1.0 - alpha / A;

        // Normalize by a0
        b0 = b0_raw / a0_raw;
        b1 = b1_raw / a0_raw;
        b2 = b2_raw / a0_raw;
        a1 = a1_raw / a0_raw;
        a2 = a2_raw / a0_raw;
    }


    // --- IIREqualizer Implementation ---

    IIREqualizer::IIREqualizer() : mEnabled(true), mSampleRate(48000), mBandCountType(BandCount::BANDS_10) {
        configure(48000, BandCount::BANDS_10);
    }

    IIREqualizer::~IIREqualizer() = default;

    void IIREqualizer::configure(int sampleRate, BandCount bandCount) {
        mSampleRate = sampleRate;
        mBandCountType = bandCount;
        setupBands();
        reset();
    }

    void IIREqualizer::setupBands() {
        mBandFrequencies.clear();
        // NOTE: these center frequencies and Q values are reconstruction
        // approximations (standard ISO octave fractions / constant-Q bandwidth).
        // They are NOT the binary's tables -- the original min-phase coefficient
        // generator (MinPhaseIIRCoeffs::GetIndexFrequency) keeps per-band-count
        // frequency tables in .rodata that could not be extracted from the
        // provided section-relative dump. The original also supports a 25-band
        // mode (not represented by this 3-value enum); adding it would require
        // changing the enum + JNI ordinal mapping in another unit.
        double qFactor = 1.414;

        switch (mBandCountType) {
            case BandCount::BANDS_10: // ISO octave bands
                mBandFrequencies = {31.25, 62.5, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0};
                qFactor = 1.414; // ~1 octave bandwidth (unverified vs binary)
                break;
            case BandCount::BANDS_15: // ISO 2/3 octave bands
                 mBandFrequencies = {25.0, 40.0, 63.0, 100.0, 160.0, 250.0, 400.0, 630.0, 1000.0, 1600.0, 2500.0, 4000.0, 6300.0, 10000.0, 16000.0};
                 qFactor = 2.145; // ~2/3 octave bandwidth (unverified vs binary)
                 break;
            case BandCount::BANDS_31: // ISO 1/3 octave bands
                mBandFrequencies = {20.0, 25.0, 31.5, 40.0, 50.0, 63.0, 80.0, 100.0, 125.0, 160.0, 200.0, 250.0, 315.0, 400.0, 500.0, 630.0, 800.0, 1000.0, 1250.0, 1600.0, 2000.0, 2500.0, 3150.0, 4000.0, 5000.0, 6300.0, 8000.0, 10000.0, 12500.0, 16000.0, 20000.0};
                qFactor = 4.318; // ~1/3 octave bandwidth (unverified vs binary)
                break;
        }

        mFiltersLeft.clear();
        mFiltersRight.clear();
        mBandGains.assign(mBandFrequencies.size(), 0.0);

        for (double freq : mBandFrequencies) {
            auto filterL = Biquad();
            auto filterR = Biquad();
            
            // Initialize with 0dB gain
            filterL.setCoefficients(freq, qFactor, 0.0, mSampleRate);
            filterR.setCoefficients(freq, qFactor, 0.0, mSampleRate);

            mFiltersLeft.push_back(filterL);
            mFiltersRight.push_back(filterR);
        }
    }

    void IIREqualizer::setBandGain(int bandIndex, double gainDb) {
        if (bandIndex < 0 || bandIndex >= mBandFrequencies.size()) return;
        
        mBandGains[bandIndex] = gainDb;

        double freq = mBandFrequencies[bandIndex];
        double qFactor = 1.414;
         switch (mBandCountType) {
            case BandCount::BANDS_10: qFactor = 1.414; break;
            case BandCount::BANDS_15: qFactor = 2.145; break;
            case BandCount::BANDS_31: qFactor = 4.318; break;
        }

        if (bandIndex < mFiltersLeft.size()) {
            mFiltersLeft[bandIndex].setCoefficients(freq, qFactor, gainDb, mSampleRate);
        }
        if (bandIndex < mFiltersRight.size()) {
            mFiltersRight[bandIndex].setCoefficients(freq, qFactor, gainDb, mSampleRate);
        }
    }

    void IIREqualizer::reset() {
        for (auto& filter : mFiltersLeft) filter.reset();
        for (auto& filter : mFiltersRight) filter.reset();
    }
    
    void IIREqualizer::setEnabled(bool enabled) {
        mEnabled = enabled;
        if (!enabled) {
            reset();
        }
    }

    void IIREqualizer::process(float* samples, int numSamples, int channelCount) {
        if (!mEnabled) return;

        if (channelCount == 1) {
            for (int i = 0; i < numSamples; ++i) {
                double sample = static_cast<double>(samples[i]);
                for (auto& filter : mFiltersLeft) {
                    sample = filter.process(sample);
                }
                samples[i] = static_cast<float>(sample);
            }
        } else if (channelCount == 2) {
             // Interleaved stereo: L, R, L, R...
             // Loop iterates in steps of 2
             for (int i = 0; i < numSamples * 2; i += 2) {
                 double left = static_cast<double>(samples[i]);
                 double right = static_cast<double>(samples[i+1]);

                 for (auto& filter : mFiltersLeft) {
                     left = filter.process(left);
                 }
                 for (auto& filter : mFiltersRight) {
                     right = filter.process(right);
                 }

                 samples[i] = static_cast<float>(left);
                 samples[i+1] = static_cast<float>(right);
             }
        }
        // Channel counts other than 1 or 2 are not handled.
    }

    double IIREqualizer::getBandFrequency(int bandIndex) const {
         if (bandIndex < 0 || bandIndex >= mBandFrequencies.size()) return 0.0;
         return mBandFrequencies[bandIndex];
    }
    
    double IIREqualizer::getBandGain(int bandIndex) const {
         if (bandIndex < 0 || bandIndex >= mBandGains.size()) return 0.0;
         return mBandGains[bandIndex];
    }

    int IIREqualizer::getBandCount() const {
        return mBandFrequencies.size();
    }

} // namespace dsp
} // namespace viper
