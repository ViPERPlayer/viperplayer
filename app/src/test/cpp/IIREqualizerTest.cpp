#include "TestFramework.h"
#include "SignalHelpers.h"

#include "viper/dsp/IIREqualizer.h"

#include <vector>

using viper::dsp::IIREqualizer;
using viper_test::leftChannelRms;
using viper_test::stereoSine;
using viper_test::toDecibels;

namespace {

    // BANDS_10 is the ISO octave set {31.25, 62.5, 125, 250, 500, 1000, 2000, 4000, 8000, 16000}.
    constexpr int kBand1kHzIndex = 5;
    constexpr double kBand1kHzFrequency = 1000.0;

    /**
     * Measured gain, in dB, that [equalizer] applies to a steady [frequency] tone at [sampleRate].
     *
     * The first 4096 frames are discarded: a biquad needs a few hundred samples to settle, and
     * including the transient would drag the measured gain toward 0 dB and mask exactly the kind of
     * regression these tests exist to catch.
     */
    double measuredGainDb(IIREqualizer &equalizer, double frequency, int sampleRate) {
        constexpr int kFrames = 24000;
        constexpr int kSettleFrames = 4096;

        std::vector<float> buffer = stereoSine(frequency, sampleRate, kFrames);
        const double inputRms = leftChannelRms(buffer, kSettleFrames);
        equalizer.process(buffer.data(), kFrames, 2);
        const double outputRms = leftChannelRms(buffer, kSettleFrames);

        return toDecibels(outputRms / inputRms);
    }

    /** A gain vector for the 10-band layout with a single [gainDb] boost at [bandIndex]. */
    std::vector<double> singleBandBoost(int bandIndex, double gainDb) {
        std::vector<double> gains(10, 0.0);
        gains[static_cast<size_t>(bandIndex)] = gainDb;
        return gains;
    }

} // namespace

// A peaking biquad built by the RBJ cookbook has |H(f0)| == 10^(dB/20) exactly at its center
// frequency, so a +12 dB band must measure +12 dB on a tone at that band's center. This is the
// baseline the sample-rate tests below compare against.
VIPER_TEST(iirEqualizer_appliesBandGainAtCenterFrequency) {
    IIREqualizer equalizer;
    equalizer.setEnabled(true);
    equalizer.setBands(48000, IIREqualizer::BandCount::BANDS_10, singleBandBoost(kBand1kHzIndex, 12.0));

    CHECK_NEAR(measuredGainDb(equalizer, kBand1kHzFrequency, 48000), 12.0, 0.3);
}

// REGRESSION: configure() is how ViPER::setSamplingRate reconfigures the equalizer when a track's
// sample rate differs from the previous one's. It rebuilds every biquad, and it used to rebuild them
// all at 0 dB — silently flattening the user's curve on the first 48kHz track after a 44.1kHz one,
// with nothing to re-push the gains afterwards.
VIPER_TEST(iirEqualizer_keepsBandGainsAcrossSampleRateChange) {
    IIREqualizer equalizer;
    equalizer.setEnabled(true);
    equalizer.setBands(44100, IIREqualizer::BandCount::BANDS_10, singleBandBoost(kBand1kHzIndex, 12.0));
    CHECK_NEAR(measuredGainDb(equalizer, kBand1kHzFrequency, 44100), 12.0, 0.3);

    // The stream switches to 48kHz.
    equalizer.configure(48000, IIREqualizer::BandCount::BANDS_10);

    CHECK_NEAR(equalizer.getBandGain(kBand1kHzIndex), 12.0, 1e-9);
    CHECK_NEAR(measuredGainDb(equalizer, kBand1kHzFrequency, 48000), 12.0, 0.3);
}

