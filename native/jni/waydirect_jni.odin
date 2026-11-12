// =============================================================================
// native/jni/waydirect_jni.odin — Odin JNI layer (replaces waydirect_engine.c)
//
// Uses the official Odin Android bindings instead of hand-written C + <jni.h>:
//
//   * core:sys/android/jni.odin   — JNI types (JNIEnv, jstring, jint, ...)
//   * core:sys/android/jni_env.odin — jni_env(), jni_set_vm(), jni_take_exception()
//   * vendor:android/java/java.odin — string_from(), string_to(), delete_local(), ...
//
// Exported symbols match NativeEngine.java's native method names:
//   Java_com_waydirect_bridge_NativeEngine_initEngine
//   Java_com_waydirect_bridge_NativeEngine_processAction
//
// These are called directly by the JVM via JNI, exactly like the old C trampoline.
// =============================================================================

package waydirect_jni

import "base:runtime"
import "core:c"
import "core:strings"

import android "core:sys/android"
import java "vendor:android/java"

import waydirect_core "../odin"

// ---------------------------------------------------------------------------
// JNI_OnLoad — called by the VM when the library loads.
// Sets the process JavaVM so android.jni_env() works on any thread.
// ---------------------------------------------------------------------------
@(export)
JNI_OnLoad :: proc "c" (vm: ^android.JavaVM, reserved: rawptr) -> android.jint {
    context = runtime.default_context()
    android.jni_set_vm(vm)
    return android.JNI_VERSION_1_6
}

// ---------------------------------------------------------------------------
// Java_com_waydirect_bridge_NativeEngine_initEngine
//   native void initEngine()
// ---------------------------------------------------------------------------
@(export)
Java_com_waydirect_bridge_NativeEngine_initEngine :: proc "c" (env: ^android.JNIEnv, clazz: android.jobject) {
    context = runtime.default_context()
    defer free_all(context.temp_allocator)

    waydirect_core.init_engine()
}

// ---------------------------------------------------------------------------
// Java_com_waydirect_bridge_NativeEngine_processAction
//   native String processAction(int actionId, String payload)
// ---------------------------------------------------------------------------
@(export)
Java_com_waydirect_bridge_NativeEngine_processAction :: proc "c" (env: ^android.JNIEnv, clazz: android.jobject, action_id: android.jint, payload: android.jstring) -> android.jstring {
    context = runtime.default_context()
    defer free_all(context.temp_allocator)

    // --- Java String -> Odin string ---------------------------------------
    // jstring is jobject (rawptr); java.string_to expects ^String (Odin's String ref).
    native_str, conv_exc := java.string_to((^java.String)(payload))
    if conv_exc != nil {
        java.delete_local(conv_exc)
        empty, empty_exc := java.string_from("")
        if empty_exc != nil {
            java.delete_local(empty_exc)
        }
        return empty
    }

    // --- Call Odin router -------------------------------------------------
    // route_action :: proc "c" (action_id: c.int, payload: cstring) -> cstring
    c_path := strings.clone_to_cstring(native_str, context.temp_allocator)
    response := waydirect_core.route_action(c.int(action_id), c_path)

    // --- Odin string -> Java String ---------------------------------------
    // string_from expects string, response is cstring -> convert.
    result, result_exc := java.string_from(string(response))
    if result_exc != nil {
        java.delete_local(result_exc)
        empty, empty_exc := java.string_from("")
        if empty_exc != nil {
            java.delete_local(empty_exc)
        }
        return empty
    }
    return result
}