#include "ViPER.h"
#include "utils/Crossfeed.h"
#include <cmath>
#include <cstring>
#include <android/log.h>

ViPER::ViPER(uint32_t samplingRate) :
    viperDdc(samplingRate),
    convolver(),
    spectrumExtend(samplingRate),
    iirEqualizer(),
    colorfulMusic(samplingRate),
    fetCompressor(samplingRate),
    dynamicSystem(samplingRate),
    viperBass(samplingRate),
    viperClarity(samplingRate),
    diffSurround(samplingRate),
    cure(samplingRate),
    analogX(samplingRate),
    playbackGain(samplingRate),
    speakerCorrection(samplingRate),
    samplingRate(samplingRate) {

    this->viperDdc.SetSamplingRate(this->samplingRate);
    this->viperDdc.Reset();
    
    this->convolver.SetSamplingRate(this->samplingRate);
    this->convolver.Reset();

    this->vhe.SetSamplingRate(this->samplingRate);
    this->vhe.Reset();

    // ProcessUnit_FX::ProcessUnit_FX seeds SpectrumExtend with reference
    // frequency 0x1db0 = 7600 Hz.
    this->spectrumExtend.SetReferenceFrequency(7600);
    this->spectrumExtend.Reset();

    // Initialize equalizer with default 10 bands (IIRFilter::IIRFilter(10)).
    this->iirEqualizer.configure((int) samplingRate, viper::dsp::IIREqualizer::BandCount::BANDS_10);
}

namespace {
    // Dynamic System device tables, indexed by the ordinal of the Kotlin enum
    // com.viperplayer.domain.model.DynamicSystemDeviceType. Values are the original
    // ViPER4Android tables: {xLow, xHigh, yLow, yHigh, gainX, gainY}. gainX/gainY are in percent
    // and become linear side gains (/100), matching the "100 == unity" convention used by
    // Param::DynamicSystemBassStrength.
    struct DynSysPreset {
        uint32_t xLow, xHigh, yLow, yHigh;
        int gainX, gainY;
    };

    constexpr DynSysPreset kDynSysPresets[] = {
            {140,  6200, 40, 60,  10, 80},   // EXTREME_HEADPHONE_V2
            {180,  5800, 55, 80,  10, 70},   // HIGH_END_HEADPHONE_V2
            {300,  5600, 60, 105, 10, 50},   // COMMON_HEADPHONE_V2
            {600,  5400, 60, 105, 10, 20},   // LOW_END_HEADPHONE_V2
            {100,  5600, 40, 80,  50, 50},   // COMMON_EARPHONE_V2
            {1200, 6200, 40, 80,  0,  20},   // EXTREME_HEADPHONE_V1
            {1000, 6200, 40, 80,  0,  10},   // HIGH_END_HEADPHONE_V1
            {800,  6200, 40, 80,  10, 0},    // COMMON_HEADPHONE_V1
            {400,  6200, 40, 80,  10, 0},    // COMMON_EARPHONE_V1
            {1200, 6200, 50, 90,  15, 10},   // APPLE_EARPHONE
            {1000, 6200, 50, 90,  30, 10},   // MONSTER_EARPHONE
            {1100, 6200, 60, 100, 20, 0},    // MOTOROLA_EARPHONE
            {1200, 6200, 50, 100, 10, 50},   // PHILIPS_EARPHONE
            {1200, 6200, 60, 100, 0,  30},   // SHP2000
            {1200, 6200, 40, 80,  0,  30},   // SHP9000
            {1000, 6200, 60, 100, 0,  0},    // UNKNOWN_TYPE_I
            {1000, 6200, 60, 120, 0,  0},    // UNKNOWN_TYPE_II
            {1000, 6200, 80, 140, 0,  0},    // UNKNOWN_TYPE_III
            {800,  6200, 80, 140, 0,  0},    // UNKNOWN_TYPE_IV
            {0,    0,    0,  0,   0,  0},    // UNKNOWN_TYPE_V
            {180,  5400, 40, 60,  50, 0},    // PITT_VAN_DE_WITT_FLAVOR_1
            {1200, 6000, 40, 60,  0,  80},   // PITT_VAN_DE_WITT_FLAVOR_2
            {140,  5400, 40, 60,  0,  0},    // PITT_VAN_DE_WITT_FLAVOR_3
    };
    constexpr int kDynSysPresetCount = sizeof(kDynSysPresets) / sizeof(kDynSysPresets[0]);
    // The JNI boundary range-checks the ordinal against this count without seeing the table.
    static_assert(kDynSysPresetCount == viper::kDynamicSystemDeviceTypeCount,
                  "Dynamic System preset table and its declared count are out of sync");
} // namespace

