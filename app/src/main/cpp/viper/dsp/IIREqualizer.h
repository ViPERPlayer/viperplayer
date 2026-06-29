#ifndef VIPER_DSP_IIREQUALIZER_H
#define VIPER_DSP_IIREQUALIZER_H

#include <vector>
#include <cmath>
#include <memory>

namespace viper {
namespace dsp {

    // NOTE ON FIDELITY: this class does NOT correspond to any single class in the
    // original ViPER4Android v2505 binary. The original IIR equalizer path builds
    // min-phase IIR coefficients via `MinPhaseIIRCoeffs` (Q25 fixed-point) and runs
    // them through `IIRFilter` (both owned by other units). The original supports
    // 10/15/25/31 bands and requires a sample rate >= 44100 Hz. The peaking-biquad
    // design below, and its per-band-count frequency/Q tables, are a floating-point
    // reconstruction approximation and are NOT derived from the binary -- the
    // ground-truth coefficient math (MinPhaseIIRCoeffs::UpdateCoeffs) is too
    // corrupted in the decompilation to recover exactly. The public API is kept
    // stable because ViPER.cpp and the JNI layer depend on it.
    class IIREqualizer {
    public:
        enum class BandCount {
            BANDS_10,
            BANDS_15,
            BANDS_31
        };

        IIREqualizer();
        ~IIREqualizer();

        // Initialize or re-initialize the equalizer
        void configure(int sampleRate, BandCount bandCount);

        // Set the gain for a specific band index (db)
        void setBandGain(int bandIndex, double gainDb);
        
        void setEnabled(bool enabled);

        // Process a buffer of audio samples. 
        // Supports stereo interleaved or mono. 
        // channelCount should be 1 or 2.
        void process(float* samples, int numSamples, int channelCount);
        
        // Reset internal filter states
        void reset();

        // Get the center frequency of a band
        double getBandFrequency(int bandIndex) const;
        
        // Get the current gain of a band
        double getBandGain(int bandIndex) const;

        // Get the number of bands currently configured
        int getBandCount() const;

    private:
        struct Biquad; // Forward declaration
        
        bool mEnabled;
        int mSampleRate;
        BandCount mBandCountType;
        std::vector<double> mBandFrequencies;
        std::vector<double> mBandGains;
        std::vector<Biquad> mFiltersLeft;
        std::vector<Biquad> mFiltersRight;
        
        // Helper to setup bands based on count
        void setupBands();
        
        // Internal Biquad class definition
        struct Biquad {
            double b0, b1, b2, a1, a2;
            double z1, z2; // State variables for Transposed Direct Form II
            
            Biquad() : b0(1.0), b1(0.0), b2(0.0), a1(0.0), a2(0.0), z1(0.0), z2(0.0) {}
            
            void setCoefficients(double freq, double Q, double gainDb, int fs);
            void reset() { z1 = 0.0; z2 = 0.0; }
            
            // Inline process for performance
            inline double process(double input) {
                // Transposed Direct Form II
                // y[n] = b0*x[n] + z1[n-1]
                // z1[n] = b1*x[n] - a1*y[n] + z2[n-1]
                // z2[n] = b2*x[n] - a2*y[n]
                
                double output = b0 * input + z1;
                z1 = b1 * input - a1 * output + z2;
                z2 = b2 * input - a2 * output;
                
                // Denormal handling could be added here if necessary, 
                // but for modern CPUs usually not critical with double.
                return output;
            }
        };
    };

} // namespace dsp
} // namespace viper

#endif // VIPER_DSP_IIREQUALIZER_H
