#include <jni.h>
#include <android/log.h>
#include "viper/ViPER.h"
#include "viper/effects/ViPERBass.h"
#include "viper/effects/ViPERClarity.h"
#include "viper/utils/Crossfeed.h"

#define DEFAULT_SAMPLING_RATE 44100
static ViPER viperEngine = ViPER(DEFAULT_SAMPLING_RATE);

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

extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setDynamicSystemDeviceType(JNIEnv *env, jobject thiz, jint deviceTypeOrdinal) {
    // TODO: Map device type ordinal to actual device type configuration
    // This may require setting X/Y coefficients and side gains based on device type
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

extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setIirEqualizerBandCount(JNIEnv *env, jobject thiz, jint bandCountOrdinal) {
    viper::dsp::IIREqualizer::BandCount count;
    switch (bandCountOrdinal) {
        case 0: count = viper::dsp::IIREqualizer::BandCount::BANDS_10; break;
        case 1: count = viper::dsp::IIREqualizer::BandCount::BANDS_15; break;
        case 2: count = viper::dsp::IIREqualizer::BandCount::BANDS_31; break;
        default: count = viper::dsp::IIREqualizer::BandCount::BANDS_10; break;
    }
    viperEngine.iirEqualizer.configure(viperEngine.getSamplingRate(), count);
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

    jfloat *kernelBody = env->GetFloatArrayElements(kernel, 0);

    // Number of samples per channel
    // for mono: numSamples
    // for stereo: numSamples / 2
    uint32_t samplesPerChannel = numSamples / channels;

    if (channels == 1) {
        viperEngine.convolver.LoadKernelMono(kernelBody, samplesPerChannel);
    } else if (channels == 2) {
        viperEngine.convolver.LoadKernelStereoInterleaved(kernelBody, samplesPerChannel);
    } else {
         // Fallback or handle other channel counts if supported (e.g. quad)
         __android_log_print(ANDROID_LOG_ERROR, "ViPERPlayer", "Unsupported convolver channels: %d", channels);
    }

    env->ReleaseFloatArrayElements(kernel, kernelBody, 0);
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