// Writes one staged parameter to its effect. Runs on the AUDIO thread (from process), so every
// branch here must be lock-free and allocation-free — see ParameterStore for why the effects that
// own a mutex are configured directly instead of through this path.
//
// The switch deliberately has no `default`, so adding a Param without handling it is a compiler
// warning rather than a parameter that silently stops working.
void ViPER::applyParameter(viper::Param param, int32_t raw) {
    using viper::Param;
    using Store = viper::ParameterStore;

    switch (param) {
        case Param::MasterLimiterGainL:
            this->leftGain = Store::asFloat(raw);
            break;
        case Param::MasterLimiterGainR:
            this->rightGain = Store::asFloat(raw);
            break;
        case Param::MasterLimiterThresholdLimit:
            for (auto &softwareLimiter: softwareLimiters) {
                softwareLimiter.SetGate(Store::asFloat(raw));
            }
            break;

        case Param::SpectrumExtensionEnabled:
            this->spectrumExtend.SetEnable(Store::asBool(raw));
            break;
        case Param::SpectrumExtensionStrength:
            // 0-100 -> exciter value
            this->spectrumExtend.SetExciter((float) Store::asInt(raw) / 100.0f);
            break;

        case Param::FieldSurroundEnabled:
            this->colorfulMusic.SetEnable(Store::asBool(raw));
            break;
        case Param::FieldSurroundStrength:
            this->colorfulMusic.SetDepthValue((short) Store::asInt(raw));
            break;
        case Param::FieldSurroundMidImageStrength:
            this->colorfulMusic.SetMidImageValue((float) Store::asInt(raw) / 100);
            break;

        case Param::DifferentialSurroundEnabled:
            this->diffSurround.SetEnable(Store::asBool(raw));
            break;
        case Param::DifferentialSurroundDelay:
            this->diffSurround.SetDelayTime((float) Store::asInt(raw) / 100.0f);
            break;

        case Param::DynamicSystemEnabled:
            this->dynamicSystem.SetEnable(Store::asBool(raw));
            break;
        case Param::DynamicSystemDeviceType: {
            const int32_t ordinal = Store::asInt(raw);
            if (ordinal < 0 || ordinal >= kDynSysPresetCount) {
                break; // Rejected at the JNI boundary; ignore here rather than index out of range.
            }
            const DynSysPreset &preset = kDynSysPresets[ordinal];
            this->dynamicSystem.SetXCoeffs(preset.xLow, preset.xHigh);
            this->dynamicSystem.SetYCoeffs(preset.yLow, preset.yHigh);
            this->dynamicSystem.SetSideGain((float) preset.gainX / 100.0f,
                                            (float) preset.gainY / 100.0f);
            break;
        }
        case Param::DynamicSystemBassStrength:
            // 0-100 -> bass gain
            this->dynamicSystem.SetBassGain((float) Store::asInt(raw) / 100.0f);
            break;

        case Param::TubeSimulatorEnabled:
            this->tubeSimulator.SetEnable(Store::asBool(raw));
            break;

        case Param::ViperBassEnabled:
            this->viperBass.SetEnable(Store::asBool(raw));
            break;
        case Param::ViperBassMode:
            this->viperBass.SetProcessMode(static_cast<ViPERBass::ProcessMode>(Store::asInt(raw)));
            break;
        case Param::ViperBassFrequency:
            this->viperBass.SetSpeaker(Store::asInt(raw));
            break;
        case Param::ViperBassGain:
            this->viperBass.SetBassFactor((float) Store::asInt(raw) * 50.0f / 100.0f);
            break;

        case Param::ViperClarityEnabled:
            this->viperClarity.SetEnable(Store::asBool(raw));
            break;
        case Param::ViperClarityMode:
            this->viperClarity.SetProcessMode(
                    static_cast<ViPERClarity::ClarityMode>(Store::asInt(raw)));
            break;
        case Param::ViperClarityGain:
            this->viperClarity.SetClarity((float) Store::asInt(raw) * 50.0f / 100.0f);
            break;

        case Param::AuditorySystemProtectionEnabled:
            this->cure.SetEnable(Store::asBool(raw));
            break;
        case Param::AuditorySystemProtectionLevel: {
            Crossfeed::Preset preset = {};
            switch (Store::asInt(raw)) {
                case 1:
                    preset.cutoff = 650;
                    preset.feedback = 95;
                    break;
                case 2:
                    preset.cutoff = 700;
                    preset.feedback = 60;
                    break;
                case 3:
                    preset.cutoff = 700;
                    preset.feedback = 45;
                    break;
                default:
                    // Rejected at the JNI boundary; ignore here rather than apply a zeroed preset.
                    return;
            }
            this->cure.SetPreset(preset);
            break;
        }

        case Param::AnalogXEnabled:
            this->analogX.SetEnable(Store::asBool(raw));
            break;
        case Param::AnalogXLevel:
            this->analogX.SetProcessingModel(Store::asInt(raw) - 1);
            break;

        case Param::SpeakerOptimizationEnabled:
            this->speakerCorrection.SetEnable(Store::asBool(raw));
            break;

        case Param::PlaybackGainEnabled:
            this->playbackGain.SetEnable(Store::asBool(raw));
            break;
        case Param::PlaybackGainStrength: {
            // Strength 1-3 -> ratio: 1 (weak) 0.5, 2 (moderate) 1.0, 3 (strong) 2.0.
            float ratio;
            switch (Store::asInt(raw)) {
                case 1: ratio = 0.5f; break;
                case 3: ratio = 2.0f; break;
                case 2:
                default: ratio = 1.0f; break;
            }
            this->playbackGain.SetRatio(ratio);
            break;
        }
        case Param::PlaybackGainMaxGain: {
            // 1-10 maps straight through; anything above 10 means "unlimited".
            const int32_t maxGain = Store::asInt(raw);
            this->playbackGain.SetMaxGainFactor(maxGain > 10 ? 100.0f : (float) maxGain);
            break;
        }
        case Param::PlaybackGainOutputThreshold: {
            // dB (usually negative) -> linear volume.
            float threshold = Store::asFloat(raw);
            if (threshold > 0.0f) threshold = 0.0f;
            this->playbackGain.SetVolume(powf(10.0f, threshold / 20.0f));
            break;
        }

        case Param::FetCompressorEnabled:
            this->fetCompressor.SetEnable(Store::asBool(raw));
            break;
        case Param::FetCompressorThreshold:
            this->fetCompressor.SetParameter(1, Store::asInt(raw) / 100.0f);
            break;
        case Param::FetCompressorRatio:
            this->fetCompressor.SetParameter(2, Store::asInt(raw) / 100.0f);
            break;
        case Param::FetCompressorKnee:
            this->fetCompressor.SetParameter(3, Store::asInt(raw) / 100.0f);
            break;
        case Param::FetCompressorAutoKnee:
            this->fetCompressor.SetParameter(4, Store::asBool(raw) ? 1.0f : 0.0f);
            break;
        case Param::FetCompressorGain:
            this->fetCompressor.SetParameter(5, Store::asInt(raw) / 100.0f);
            break;
        case Param::FetCompressorAutoGain:
            this->fetCompressor.SetParameter(6, Store::asBool(raw) ? 1.0f : 0.0f);
            break;
        case Param::FetCompressorAttack:
            this->fetCompressor.SetParameter(7, Store::asInt(raw) / 100.0f);
            break;
        case Param::FetCompressorAutoAttack:
            this->fetCompressor.SetParameter(8, Store::asBool(raw) ? 1.0f : 0.0f);
            break;
        case Param::FetCompressorRelease:
            this->fetCompressor.SetParameter(9, Store::asInt(raw) / 100.0f);
            break;
        case Param::FetCompressorAutoRelease:
            this->fetCompressor.SetParameter(10, Store::asBool(raw) ? 1.0f : 0.0f);
            break;
        case Param::FetCompressorKneeMulti:
            this->fetCompressor.SetParameter(11, Store::asInt(raw) / 100.0f);
            break;
        case Param::FetCompressorMaxAttack:
            this->fetCompressor.SetParameter(12, Store::asInt(raw) / 100.0f);
            break;
        case Param::FetCompressorMaxRelease:
            this->fetCompressor.SetParameter(13, Store::asInt(raw) / 100.0f);
            break;
        case Param::FetCompressorCrest:
            this->fetCompressor.SetParameter(14, Store::asInt(raw) / 100.0f);
            break;
        case Param::FetCompressorAdapt:
            this->fetCompressor.SetParameter(15, Store::asInt(raw) / 100.0f);
            break;
        case Param::FetCompressorNoClip:
            this->fetCompressor.SetParameter(16, Store::asBool(raw) ? 1.0f : 0.0f);
            break;

        // UI values arrive as 0..100 and are normalised to the [0,1] Freeverb range the CRevModel
        // setters expect.
        case Param::ReverberationEnabled:
            this->reverberation.SetEnable(Store::asBool(raw));
            break;
        case Param::ReverberationRoomSize:
            this->reverberation.SetRoomSize((float) Store::asInt(raw) / 100.0f);
            break;
        case Param::ReverberationWidth:
            this->reverberation.SetWidth((float) Store::asInt(raw) / 100.0f);
            break;
        case Param::ReverberationDamp:
            this->reverberation.SetDamp((float) Store::asInt(raw) / 100.0f);
            break;
        case Param::ReverberationWet:
            this->reverberation.SetWet((float) Store::asInt(raw) / 100.0f);
            break;
        case Param::ReverberationDry:
            this->reverberation.SetDry((float) Store::asInt(raw) / 100.0f);
            break;

        case Param::kCount:
            break;
    }
}

