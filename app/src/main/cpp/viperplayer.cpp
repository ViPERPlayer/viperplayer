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
    viperEngine.parameters.setFloat(viper::Param::MasterLimiterGainL, gainL);
    viperEngine.parameters.setFloat(viper::Param::MasterLimiterGainR, gainR);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setMasterLimiterThresholdLimit(JNIEnv *env, jobject thiz, jfloat threshold) {
    viperEngine.parameters.setFloat(viper::Param::MasterLimiterThresholdLimit, threshold);
}

// Spectrum Extension
extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setSpectrumExtensionEnabled(JNIEnv *env, jobject thiz, jboolean enabled) {
    viperEngine.parameters.setBool(viper::Param::SpectrumExtensionEnabled, enabled);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setSpectrumExtensionStrength(JNIEnv *env, jobject thiz, jint strength) {
    viperEngine.parameters.setInt(viper::Param::SpectrumExtensionStrength, strength);
}

// Field Surround (Reverberation)
extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setFieldSurroundEnabled(JNIEnv *env, jobject thiz, jboolean enabled) {
    viperEngine.parameters.setBool(viper::Param::FieldSurroundEnabled, enabled);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setFieldSurroundStrength(JNIEnv *env, jobject thiz, jint strength) {
    viperEngine.parameters.setInt(viper::Param::FieldSurroundStrength, strength);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setFieldSurroundMidImageStrength(JNIEnv *env, jobject thiz, jint strength) {
    viperEngine.parameters.setInt(viper::Param::FieldSurroundMidImageStrength, strength);
}

// Differential Surround
extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setDifferentialSurroundEnabled(JNIEnv *env, jobject thiz, jboolean enabled) {
    viperEngine.parameters.setBool(viper::Param::DifferentialSurroundEnabled, enabled);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setDifferentialSurroundDelay(JNIEnv *env, jobject thiz, jint delay) {
    viperEngine.parameters.setInt(viper::Param::DifferentialSurroundDelay, delay);
}

// Dynamic System
extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setDynamicSystemEnabled(JNIEnv *env, jobject thiz, jboolean enabled) {
    viperEngine.parameters.setBool(viper::Param::DynamicSystemEnabled, enabled);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setDynamicSystemDeviceType(JNIEnv *env, jobject thiz, jint deviceTypeOrdinal) {
    // Reject (and report) a bad ordinal here rather than on the audio thread, which has no way to
    // log and nothing useful to do with it. The preset table itself lives next to the code that
    // reads it, in ViPER::applyParameter.
    if (deviceTypeOrdinal < 0 || deviceTypeOrdinal >= viper::kDynamicSystemDeviceTypeCount) {
        __android_log_print(ANDROID_LOG_ERROR, "ViPERPlayer", "Invalid dynamic system device type: %d", deviceTypeOrdinal);
        return;
    }
    viperEngine.parameters.setInt(viper::Param::DynamicSystemDeviceType, deviceTypeOrdinal);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setDynamicSystemBassStrength(JNIEnv *env, jobject thiz, jint strength) {
    viperEngine.parameters.setInt(viper::Param::DynamicSystemBassStrength, strength);
}

// Tube Simulator
extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setTubeSimulatorEnabled(JNIEnv *env, jobject thiz, jboolean enabled) {
    viperEngine.parameters.setBool(viper::Param::TubeSimulatorEnabled, enabled);
}

// ViPER Bass
extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setViperBassEnabled(JNIEnv *env, jobject thiz, jboolean enabled) {
    viperEngine.parameters.setBool(viper::Param::ViperBassEnabled, enabled);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setViperBassMode(JNIEnv *env, jobject thiz, jint mode) {
    viperEngine.parameters.setInt(viper::Param::ViperBassMode, mode);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setViperBassFrequency(JNIEnv *env, jobject thiz, jint frequency) {
    viperEngine.parameters.setInt(viper::Param::ViperBassFrequency, frequency);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setViperBassGain(JNIEnv *env, jobject thiz, jint gain) {
    viperEngine.parameters.setInt(viper::Param::ViperBassGain, gain);
}

// ViPER Clarity
extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setViperClarityEnabled(JNIEnv *env, jobject thiz, jboolean enabled) {
    viperEngine.parameters.setBool(viper::Param::ViperClarityEnabled, enabled);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setViperClarityMode(JNIEnv *env, jobject thiz, jint mode) {
    viperEngine.parameters.setInt(viper::Param::ViperClarityMode, mode);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setViperClarityGain(JNIEnv *env, jobject thiz, jint gain) {
    viperEngine.parameters.setInt(viper::Param::ViperClarityGain, gain);
}

// Auditory System Protection (Cure)
extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setAuditorySystemProtectionEnabled(JNIEnv *env, jobject thiz, jboolean enabled) {
    viperEngine.parameters.setBool(viper::Param::AuditorySystemProtectionEnabled, enabled);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setAuditorySystemProtectionLevel(JNIEnv *env, jobject thiz, jint level) {
    // As with the device type: reject a bad level here, where it can be logged. The crossfeed
    // presets live next to the code that applies them, in ViPER::applyParameter.
    if (level < 1 || level > 3) {
        __android_log_print(ANDROID_LOG_ERROR, "ViPERPlayer", "Invalid cure level: %d", level);
        return;
    }
    viperEngine.parameters.setInt(viper::Param::AuditorySystemProtectionLevel, level);
}

// Analog X
extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setAnalogXEnabled(JNIEnv *env, jobject thiz, jboolean enabled) {
    viperEngine.parameters.setBool(viper::Param::AnalogXEnabled, enabled);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setAnalogXLevel(JNIEnv *env, jobject thiz, jint level) {
    viperEngine.parameters.setInt(viper::Param::AnalogXLevel, level);
}

// Speaker Optimization
extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setSpeakerOptimizationEnabled(JNIEnv *env, jobject thiz, jboolean enabled) {
    viperEngine.parameters.setBool(viper::Param::SpeakerOptimizationEnabled, enabled);
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
    viperEngine.parameters.setBool(viper::Param::PlaybackGainEnabled, enabled);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setPlaybackGainStrength(JNIEnv *env, jobject thiz,
                                                                          jint strength) {
    viperEngine.parameters.setInt(viper::Param::PlaybackGainStrength, strength);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setPlaybackGainMaxGain(JNIEnv *env, jobject thiz,
                                                                         jint max_gain) {
    viperEngine.parameters.setInt(viper::Param::PlaybackGainMaxGain, max_gain);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setPlaybackGainOutputThreshold(JNIEnv *env,
                                                                                 jobject thiz,
                                                                                 jfloat threshold) {
    viperEngine.parameters.setFloat(viper::Param::PlaybackGainOutputThreshold, threshold);
}

// FET Compressor
extern "C" JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setFetCompressorEnabled(JNIEnv *env, jobject thiz, jboolean enabled) {
    viperEngine.parameters.setBool(viper::Param::FetCompressorEnabled, enabled);
}

extern "C" JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setFetCompressorThreshold(JNIEnv *env, jobject thiz, jint value) {
    viperEngine.parameters.setInt(viper::Param::FetCompressorThreshold, value);
}

extern "C" JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setFetCompressorRatio(JNIEnv *env, jobject thiz, jint value) {
    viperEngine.parameters.setInt(viper::Param::FetCompressorRatio, value);
}

extern "C" JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setFetCompressorKnee(JNIEnv *env, jobject thiz, jint value) {
    viperEngine.parameters.setInt(viper::Param::FetCompressorKnee, value);
}

extern "C" JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setFetCompressorAutoKnee(JNIEnv *env, jobject thiz, jboolean enabled) {
    viperEngine.parameters.setBool(viper::Param::FetCompressorAutoKnee, enabled);
}

extern "C" JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setFetCompressorGain(JNIEnv *env, jobject thiz, jint value) {
    viperEngine.parameters.setInt(viper::Param::FetCompressorGain, value);
}

extern "C" JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setFetCompressorAutoGain(JNIEnv *env, jobject thiz, jboolean enabled) {
    viperEngine.parameters.setBool(viper::Param::FetCompressorAutoGain, enabled);
}

extern "C" JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setFetCompressorAttack(JNIEnv *env, jobject thiz, jint value) {
    viperEngine.parameters.setInt(viper::Param::FetCompressorAttack, value);
}

extern "C" JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setFetCompressorAutoAttack(JNIEnv *env, jobject thiz, jboolean enabled) {
    viperEngine.parameters.setBool(viper::Param::FetCompressorAutoAttack, enabled);
}

extern "C" JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setFetCompressorRelease(JNIEnv *env, jobject thiz, jint value) {
    viperEngine.parameters.setInt(viper::Param::FetCompressorRelease, value);
}

extern "C" JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setFetCompressorAutoRelease(JNIEnv *env, jobject thiz, jboolean enabled) {
    viperEngine.parameters.setBool(viper::Param::FetCompressorAutoRelease, enabled);
}

extern "C" JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setFetCompressorKneeMulti(JNIEnv *env, jobject thiz, jint value) {
    viperEngine.parameters.setInt(viper::Param::FetCompressorKneeMulti, value);
}

extern "C" JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setFetCompressorMaxAttack(JNIEnv *env, jobject thiz, jint value) {
    viperEngine.parameters.setInt(viper::Param::FetCompressorMaxAttack, value);
}

extern "C" JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setFetCompressorMaxRelease(JNIEnv *env, jobject thiz, jint value) {
    viperEngine.parameters.setInt(viper::Param::FetCompressorMaxRelease, value);
}

extern "C" JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setFetCompressorCrest(JNIEnv *env, jobject thiz, jint value) {
    viperEngine.parameters.setInt(viper::Param::FetCompressorCrest, value);
}

extern "C" JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setFetCompressorAdapt(JNIEnv *env, jobject thiz, jint value) {
    viperEngine.parameters.setInt(viper::Param::FetCompressorAdapt, value);
}

extern "C" JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setFetCompressorNoClip(JNIEnv *env, jobject thiz, jboolean enabled) {
    viperEngine.parameters.setBool(viper::Param::FetCompressorNoClip, enabled);
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
    viperEngine.parameters.setBool(viper::Param::ReverberationEnabled, enabled);
}

extern "C" JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setReverberationRoomSize(JNIEnv *env, jobject thiz, jint value) {
    viperEngine.parameters.setInt(viper::Param::ReverberationRoomSize, value);
}

extern "C" JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setReverberationWidth(JNIEnv *env, jobject thiz, jint value) {
    viperEngine.parameters.setInt(viper::Param::ReverberationWidth, value);
}

extern "C" JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setReverberationDamp(JNIEnv *env, jobject thiz, jint value) {
    viperEngine.parameters.setInt(viper::Param::ReverberationDamp, value);
}

extern "C" JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setReverberationWet(JNIEnv *env, jobject thiz, jint value) {
    viperEngine.parameters.setInt(viper::Param::ReverberationWet, value);
}

extern "C" JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setReverberationDry(JNIEnv *env, jobject thiz, jint value) {
    viperEngine.parameters.setInt(viper::Param::ReverberationDry, value);
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
