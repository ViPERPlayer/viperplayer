#include <jni.h>
#include <vector>
#include <android/log.h>
#include "viper/ViPER.h"
#include "viper/effects/ViPERBass.h"
#include "viper/effects/ViPERClarity.h"
#include "viper/utils/Crossfeed.h"
#include "xdl.h"

#define DEFAULT_SAMPLING_RATE 44100
static ViPER viperEngine = ViPER(DEFAULT_SAMPLING_RATE);

// Reconfigure the engine for the stream's actual sample rate (rate-dependent DSP coefficients).
extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setSamplingRate(JNIEnv *env, jobject thiz, jint samplingRate) {
    viperEngine.setSamplingRate((uint32_t) samplingRate);
}

// Master Limiter
extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setMasterLimiterOutputGain(JNIEnv *env, jobject thiz, jfloat gainL, jfloat gainR) {
    viperEngine.setGain(gainL, gainR);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setMasterLimiterThresholdLimit(JNIEnv *env, jobject thiz, jfloat threshold) {
    viperEngine.setThresholdLimit(threshold);
}

// Spectrum Extension
extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setSpectrumExtensionEnabled(JNIEnv *env, jobject thiz, jboolean enabled) {
    viperEngine.spectrumExtend.SetEnable(enabled);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setSpectrumExtensionStrength(JNIEnv *env, jobject thiz, jint strength) {
    // Convert 0-100 to actual exciter value
    float exciter = (float) strength / 100.0f;
    viperEngine.spectrumExtend.SetExciter(exciter);
}

// Field Surround (Reverberation)
extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setFieldSurroundEnabled(JNIEnv *env, jobject thiz, jboolean enabled) {
    viperEngine.colorfulMusic.SetEnable(enabled);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setFieldSurroundStrength(JNIEnv *env, jobject thiz, jint strength) {
    viperEngine.colorfulMusic.SetDepthValue((short) strength);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setFieldSurroundMidImageStrength(JNIEnv *env, jobject thiz, jint strength) {
    float midImageValue = (float) strength / 100;
    viperEngine.colorfulMusic.SetMidImageValue(midImageValue);
}

// Differential Surround
extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setDifferentialSurroundEnabled(JNIEnv *env, jobject thiz, jboolean enabled) {
    viperEngine.diffSurround.SetEnable(enabled);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setDifferentialSurroundDelay(JNIEnv *env, jobject thiz, jint delay) {
    float delayTime = (float) delay / 100.0f;
    viperEngine.diffSurround.SetDelayTime(delayTime);
}

// Dynamic System
extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setDynamicSystemEnabled(JNIEnv *env, jobject thiz, jboolean enabled) {
    viperEngine.dynamicSystem.SetEnable(enabled);
}

// Dynamic System device presets.
// These mirror, in declaration order, the Kotlin enum
// com.viperplayer.domain.model.DynamicSystemDeviceType so the JNI signature
// setDynamicSystemDeviceType(ordinal) stays unchanged. The values are the
// original ViPER4Android device tables: {xLow, xHigh, yLow, yHigh, gainX, gainY}.
// gainX/gainY are sent in percent and converted to linear side gains (/100),
// matching the "100 == unity" convention used by setDynamicSystemBassStrength.
namespace {
    struct DynSysPreset {
        uint32_t xLow, xHigh, yLow, yHigh;
        int gainX, gainY;
    };
    constexpr DynSysPreset kDynSysPresets[] = {
            {140, 6200, 40, 60, 10, 80},   // EXTREME_HEADPHONE_V2
            {180, 5800, 55, 80, 10, 70},   // HIGH_END_HEADPHONE_V2
            {300, 5600, 60, 105, 10, 50},  // COMMON_HEADPHONE_V2
            {600, 5400, 60, 105, 10, 20},  // LOW_END_HEADPHONE_V2
            {100, 5600, 40, 80, 50, 50},   // COMMON_EARPHONE_V2
            {1200, 6200, 40, 80, 0, 20},   // EXTREME_HEADPHONE_V1
            {1000, 6200, 40, 80, 0, 10},   // HIGH_END_HEADPHONE_V1
            {800, 6200, 40, 80, 10, 0},    // COMMON_HEADPHONE_V1
            {400, 6200, 40, 80, 10, 0},    // COMMON_EARPHONE_V1
            {1200, 6200, 50, 90, 15, 10},  // APPLE_EARPHONE
            {1000, 6200, 50, 90, 30, 10},  // MONSTER_EARPHONE
            {1100, 6200, 60, 100, 20, 0},  // MOTOROLA_EARPHONE
            {1200, 6200, 50, 100, 10, 50}, // PHILIPS_EARPHONE
            {1200, 6200, 60, 100, 0, 30},  // SHP2000
            {1200, 6200, 40, 80, 0, 30},   // SHP9000
            {1000, 6200, 60, 100, 0, 0},   // UNKNOWN_TYPE_I
            {1000, 6200, 60, 120, 0, 0},   // UNKNOWN_TYPE_II
            {1000, 6200, 80, 140, 0, 0},   // UNKNOWN_TYPE_III
            {800, 6200, 80, 140, 0, 0},    // UNKNOWN_TYPE_IV
            {0, 0, 0, 0, 0, 0},            // UNKNOWN_TYPE_V
            {180, 5400, 40, 60, 50, 0},    // PITT_VAN_DE_WITT_FLAVOR_1
            {1200, 6000, 40, 60, 0, 80},   // PITT_VAN_DE_WITT_FLAVOR_2
            {140, 5400, 40, 60, 0, 0},     // PITT_VAN_DE_WITT_FLAVOR_3
    };
    constexpr int kDynSysPresetCount = sizeof(kDynSysPresets) / sizeof(kDynSysPresets[0]);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setDynamicSystemDeviceType(JNIEnv *env, jobject thiz, jint deviceTypeOrdinal) {
    if (deviceTypeOrdinal < 0 || deviceTypeOrdinal >= kDynSysPresetCount) {
        __android_log_print(ANDROID_LOG_ERROR, "ViPERPlayer", "Invalid dynamic system device type: %d", deviceTypeOrdinal);
        return;
    }

    const DynSysPreset &preset = kDynSysPresets[deviceTypeOrdinal];
    viperEngine.dynamicSystem.SetXCoeffs(preset.xLow, preset.xHigh);
    viperEngine.dynamicSystem.SetYCoeffs(preset.yLow, preset.yHigh);
    viperEngine.dynamicSystem.SetSideGain((float) preset.gainX / 100.0f, (float) preset.gainY / 100.0f);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setDynamicSystemBassStrength(JNIEnv *env, jobject thiz, jint strength) {
    // Convert 0-100 to bass gain
    float bassGain = (float) strength / 100.0f;
    viperEngine.dynamicSystem.SetBassGain(bassGain);
}

// Tube Simulator
extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setTubeSimulatorEnabled(JNIEnv *env, jobject thiz, jboolean enabled) {
    viperEngine.tubeSimulator.SetEnable(enabled);
}

// ViPER Bass
extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setViperBassEnabled(JNIEnv *env, jobject thiz, jboolean enabled) {
    viperEngine.viperBass.SetEnable(enabled);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setViperBassMode(JNIEnv *env, jobject thiz, jint mode) {
    ViPERBass::ProcessMode processMode = static_cast<ViPERBass::ProcessMode>(mode);
    viperEngine.viperBass.SetProcessMode(processMode);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setViperBassFrequency(JNIEnv *env, jobject thiz, jint frequency) {
    viperEngine.viperBass.SetSpeaker(frequency);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setViperBassGain(JNIEnv *env, jobject thiz, jint gain) {
    float bassFactor = (float) gain * 50.0f / 100.0f;
    viperEngine.viperBass.SetBassFactor(bassFactor);
}

// ViPER Clarity
extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setViperClarityEnabled(JNIEnv *env, jobject thiz, jboolean enabled) {
    viperEngine.viperClarity.SetEnable(enabled);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setViperClarityMode(JNIEnv *env, jobject thiz, jint mode) {
    ViPERClarity::ClarityMode clarityMode = static_cast<ViPERClarity::ClarityMode>(mode);
    viperEngine.viperClarity.SetProcessMode(clarityMode);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setViperClarityGain(JNIEnv *env, jobject thiz, jint gain) {
    float clarityGain = (float) gain * 50.0f / 100.0f;
    viperEngine.viperClarity.SetClarity(clarityGain);
}

// Auditory System Protection (Cure)
extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setAuditorySystemProtectionEnabled(JNIEnv *env, jobject thiz, jboolean enabled) {
    viperEngine.cure.SetEnable(enabled);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setAuditorySystemProtectionLevel(JNIEnv *env, jobject thiz, jint level) {
    struct Crossfeed::Preset preset = {};

    switch (level) {
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
            __android_log_print(ANDROID_LOG_ERROR, "ViPERPlayer", "Invalid cure level: %d", level);
            return;
    }

    viperEngine.cure.SetPreset(preset);
}

// Analog X
extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setAnalogXEnabled(JNIEnv *env, jobject thiz, jboolean enabled) {
    viperEngine.analogX.SetEnable(enabled);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setAnalogXLevel(JNIEnv *env, jobject thiz, jint level) {
    viperEngine.analogX.SetProcessingModel(level - 1);
}

// Speaker Optimization
extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setSpeakerOptimizationEnabled(JNIEnv *env, jobject thiz, jboolean enabled) {
    viperEngine.speakerCorrection.SetEnable(enabled);
}

// IIR Equalizer
extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setIirEqualizerEnabled(JNIEnv *env, jobject thiz, jboolean enabled) {
    viperEngine.iirEqualizer.setEnabled(enabled);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setIirEqualizerBandLevel(JNIEnv *env, jobject thiz, jint bandIndex, jfloat level) {
    viperEngine.iirEqualizer.setBandGain(bandIndex, (double)level);
}

namespace {
    viper::dsp::IIREqualizer::BandCount iirBandCountFromOrdinal(jint ordinal) {
        switch (ordinal) {
            case 1: return viper::dsp::IIREqualizer::BandCount::BANDS_15;
            case 2: return viper::dsp::IIREqualizer::BandCount::BANDS_31;
            default: return viper::dsp::IIREqualizer::BandCount::BANDS_10;
        }
    }
}

// Apply the whole EQ (band count + every gain) in ONE call. Issuing setIirEqualizerBandCount
// followed by N setIirEqualizerBandLevel calls let the audio thread render buffers part-way
// through the sequence — against the new band count with old gains, or with only some bands
// updated. The band-count switch (10 -> 31) was the audible case.
extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setIirEqualizerBands(JNIEnv *env, jobject thiz, jint bandCountOrdinal, jfloatArray gains) {
    std::vector<double> gainsDb;
    if (gains != nullptr) {
        const jsize n = env->GetArrayLength(gains);
        jfloat *body = env->GetFloatArrayElements(gains, nullptr);
        if (body == nullptr) return; // OOM; pending exception throws on return to Java
        gainsDb.reserve((size_t) n);
        for (jsize i = 0; i < n; ++i) gainsDb.push_back((double) body[i]);
        env->ReleaseFloatArrayElements(gains, body, JNI_ABORT);
    }
    viperEngine.iirEqualizer.setBands(
            (int) viperEngine.getSamplingRate(), iirBandCountFromOrdinal(bandCountOrdinal), gainsDb);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setIirEqualizerBandCount(JNIEnv *env, jobject thiz, jint bandCountOrdinal) {
    viperEngine.iirEqualizer.configure(
            viperEngine.getSamplingRate(), iirBandCountFromOrdinal(bandCountOrdinal));
}

// ViPER DDC
extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setViperDdcEnabled(JNIEnv *env, jobject thiz, jboolean enabled) {
    viperEngine.viperDdc.SetEnable(enabled);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_viperDdcClearCoeffs(JNIEnv *env, jobject thiz) {
    viperEngine.viperDdc.ClearCoeffs();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_viperDdcAddCoeffs(JNIEnv *env, jobject thiz, jint samplingRate, jfloatArray coeffs) {
    jsize numCoeffs = env->GetArrayLength(coeffs);
    if (numCoeffs == 0) return;

    jfloat *coeffsBody = env->GetFloatArrayElements(coeffs, 0);

    // Coeffs are flattened [L_b0, L_b1... R_b0...] for each band.
    // Each biquad is 5 floats.
    // Total coeffs = bands * 5.
    uint32_t bands = numCoeffs / 5;
    std::vector<std::array<float, 5>> rateCoeffs(bands);

    for (uint32_t j = 0; j < bands; j++) {
        rateCoeffs[j][0] = coeffsBody[j * 5 + 0];
        rateCoeffs[j][1] = coeffsBody[j * 5 + 1];
        rateCoeffs[j][2] = coeffsBody[j * 5 + 2];
        rateCoeffs[j][3] = coeffsBody[j * 5 + 3];
        rateCoeffs[j][4] = coeffsBody[j * 5 + 4];
    }

    viperEngine.viperDdc.AddCoeffs((uint32_t)samplingRate, rateCoeffs);

    env->ReleaseFloatArrayElements(coeffs, coeffsBody, 0);
}

// Convolver
extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setConvolverEnabled(JNIEnv *env, jobject thiz, jboolean enabled) {
    viperEngine.convolver.SetEnable(enabled);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setConvolverImpulseResponse(JNIEnv *env, jobject thiz, jint channels, jfloatArray kernel) {
    jsize numSamples = env->GetArrayLength(kernel);
    if (numSamples == 0) {
        viperEngine.convolver.UnloadKernel();
        return;
    }

    // Validate the channel count BEFORE dividing by it — a zero (or negative) count reaching the
    // division below is an integer-division fault, not a catchable exception.
    if (channels != 1 && channels != 2) {
        __android_log_print(ANDROID_LOG_ERROR, "ViPERPlayer", "Unsupported convolver channels: %d", channels);
        return;
    }

    jfloat *kernelBody = env->GetFloatArrayElements(kernel, nullptr);
    if (kernelBody == nullptr) {
        // OOM; the pending exception is thrown when we return to Java.
        return;
    }

    // Number of samples per channel
    // for mono: numSamples
    // for stereo: numSamples / 2
    uint32_t samplesPerChannel = (uint32_t) (numSamples / channels);

    if (channels == 1) {
        viperEngine.convolver.LoadKernelMono(kernelBody, samplesPerChannel);
    } else {
        viperEngine.convolver.LoadKernelStereoInterleaved(kernelBody, samplesPerChannel);
    }

    // JNI_ABORT: the kernel is read-only here, so skip copying the (unmodified) buffer back.
    env->ReleaseFloatArrayElements(kernel, kernelBody, JNI_ABORT);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setConvolverCrossChannel(JNIEnv *env, jobject thiz, jint crossChannel) {
    float level = (float) crossChannel / 100.0f;
    viperEngine.convolver.SetCrossChannel(level);
}

// Playback Gain Control
extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setPlaybackGainEnabled(JNIEnv *env, jobject thiz,
                                                                         jboolean enabled) {
    viperEngine.playbackGain.SetEnable(enabled);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setPlaybackGainStrength(JNIEnv *env, jobject thiz,
                                                                          jint strength) {
    // Map strength (1-3) to Ratio (float)
    // 1 (Weak) -> 0.5
    // 2 (Moderate) -> 1.0
    // 3 (Strong) -> 2.0
    float ratio = 1.0f;
    switch(strength) {
        case 1: ratio = 0.5f; break;
        case 2: ratio = 1.0f; break;
        case 3: ratio = 2.0f; break;
        default: ratio = 1.0f; break;
    }
    viperEngine.playbackGain.SetRatio(ratio);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setPlaybackGainMaxGain(JNIEnv *env, jobject thiz,
                                                                         jint max_gain) {
    // Map max_gain (1-10, >10=Inf) to Factor
    // >10 -> Infinite (e.g. 100x)
    float factor = (float)max_gain;
    if (max_gain > 10) factor = 100.0f;
    viperEngine.playbackGain.SetMaxGainFactor(factor);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setPlaybackGainOutputThreshold(JNIEnv *env,
                                                                                 jobject thiz,
                                                                                 jfloat threshold) {
    // Map threshold (dB) to Volume (linear)
    // threshold is usually negative (e.g. -1.0dB).
    if (threshold > 0.0f) threshold = 0.0f;
    float volume = powf(10.0f, threshold / 20.0f);
    viperEngine.playbackGain.SetVolume(volume);
}

// FET Compressor
extern "C" JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setFetCompressorEnabled(JNIEnv *env, jobject thiz, jboolean enabled) {
    viperEngine.fetCompressor.SetEnable(enabled);
}

extern "C" JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setFetCompressorThreshold(JNIEnv *env, jobject thiz, jint value) {
    viperEngine.fetCompressor.SetParameter(1, value / 100.0f);
}

extern "C" JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setFetCompressorRatio(JNIEnv *env, jobject thiz, jint value) {
    viperEngine.fetCompressor.SetParameter(2, value / 100.0f);
}

extern "C" JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setFetCompressorKnee(JNIEnv *env, jobject thiz, jint value) {
    viperEngine.fetCompressor.SetParameter(3, value / 100.0f);
}

extern "C" JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setFetCompressorAutoKnee(JNIEnv *env, jobject thiz, jboolean enabled) {
    viperEngine.fetCompressor.SetParameter(4, enabled ? 1.0f : 0.0f);
}

extern "C" JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setFetCompressorGain(JNIEnv *env, jobject thiz, jint value) {
    viperEngine.fetCompressor.SetParameter(5, value / 100.0f);
}

extern "C" JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setFetCompressorAutoGain(JNIEnv *env, jobject thiz, jboolean enabled) {
    viperEngine.fetCompressor.SetParameter(6, enabled ? 1.0f : 0.0f);
}

extern "C" JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setFetCompressorAttack(JNIEnv *env, jobject thiz, jint value) {
    viperEngine.fetCompressor.SetParameter(7, value / 100.0f);
}

extern "C" JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setFetCompressorAutoAttack(JNIEnv *env, jobject thiz, jboolean enabled) {
    viperEngine.fetCompressor.SetParameter(8, enabled ? 1.0f : 0.0f);
}

extern "C" JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setFetCompressorRelease(JNIEnv *env, jobject thiz, jint value) {
    viperEngine.fetCompressor.SetParameter(9, value / 100.0f);
}

extern "C" JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setFetCompressorAutoRelease(JNIEnv *env, jobject thiz, jboolean enabled) {
    viperEngine.fetCompressor.SetParameter(10, enabled ? 1.0f : 0.0f);
}

extern "C" JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setFetCompressorKneeMulti(JNIEnv *env, jobject thiz, jint value) {
    viperEngine.fetCompressor.SetParameter(11, value / 100.0f);
}

extern "C" JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setFetCompressorMaxAttack(JNIEnv *env, jobject thiz, jint value) {
    viperEngine.fetCompressor.SetParameter(12, value / 100.0f);
}

extern "C" JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setFetCompressorMaxRelease(JNIEnv *env, jobject thiz, jint value) {
    viperEngine.fetCompressor.SetParameter(13, value / 100.0f);
}

extern "C" JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setFetCompressorCrest(JNIEnv *env, jobject thiz, jint value) {
    viperEngine.fetCompressor.SetParameter(14, value / 100.0f);
}

extern "C" JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setFetCompressorAdapt(JNIEnv *env, jobject thiz, jint value) {
    viperEngine.fetCompressor.SetParameter(15, value / 100.0f);
}

extern "C" JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setFetCompressorNoClip(JNIEnv *env, jobject thiz, jboolean enabled) {
    viperEngine.fetCompressor.SetParameter(16, enabled ? 1.0f : 0.0f);
}

// ViPER Headphone Surround+ (VHE)
extern "C" JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setHeadphoneSurroundEnabled(JNIEnv *env, jobject thiz, jboolean enabled) {
    viperEngine.vhe.SetEnable(enabled);
}

extern "C" JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setHeadphoneSurroundLevel(JNIEnv *env, jobject thiz, jint level) {
    viperEngine.vhe.SetEffectLevel((int) level);
}

// Reverberation
// The reverberation member sits in the engine's effect chain (ViPER::process).
// Wiring mirrors the ViperBass pattern: the JNI functions call the public member's
// setters directly. UI values arrive as 0..100 and are normalised to the [0,1]
// Freeverb range the CRevModel setters expect.
extern "C" JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setReverberationEnabled(JNIEnv *env, jobject thiz, jboolean enabled) {
    viperEngine.reverberation.SetEnable(enabled);
}

extern "C" JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setReverberationRoomSize(JNIEnv *env, jobject thiz, jint value) {
    viperEngine.reverberation.SetRoomSize((float) value / 100.0f);
}

extern "C" JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setReverberationWidth(JNIEnv *env, jobject thiz, jint value) {
    viperEngine.reverberation.SetWidth((float) value / 100.0f);
}

extern "C" JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setReverberationDamp(JNIEnv *env, jobject thiz, jint value) {
    viperEngine.reverberation.SetDamp((float) value / 100.0f);
}

extern "C" JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setReverberationWet(JNIEnv *env, jobject thiz, jint value) {
    viperEngine.reverberation.SetWet((float) value / 100.0f);
}

extern "C" JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setReverberationDry(JNIEnv *env, jobject thiz, jint value) {
    viperEngine.reverberation.SetDry((float) value / 100.0f);
}

// Common
extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_process(JNIEnv *env, jobject thiz,
                                                           jobject buffer, jint offset, jint size) {
    uint8_t *data = static_cast<uint8_t *>(env->GetDirectBufferAddress(buffer));

    viperEngine.process((float *) (data + offset), size / 4 / 2);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_reset(JNIEnv *env, jobject thiz) {
    viperEngine.reset();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_MainActivity_nativeInit(JNIEnv *env, jobject thiz) {
    void *libaudioclient_handle = xdl_open("libaudioclient.so", XDL_DEFAULT);
    __android_log_print(ANDROID_LOG_INFO, "ViPERPlayer", "libaudioclient.so -> %p", libaudioclient_handle);

    void *libutils_handle = xdl_open("libutils.so", XDL_DEFAULT);
    __android_log_print(ANDROID_LOG_INFO, "ViPERPlayer", "libutils.so -> %p", libutils_handle);

    if (android_get_device_api_level() >= 31) {
        void *libpermission_handle = xdl_open("libpermission.so", XDL_DEFAULT);
        __android_log_print(ANDROID_LOG_INFO, "ViPERPlayer", "libpermission.so -> %p", libpermission_handle);
    }

    if (android_get_device_api_level() >= 31) {
        void *libandroid_runtime_handle = xdl_open("libandroid_runtime.so", XDL_DEFAULT);
        __android_log_print(ANDROID_LOG_INFO, "ViPERPlayer", "libandroid_runtime.so -> %p", libandroid_runtime_handle);
    }

    void *libbinder_handle = xdl_open("libbinder.so", XDL_DEFAULT);
    __android_log_print(ANDROID_LOG_INFO, "ViPERPlayer", "libbinder.so -> %p", libbinder_handle);
}