void ViPER::process(float *buffer, uint32_t size) {
    // Take delivery of whatever the control thread staged since the last buffer, BEFORE rendering
    // this one. Applying the parameters here — on the thread that reads them — is what makes the
    // effects' unsynchronised scalar fields safe: nothing else writes them.
    this->parameters.drain([this](viper::Param param, int32_t raw) {
        this->applyParameter(param, raw);
    });

    if (size == 0) {
        return;
    }

    // Effect chain order is taken verbatim from
    // ProcessUnit_FX::processBuffer @ 00072460 for FX type 1 (the music /
    // headphone path, this+0x7c == 1). In the original the Convolver and VHE
    // run first through a resampling WaveBuffer stage; here we operate directly
    // on the interleaved float buffer, so they are simply applied in-place at
    // the head of the chain. SpeakerCorrection is intentionally absent: the
    // original only invokes it in FX type 2 (the speaker path), not in the
    // music path this engine reproduces.
    this->convolver.Process(buffer, buffer, size);
    this->vhe.Process(buffer, size);
    this->viperDdc.Process(buffer, size);
    this->spectrumExtend.Process(buffer, size);
    // IIRFilter::Process — interleaved stereo (channelCount = 2).
    this->iirEqualizer.process(buffer, size, 2);
    this->colorfulMusic.Process(buffer, size);
    this->diffSurround.Process(buffer, size);
    this->reverberation.Process(buffer, size);
    this->playbackGain.Process(buffer, size);
    // FETCompressor gates itself internally on its own enable flag (mirrors the
    // original's this+0x88 guard around FETCompressor::Process).
    this->fetCompressor.Process(buffer, size);
    this->dynamicSystem.Process(buffer, size);
    this->viperBass.Process(buffer, size);
    this->viperClarity.Process(buffer, size);
    this->cure.Process(buffer, size);
    this->tubeSimulator.TubeProcess(buffer, size);
    this->analogX.Process(buffer, size);

    // Master output gain: corresponds to AdaptiveBuffer_FPI32::ScaleFrames /
    // PanFrames in the original, applied after the chain and before the limiter.
    // The original keeps these in Q25 (unity = 0x2000000 = 2^25); here unity is
    // simply 1.0f in the float domain.
    if (this->leftGain != 1.0f || this->rightGain != 1.0f) {
        for (uint32_t i = 0; i < size * 2; i += 2) {
            buffer[i] *= this->leftGain;
            buffer[i + 1] *= this->rightGain;
        }
    }

    // SoftwareLimiter on each channel, always applied (final stage).
    for (uint32_t i = 0; i < size * 2; i += 2) {
        buffer[i] = this->softwareLimiters[0].Process(buffer[i]);
        buffer[i + 1] = this->softwareLimiters[1].Process(buffer[i + 1]);
    }
}

