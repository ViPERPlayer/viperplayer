#include "../utils/Biquad.h"
#include <vector>

namespace viper {
namespace effects {

class PlaybackGain {
public:
    PlaybackGain(uint32_t samplingRate);
    ~PlaybackGain();

    void SetSamplingRate(uint32_t samplingRate);
    void Reset();

    // The decompiled logic processes int* but we use float* pipeline.
    // We will adapt the logic to float.
    void Process(float *buffer, uint32_t size);

    void SetEnable(bool enable);
    void SetRatio(float ratio); // 1.0 = weak?, >1.0 = strong?
    void SetVolume(float volume);
    void SetMaxGainFactor(float maxGainFactor);

private:
    bool enabled;
    uint32_t samplingRate;

    Biquad filterL, filterR;
    
    // Parameters derived from decompiled struct
    float alpha; // field0_0x0
    double energyFactor; // field8_0x8
    float beta; // field16_0x10
    int counter; // field20_0x14
    float currentVolume; // field24_0x18 (Mapped to float)
    float maxGain; // field28_0x1c (Mapped to float)
    float currentGain; // field32_0x20 (Mapped to float)
    
    // Helper
    double AnalyseWave(float *buffer, uint32_t size);
};

} // namespace effects
} // namespace viper