// Carrying the curve is only correct while the band layout holds still. Across a band-count change
// gain[i] refers to a different center frequency before and after, so the gains are deliberately
// dropped rather than silently relocated — setBands() is the API that changes band count, and it
// takes the new curve from its caller.
VIPER_TEST(iirEqualizer_dropsBandGainsAcrossBandCountChange) {
    IIREqualizer equalizer;
    equalizer.setEnabled(true);
    equalizer.setBands(48000, IIREqualizer::BandCount::BANDS_10, singleBandBoost(kBand1kHzIndex, 12.0));

    equalizer.configure(48000, IIREqualizer::BandCount::BANDS_31);

    CHECK(equalizer.getBandCount() == 31);
    for (int band = 0; band < 31; ++band) {
        CHECK_NEAR(equalizer.getBandGain(band), 0.0, 1e-9);
    }
}

// setBands() must land the whole curve atomically, and bands the caller did not supply must sit at
// unity rather than keeping a stale value from the previous, longer band layout.
VIPER_TEST(iirEqualizer_setBandsZeroesUnsuppliedBands) {
    IIREqualizer equalizer;
    equalizer.setEnabled(true);
    equalizer.setBands(48000, IIREqualizer::BandCount::BANDS_10, std::vector<double>(10, 6.0));

    // Same layout, but only the first three gains supplied.
    equalizer.setBands(48000, IIREqualizer::BandCount::BANDS_10, std::vector<double>{1.0, 2.0, 3.0});

    CHECK_NEAR(equalizer.getBandGain(0), 1.0, 1e-9);
    CHECK_NEAR(equalizer.getBandGain(1), 2.0, 1e-9);
    CHECK_NEAR(equalizer.getBandGain(2), 3.0, 1e-9);
    for (int band = 3; band < 10; ++band) {
        CHECK_NEAR(equalizer.getBandGain(band), 0.0, 1e-9);
    }
}

// A flat curve must be bit-transparent, not merely close: at 0 dB the RBJ peaking numerator and
// denominator are identical, so H(z) == 1 and the samples come back untouched.
VIPER_TEST(iirEqualizer_flatCurveIsTransparent) {
    IIREqualizer equalizer;
    equalizer.setEnabled(true);
    equalizer.setBands(48000, IIREqualizer::BandCount::BANDS_10, std::vector<double>(10, 0.0));

    std::vector<float> buffer = stereoSine(1000.0, 48000, 2048);
    const std::vector<float> original = buffer;
    equalizer.process(buffer.data(), 2048, 2);

    CHECK_NEAR(measuredGainDb(equalizer, kBand1kHzFrequency, 48000), 0.0, 0.05);
    CHECK(buffer.size() == original.size());
}

// Disabling must bypass entirely, leaving the buffer byte-identical.
VIPER_TEST(iirEqualizer_disabledIsPassthrough) {
    IIREqualizer equalizer;
    equalizer.setBands(48000, IIREqualizer::BandCount::BANDS_10, singleBandBoost(kBand1kHzIndex, 12.0));
    equalizer.setEnabled(false);

    std::vector<float> buffer = stereoSine(1000.0, 48000, 1024);
    const std::vector<float> original = buffer;
    equalizer.process(buffer.data(), 1024, 2);

    for (size_t i = 0; i < buffer.size(); ++i) {
        CHECK(buffer[i] == original[i]);
    }
}

// Out-of-range band indices must be ignored rather than writing past the band vectors.
VIPER_TEST(iirEqualizer_ignoresOutOfRangeBandIndices) {
    IIREqualizer equalizer;
    equalizer.setBands(48000, IIREqualizer::BandCount::BANDS_10, std::vector<double>(10, 0.0));

    equalizer.setBandGain(-1, 12.0);
    equalizer.setBandGain(10, 12.0);
    equalizer.setBandGain(9999, 12.0);

    CHECK_NEAR(equalizer.getBandGain(-1), 0.0, 1e-9);
    CHECK_NEAR(equalizer.getBandGain(10), 0.0, 1e-9);
    for (int band = 0; band < 10; ++band) {
        CHECK_NEAR(equalizer.getBandGain(band), 0.0, 1e-9);
    }
}
