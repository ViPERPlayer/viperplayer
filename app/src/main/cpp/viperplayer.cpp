#include <jni.h>
#include "viper/ViPER.h"

#define DEFAULT_SAMPLING_RATE 44100
static ViPER viper = ViPER(DEFAULT_SAMPLING_RATE);

extern "C"
JNIEXPORT void JNICALL
Java_com_viperplayer_data_player_ViperAudioProcessor_process(JNIEnv *env, jobject thiz,
                                                             jbyteArray buffer, jint size) {
    jboolean isCopy;
    jbyte *data = env->GetByteArrayElements(buffer, &isCopy);

    viper.process((float *) data, size);

    env->ReleaseByteArrayElements(buffer, data, JNI_ABORT);
}