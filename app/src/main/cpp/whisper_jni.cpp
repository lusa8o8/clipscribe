#include <jni.h>
#include <string>
#include <android/log.h>

#define LOG_TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_transcription_WhisperNativeBridge_initContext(
        JNIEnv* env,
        jobject thiz,
        jstring model_path) {
    const char* path = env->GetStringUTFChars(model_path, nullptr);
    LOGI("initContext called with path: %s", path);
    env->ReleaseStringUTFChars(model_path, path);
    return 123456789L;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_transcription_WhisperNativeBridge_transcribe(
        JNIEnv* env,
        jobject thiz,
        jlong context_ptr,
        jfloatArray samples,
        jint sample_rate) {
    LOGI("transcribe called with context_ptr: %lld", (long long)context_ptr);
    return env->NewStringUTF("This is native transcription text from whisper_jni C++ back-end.");
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_transcription_WhisperNativeBridge_releaseContext(
        JNIEnv* env,
        jobject thiz,
        jlong context_ptr) {
    LOGI("releaseContext called with context_ptr: %lld", (long long)context_ptr);
}
