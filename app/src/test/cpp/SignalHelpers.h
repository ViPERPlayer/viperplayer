#ifndef VIPER_TEST_SIGNAL_HELPERS_H
#define VIPER_TEST_SIGNAL_HELPERS_H

// Signal generation / measurement shared by the DSP tests. Measuring an effect's actual frequency
// response is what makes these tests worth having: asserting on getters only proves a value round
// trips through a setter, whereas pushing a tone through process() and measuring the output proves
// the filter coefficients really were rebuilt.

#include <cmath>
#include <vector>

namespace viper_test {

    constexpr double kPi = 3.14159265358979323846;

    /** Interleaved stereo sine at [frequency] Hz, [frames] frames long, amplitude 1.0. */
    inline std::vector<float> stereoSine(double frequency, int sampleRate, int frames) {
        std::vector<float> buffer(static_cast<size_t>(frames) * 2);
        for (int i = 0; i < frames; ++i) {
            const auto sample = static_cast<float>(
                    std::sin(2.0 * kPi * frequency * i / sampleRate));
            buffer[static_cast<size_t>(i) * 2] = sample;
            buffer[static_cast<size_t>(i) * 2 + 1] = sample;
        }
        return buffer;
    }

    /**
     * RMS of the left channel of an interleaved stereo buffer, ignoring the first [skipFrames]
     * frames so the filter's transient response does not pollute the measurement.
     */
    inline double leftChannelRms(const std::vector<float> &interleaved, int skipFrames) {
        const size_t frames = interleaved.size() / 2;
        if (static_cast<size_t>(skipFrames) >= frames) return 0.0;
        double sum = 0.0;
        for (size_t i = static_cast<size_t>(skipFrames); i < frames; ++i) {
            const double sample = interleaved[i * 2];
            sum += sample * sample;
        }
        return std::sqrt(sum / static_cast<double>(frames - static_cast<size_t>(skipFrames)));
    }

    /** Linear amplitude ratio expressed in decibels. */
    inline double toDecibels(double ratio) {
        return 20.0 * std::log10(ratio);
    }

} // namespace viper_test

#endif // VIPER_TEST_SIGNAL_HELPERS_H