void ViPER::reset() {
    this->viperDdc.Reset();
    this->convolver.Reset();
    this->vhe.Reset();
    this->spectrumExtend.Reset();
    this->iirEqualizer.reset();
    this->colorfulMusic.Reset();
    this->reverberation.Reset();
    this->fetCompressor.Reset();
    this->dynamicSystem.Reset();
    this->viperBass.Reset();
    this->viperClarity.Reset();
    this->diffSurround.Reset();
    this->cure.Reset();
    this->tubeSimulator.Reset();
    this->analogX.Reset();
    this->playbackGain.Reset();
    this->speakerCorrection.Reset();
    for (auto &softwareLimiter: softwareLimiters) {
        softwareLimiter.Reset();
    }
}

void ViPER::setSamplingRate(uint32_t samplingRate) {
    // Mirrors ProcessUnit_FX::ResetAllEffects @ 000703b0: push the new sampling
    // rate to every effect that depends on it, then reset the whole chain.
    this->samplingRate = samplingRate;

    this->convolver.SetSamplingRate(samplingRate);
    this->vhe.SetSamplingRate(samplingRate);
    this->viperDdc.SetSamplingRate(samplingRate);
    this->spectrumExtend.SetSamplingRate(samplingRate);
    this->colorfulMusic.SetSamplingRate(samplingRate);
    this->fetCompressor.SetSamplingRate(samplingRate);
    this->dynamicSystem.SetSamplingRate(samplingRate);
    this->viperBass.SetSamplingRate(samplingRate);
    this->viperClarity.SetSamplingRate(samplingRate);
    this->diffSurround.SetSamplingRate(samplingRate);
    this->cure.SetSamplingRate(samplingRate);
    this->analogX.SetSamplingRate(samplingRate);
    this->playbackGain.SetSamplingRate(samplingRate);
    this->speakerCorrection.SetSamplingRate(samplingRate);

    // IIREqualizer has no rate-only setter; re-configure it for the new rate,
    // preserving the currently selected band count.
    viper::dsp::IIREqualizer::BandCount bandCount;
    switch (this->iirEqualizer.getBandCount()) {
        case 15: bandCount = viper::dsp::IIREqualizer::BandCount::BANDS_15; break;
        case 31: bandCount = viper::dsp::IIREqualizer::BandCount::BANDS_31; break;
        case 10:
        default: bandCount = viper::dsp::IIREqualizer::BandCount::BANDS_10; break;
    }
    this->iirEqualizer.configure((int) samplingRate, bandCount);

    this->reverberation.SetSamplingRate(samplingRate);

    this->reset();
}
