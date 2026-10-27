#include <jni.h>
#include <string>

extern "C" JNIEXPORT jstring JNICALL
Java_com_waydirect_NativeBridge_getEngineVersion(JNIEnv* env, jobject /*this*/) {
    std::string version = "WayDirect C++23 Engine v0.0.0";
    return env->NewStringUTF(version.c_str());
}