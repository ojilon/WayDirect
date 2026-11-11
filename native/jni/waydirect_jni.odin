// =============================================================================
// native/jni/waydirect_jni.odin — Odin JNI layer (replaces waydirect_engine.c)
//
// Uses the official Odin Android bindings instead of hand-written C + <jni.h>:
//
//   * core:sys/android/jni.odin   — JNI types (JNIEnv, jstring, jint, ...)
//   * vendor:android/java/java.odin — string_from(), string_to(), ...
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
    native_str, exc := java.string_to(payload)
    if exc != nil {
        java.delete_local(exc)
        empty, e2 := java.string_from("")
        if e2 != nil { java.delete_local(e2) }
        return empty
    }

    // --- Call Odin router -------------------------------------------------
    response := waydirect_core.route_action(int(action_id), native_str)

    // --- Odin string -> Java String ---------------------------------------
    result, exc := java.string_from(response)
    if exc != nil {
        java.delete_local(exc)
        empty, e2 := java.string_from("")
        if e2 != nil { java.delete_local(e2) }
        return empty
    }
    return result
}