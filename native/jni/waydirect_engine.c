#include <jni.h>

#include <stddef.h>
#include <stdint.h>

//C ABI -> odin core
extern const char* route_action(int action_id, const char* payload);
extern void init_engine(void);

JNIEXPORT void JNICALL
Java_com_waydirect_bridge_NativeEngine_initEngine(JNIEnv* /* env */, jclass /* clazz */) {
    init_engine();
}

JNIEXPORT jstring JNICALL
Java_com_waydirect_bridge_NativeEngine_processAction(
    JNIEnv* env, jclass /* clazz */, jint action_id, jstring payload) {
    const char* native_str = (*env)->GetStringUTFChars(env, payload, NULL);
    if (!native_str) return (*env)->NewStringUTF(env, "");

    const char* response = route_action((int)action_id, native_str);

    (*env)->ReleaseStringUTFChars(env, payload, native_str);
    return (*env)->NewStringUTF(env, response ? response : "");
}