#include <jni.h>
#include "viper/ViPER.h"
#include "viper/effects/ViPERBass.h"
#include "viper/effects/ViPERClarity.h"

#define DEFAULT_SAMPLING_RATE 44100
static ViPER viper = ViPER(DEFAULT_SAMPLING_RATE);

// Master Limiter
extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setMasterLimiterOutputGain(JNIEnv *env, jobject thiz, jfloat gainL, jfloat gainR) {
    viper.setGain(gainL, gainR);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setMasterLimiterThresholdLimit(JNIEnv *env, jobject thiz, jfloat threshold) {
    viper.setThresholdLimit(threshold);
}

// Spectrum Extension
extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setSpectrumExtensionEnabled(JNIEnv *env, jobject thiz, jboolean enabled) {
    viper.spectrumExtend.SetEnable(enabled);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setSpectrumExtensionStrength(JNIEnv *env, jobject thiz, jint strength) {
    // Convert 0-100 to actual exciter value
    float exciter = strength / 100.0f;
    viper.spectrumExtend.SetExciter(exciter);
}

// Field Surround (Reverberation)
extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setFieldSurroundEnabled(JNIEnv *env, jobject thiz, jboolean enabled) {
    viper.reverberation.SetEnable(enabled);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setFieldSurroundStrength(JNIEnv *env, jobject thiz, jint strength) {
    // Map surround strength 0-100 to wet/dry mix
    float wet = strength / 100.0f;
    float dry = 1.0f - (strength / 200.0f); // Keep some dry signal
    viper.reverberation.SetWet(wet);
    viper.reverberation.SetDry(dry);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setFieldSurroundMidImageStrength(JNIEnv *env, jobject thiz, jint strength) {
    // Map mid image strength 0-100 to room size/width
    float roomSize = strength / 100.0f;
    float width = strength / 100.0f;
    viper.reverberation.SetRoomSize(roomSize);
    viper.reverberation.SetWidth(width);
}

// Differential Surround
extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setDifferentialSurroundEnabled(JNIEnv *env, jobject thiz, jboolean enabled) {
    viper.diffSurround.SetEnable(enabled);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setDifferentialSurroundDelay(JNIEnv *env, jobject thiz, jint delay) {
    // Convert delay in ms to float
    float delayTime = delay / 1000.0f;
    viper.diffSurround.SetDelayTime(delayTime);
}

// Dynamic System
extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setDynamicSystemEnabled(JNIEnv *env, jobject thiz, jboolean enabled) {
    viper.dynamicSystem.SetEnable(enabled);
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
    float bassGain = strength / 100.0f;
    viper.dynamicSystem.SetBassGain(bassGain);
}

// Tube Simulator
extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setTubeSimulatorEnabled(JNIEnv *env, jobject thiz, jboolean enabled) {
    viper.tubeSimulator.SetEnable(enabled);
}

// ViPER Bass
extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setViperBassEnabled(JNIEnv *env, jobject thiz, jboolean enabled) {
    viper.viperBass.SetEnable(enabled);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setViperBassMode(JNIEnv *env, jobject thiz, jint mode) {
    ViPERBass::ProcessMode processMode = static_cast<ViPERBass::ProcessMode>(mode);
    viper.viperBass.SetProcessMode(processMode);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setViperBassFrequency(JNIEnv *env, jobject thiz, jint frequency) {
    viper.viperBass.SetSpeaker(frequency);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setViperBassGain(JNIEnv *env, jobject thiz, jint gain) {
    // Convert gain index to bass factor (1-12 -> actual bass factor)
    float bassFactor = gain / 12.0f;
    viper.viperBass.SetBassFactor(bassFactor);
}

// ViPER Clarity
extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setViperClarityEnabled(JNIEnv *env, jobject thiz, jboolean enabled) {
    viper.viperClarity.SetEnable(enabled);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setViperClarityMode(JNIEnv *env, jobject thiz, jint mode) {
    ViPERClarity::ClarityMode clarityMode = static_cast<ViPERClarity::ClarityMode>(mode);
    viper.viperClarity.SetProcessMode(clarityMode);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setViperClarityGain(JNIEnv *env, jobject thiz, jint gain) {
    // Convert 0-100 to clarity gain percent
    float clarityGain = gain / 100.0f;
    viper.viperClarity.SetClarity(clarityGain);
}

// Auditory System Protection (Cure)
extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setAuditorySystemProtectionEnabled(JNIEnv *env, jobject thiz, jboolean enabled) {
    viper.cure.SetEnable(enabled);
}

// Analog X
extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setAnalogXEnabled(JNIEnv *env, jobject thiz, jboolean enabled) {
    viper.analogX.SetEnable(enabled);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setAnalogXLevel(JNIEnv *env, jobject thiz, jint level) {
    // Convert 0-100 to processing model
    int processingModel = level / 10; // Map to 0-10 range
    viper.analogX.SetProcessingModel(processingModel);
}

// Speaker Optimization
extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_setSpeakerOptimizationEnabled(JNIEnv *env, jobject thiz, jboolean enabled) {
    viper.speakerCorrection.SetEnable(enabled);
}

// Common
extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_process(JNIEnv *env, jobject thiz,
                                                           jobject buffer, jint offset, jint size) {
    uint8_t *data = static_cast<uint8_t *>(env->GetDirectBufferAddress(buffer));

    viper.process((float *) (data + offset), size / 4 / 2);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperNativeDriver_reset(JNIEnv *env, jobject thiz) {
    viper.reset();
}