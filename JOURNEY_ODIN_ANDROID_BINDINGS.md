# Odin Android Bindings — Journey, Findings & Recommendations

**Repo:** `D:\projects\WayDirect` (branch `arena/e58556e6-WayDirect` / `refactor/native-modules`)
**Reference (read-only):** `D:\Dev\Odin` — official Odin compiler + Android bindings
**Odin compiler version installed:** `dev-2026-11:9a6991685`
**Project targets:** Odin `dev-2025-10`, Android 10 (API 29), ABI `arm64-v8a` only

---

## 1. Where the official bindings actually live

The path `D:\Dev\Odin\code\sys\android` **does not exist**. The official Odin Android bindings are at:

| Purpose | Path in `D:\Dev\Odin` | What it is |
|---------|----------------------|------------|
| NDK C API bindings | `core/sys/android/` | `jni.h`, `liblog`, `libandroid`, `libnativewindow`, `android/log.h`, `native_activity.h`, etc. — all generated from NDK r30 headers by `core/sys/android/_gen/generate.py` |
| NativeActivity glue | `core/sys/android/native_app_glue/native_app_glue.odin` | Exports `ANativeActivity_onCreate`, runs Odin `main()` on a thread, sets up `ALooper`, redirects stdio to logcat |
| Asset access | `core/sys/android/assets.odin` | `AAssetManager` from Java via JNI, `asset_open`, `asset_read_entire_file`, `asset_list` |
| JNI types | `core/sys/android/jni.odin` | Full `jni.h` type mirror: `JNIEnv`, `jobject`, `jmethodID`, `JNINativeMethod`, `RegisterNatives`, etc. |
| JNI helpers | `core/sys/android/jni_env.odin` | `jni_env()`, `jni_set_vm()`, `jni_take_exception()` — thread attach/detach, exception capture |
| Java API wrappers | `vendor/android/java/` | Generated from `android.jar`: `java.odin`, `android_app.odin`, `android_content.odin`, `android_os.odin`, `android_view.odin`, `proxy.odin`, etc. |
| Java proxy / OdinActivity | `vendor/android/java/_dex/NativeProxy.java`, `OdinActivity.java` | Embedded in `classes.dex`; `OdinActivity` is a `NativeActivity` subclass that forwards activity results/permissions/intents to listeners |
| APK bundler | `odin bundle android <dir>` | Takes `<dir>/AndroidManifest.xml`, `res/`, `assets/`, `lib/<abi>/*.so`; adds `classes.dex` with `OdinActivity`; signs the APK/AAB |

**Key CLI flow** (from `examples/android/README.md`):

```sh
# 1. Build the Odin shared library per ABI
odin build <pkg> -target:linux_arm64 -subtarget:android \
    -minimum-os-version:29 -build-mode:shared \
    -out:app/lib/arm64-v8a/lib<name>.so

# 2. Bundle into an APK (adds OdinActivity + classes.dex, signs)
odin bundle android app -minimum-os-version:29 -out:example.apk
```

> Odin requires API level 29 or higher (`-minimum-os-version:29`). The example's `AndroidManifest.xml` uses `org.odin_lang.OdinActivity` (the bundler-provided NativeActivity) and `android.app.lib_name` meta-data pointing at the `.so` basename.

---

## 2. Version matrix

| Component | Version | Source |
|-----------|---------|--------|
| **Odin compiler (installed)** | `dev-2026-11:9a6991685` | `odin.exe version` |
| Odin target (project docs) | `dev-2025-10` | `README.md`, `native/MODULES.md` |
| Android NDK | `29.0.14206865` (r29) | `app/build.gradle:17`, `gradle.properties:17` |
| Android SDK compileSdk | `36` | `app/build.gradle:15` |
| targetSdk | `34` | `app/build.gradle:26` |
| minSdk | `26` | `app/build.gradle:25` |
| CMake | `3.22.1` | `app/build.gradle:91`, `native/CMakeLists.txt:16` |
| C++ standard | `C++23` | `app/build.gradle:45`, `native/CMakeLists.txt:24` |
| C standard | `C11` | `native/CMakeLists.txt:20` |
| Gradle plugin | `com.android.application` | `app/build.gradle:2` |
| Java | `17` | `app/build.gradle:84-85` |
| AGP | not explicitly pinned | `app/build.gradle` |
| ABI filters | `arm64-v8a` (duplicated on lines 32-33) | `gradle.properties:32-33` |

> **Note:** The installed Odin (`dev-2026-11`) is newer than the project's documented target (`dev-2025-10`). The Oct 2025 breaking changes (new `core:os`, `ODIN_OS` enum, `foreign import "system:c"`, dynamic literals, `using` opt-in) are already reflected in the repo's code, but drift with the newer compiler should be re-verified with `odin check`.

---

## 3. How the current project uses JNI today (what to keep / what to replace)

### Current boundary
```
Java (NativeEngine.java)
  └─ processAction(actionId, payload: String) → JNI
       └─ waydirect_engine.c (C trampoline, GetStringUTFChars / NewStringUTF)
            └─ route_action(action_id, payload) → exported from Odin archive
                 └─ dispatch_action() → storage/documents/transfer/preview modules
                      └─ writes result file under files/WayDirect/cache/
                           └─ returns {status, path} cstring to JNI → Java
```

### Files in the boundary today
- `app/src/main/java/com/waydirect/bridge/NativeEngine.java` — action IDs (must match Odin), `initEngine()`, `processAction()` native declarations, `System.loadLibrary("waydirect_engine")`
- `app/src/main/java/com/waydirect/bridge/Bridge.java` — single-thread executor, UI-thread callback dispatch, optional file leases
- `app/src/main/java/com/waydirect/bridge/PathRegistry.java` — reads `paths.json` manifest, persisted in SharedPreferences
- `native/jni/waydirect_engine.c` — sole JNI boundary; only file allowed to include `<jni.h>`
- `native/jni/CMakeLists.txt` — links `waydirect_engine` SHARED against Odin archive + C++ modules
- `native/odin/waydirect_core.odin` — `route_action` switch (19 actions), `@(export) proc "c"` C ABI exports, `reply_buffer`, payload parsing
- `native/odin/build_odin.cmake` — runs `odin build -build-mode:obj -reloc-mode:pic -no-entry-point`, packs `.o` into `libwaydirect_core_odin.a` via `llvm-ar`

### What works
- Odin owns the router and all action logic; Java is a thin UI + socket layer.
- File-backed protocol keeps JNI strings < 16 KiB (large payloads spill to cache files).
- C wrappers (`android_fs.c`, `dir_walk_android.c`, `index_c.c`) avoid `statx`/`openat2` on Android 10 seccomp.

---

## 4. How the official bindings let you "abstract away the Java"

The official bindings give you three progressively deeper options for reducing hand-written Java/JNI glue:

### Option A — Keep Java UI + XML, modernize the JNI boundary (recommended for WayDirect)

WayDirect's UI is Material 3 (Fragments, NavigationBarView, ViewBinding) and must stay Java/XML. The improvement here is to **replace the hand-written C JNI trampoline** (`waydirect_engine.c`) with Odin code that uses the official bindings:

| Today (hand-written C) | With official bindings |
|------------------------|----------------------|
| `waydirect_engine.c` includes `<jni.h>`, manually calls `GetStringUTFChars`/`NewStringUTF` | Odin code uses `core:sys/android/jni.odin` types (`JNIEnv`, `jstring`, `jmethodID`) + `vendor:android/java` wrappers (`string_from`, `string_to`, `CallObjectMethodA`, etc.) |
| `JNIEnv` passed from Java to C to Odin | `android.jni_env()` (from `jni_env.odin`) obtains the thread's `JNIEnv`, attaching/detaching automatically |
| Java implements `NativeEngine` with `static { System.loadLibrary(...) }` | Same, but the native side is pure Odin instead of C |
| Java callbacks via anonymous inner classes | `vendor:android/java/proxy.odin` `proxy_new()` creates Java interface implementations from Odin procs |

This keeps `NativeEngine.java` (action IDs + `processAction` native decl) but removes `waydirect_engine.c` entirely — JNI registration and calls happen from Odin.

### Option B — Pure Odin NativeActivity (like `examples/android/main.odin`)

Use `core:sys/android/native_app_glue` as the entry point. Odin `main()` runs directly under `ANativeActivity_onCreate`. The app window is drawn via `ANativeWindow` (as in the example's `draw()` proc). **Not suitable for WayDirect** because the UI is Material 3 Java/XML, but useful for future fullscreen native tools or a native-only viewer.

### Option C — `odin bundle android` packaging

Instead of (or alongside) Gradle assembling the APK, use:

```sh
odin build native/odin -target:linux_arm64 -subtarget:android \
    -minimum-os-version:29 -build-mode:shared \
    -out:app/lib/arm64-v8a/libwaydirect_engine.so
odin bundle android app -minimum-os-version:29 -out:WayDirect.apk
```

The bundler adds `classes.dex` (with `org.odin_lang.OdinActivity`) and signs. The manifest names `OdinActivity` and sets `android.app.lib_name`. This is what `examples/android` does.

---

## 5. Concrete improvement plan for WayDirect

### Phase 1 — Align toolchain versions (no code change yet)

1. **Odin drift**: Project targets `dev-2025-10`; installed compiler is `dev-2026-11`. Run `odin check native/odin` and `odin build native/odin -target:linux_arm64 -subtarget:android -vet -strict-style` to catch syntax drift (the Oct 2025 fixes in `ERROR_ANALYSIS.md` may no longer apply or may differ).
2. **Duplicate ABI filter** in `gradle.properties` lines 32-33 (`aurora.abiFilters=arm64-v8a` twice) — remove the duplicate.
3. Keep NDK r29 (`29.0.14206865`), compileSdk 36, targetSdk 34, minSdk 26, CMake 3.22.1, C++23 — these are coherent and current.

### Phase 2 — Replace `waydirect_engine.c` with Odin JNI using official bindings

1. Add a new Odin package (e.g. `native/jni/waydirect_jni.odin`, package `waydirect_jni`) that:
   - Imports `android "core:sys/android"` and `java "vendor:android/java"`
   - Calls `android.jni_set_vm(vm)` once (from `JNI_OnLoad` equivalent, or from the existing `initEngine` path)
   - Uses `java.string_from()` / `java.string_to()` to convert between Odin strings and Java `String`
   - Uses `java.CallObjectMethodA()` / `java.FindClass()` / `java.GetMethodID()` to invoke Java callbacks
   - Exports `route_action` and `init_engine` via `@(export) proc "c"` (same symbols `waydirect_engine.c` currently calls)
2. Delete `native/jni/waydirect_engine.c`; update `native/jni/CMakeLists.txt` to link only the Odin archive + modules (no C trampoline).
3. `NativeEngine.java` stays (action IDs + `processAction` native method + `System.loadLibrary`), but the underlying native implementation is Odin.

### Phase 3 — Add `proxy_new`-based listeners (optional, later)

If Java needs to receive async events from native (e.g. transfer progress, search completion), use `vendor:android/java/proxy.odin` `proxy_new()` to implement Java listener interfaces from Odin procs, backed by `NativeProxy.java` + `OdinActivity.java` from `vendor/android/java/_dex/`. This avoids hand-writing Java `InvocationHandler` classes.

### Phase 4 — `odin bundle android` as an alternative build path (optional)

Add a Gradle task or script that builds the Odin `.so` then calls `odin bundle android app`. This is useful for CI or for a pure-Odin distribution. Gradle remains the primary build.

---

## 6. Documentation cleanup — clear out unnecessary / confusing work

The following items create confusion with the new Odin-bindings work and should be archived, consolidated, or removed:

| File | Issue | Action |
|------|-------|--------|
| `native/ERROR_ANALYSIS.md` | Documents Oct 2025 Odin syntax fixes + interop rules. Useful as history, but some fixes (e.g. `search.odin:34` `vendor:libc-shim`, `logging.odin:50` `when ODIN_OS == .Android`) may already be applied. | **Archive** → `docs/ARCHIVED/ERROR_ANALYSIS_2025.md`; keep only the interop mapping table (§4) as a reference. |
| `native/FUTURE_JNI_AND_CPP23.md` | Roadmap for action IDs + C++23. Some actions already implemented in Odin; the C++23 focus competes with the Odin-first direction. | **Archive** → `docs/ARCHIVED/FUTURE_JNI_CPP23_2025.md`; action ID table is still valid in `NativeEngine.java` / `waydirect_core.odin`. |
| `native/shim/WayDirect_shim.cpp` + `odin_bionic_shim.h` | C++ shim "being replaced by Odin exports" — now that `waydirect_core.odin` exports all `WayDirect_*` symbols, the shim is legacy. | **Mark deprecated**; plan removal once all Java call sites use `route_action`. Keep `odin_bionic_shim.h` allowlist docs (still valid). |
| `native/build-test/` | Prebuilt `.o` / `.a` artifacts (`wayer_core_odin-*.o`, `wayer_android_test-*.o`) — stale build outputs, gitignored? | **Verify gitignore**; if not ignored, add to `.gitignore`. Do not commit prebuilt objects. |
| `gradle.properties:32-33` | `aurora.abiFilters=arm64-v8a` duplicated on two lines. | **Remove duplicate line.** |
| `native/MODULES.md` | References `docs/BRIDGE_PLAN.md`, `docs/NATIVE_OVERVIEW.md`, `docs/BRIDGE_PLAN.md` Step 0. Still accurate. | Keep; add a pointer to this journey doc. |
| `docs/BRIDGE_PLAN.md` | 7-step file-backed bridge plan; Steps 0-7 coded but "needs device pass." | Keep; it's the active bridge plan. |
| `docs/NATIVE_OVERVIEW.md` | Oct 2025 Odin sweep — bionic allowlist, syntax fixes, per-module notes. | Keep; it's the active native style guide. |

### Suggested archive layout
```
docs/
  ARCHIVED/
    ERROR_ANALYSIS_2025.md      (moved from native/ERROR_ANALYSIS.md)
    FUTURE_JNI_CPP23_2025.md    (moved from native/FUTURE_JNI_AND_CPP23.md)
  JOURNEY_ODIN_ANDROID_BINDINGS.md  (this document)
  NATIVE_OVERVIEW.md            (keep — active style guide)
  BRIDGE_PLAN.md                (keep — active bridge plan)
```

---

## 7. Files to touch (implementation checklist)

### `native/jni/`
- [ ] Create `waydirect_jni.odin` — Odin JNI layer using `core:sys/android` + `vendor:android/java`
- [ ] Delete `waydirect_engine.c` (C trampoline no longer needed)
- [ ] Edit `CMakeLists.txt` — link Odin archive + modules only; remove `waydirect_engine.c` source

### `native/odin/waydirect_core.odin`
- [ ] Verify all `@(export) proc "c"` symbols match what `waydirect_jni.odin` will call (currently `route_action`, `init_engine`, plus individual `WayDirect_*` exports)

### `app/src/main/java/com/waydirect/bridge/NativeEngine.java`
- [ ] Keep action IDs (must stay in sync with Odin)
- [ ] Keep `processAction` / `initEngine` native declarations
- [ ] Update comment: native side is now Odin, not C

### `gradle.properties`
- [ ] Remove duplicate `aurora.abiFilters=arm64-v8a` line

### `docs/`
- [ ] Add `JOURNEY_ODIN_ANDROID_BINDINGS.md` (this document)
- [ ] Archive `native/ERROR_ANALYSIS.md` and `native/FUTURE_JNI_AND_CPP23.md` to `docs/ARCHIVED/`
- [ ] Add cross-reference from `README.md` to the new bindings documentation

---

## 8. Odin binding idioms to adopt (from official sources)

From `core/sys/android/jni_env.odin`:
```odin
android.jni_set_vm(activity.vm)   // set once, from NativeActivity or JNI_OnLoad
env := android.jni_env()           // obtains thread's JNIEnv, attaches if needed
android.jni_take_exception(env)    // capture + clear pending Java exception
```

From `vendor/android/java/java.odin`:
```odin
import java "vendor:android/java"
s_java := java.string_from(odin_string) or_return      // Odin string → Java String
odin_s, _ := java.string_to(java_string)                // Java String → Odin string
env->CallObjectMethodA(obj, method_id, &args)           // invoke Java method
java.new_global(obj) / java.delete_global(obj)          // global refs
java.push_local_frame() / java.pop_local_frame()        // local ref frames
```

From `vendor/android/java/proxy.odin`:
```odin
listener := java.proxy_new({listener_class}, handler_proc, data) or_return
// handler: Proxy_Handler :: #type proc(data, method, args) -> (result, exception)
```

From `odin bundle android` (CLI):
```sh
odin build <pkg> -target:linux_arm64 -subtarget:android -minimum-os-version:29 \
    -build-mode:shared -out:app/lib/arm64-v8a/lib<name>.so
odin bundle android app -minimum-os-version:29 -out:WayDirect.apk
```

---

## 9. Summary

The official Odin Android bindings at `D:\Dev\Odin\core\sys/android/` (NDK C APIs) and `D:\Dev\Odin\vendor/android/java/` (Java API wrappers) provide a complete, generated-from-NDK-r30 alternative to hand-written JNI C glue. For WayDirect, the pragmatic path is **Phase 2**: replace `native/jni/waydirect_engine.c` with an Odin JNI layer using these bindings, keeping the Java/XML Material 3 UI intact. This reduces the boundary to a single native method declaration in `NativeEngine.java` with the implementation in Odin, and opens the door to `proxy_new`-based listeners and `odin bundle android` packaging later.

The project's toolchain (NDK r29, SDK 36/34/26, CMake 3.22.1, C++23, Odin dev-2025-10 target) is coherent; the main housekeeping items are the Odin compiler version drift (dev-2026-11 installed vs dev-2025-10 targeted), the duplicate `abiFilters` line in `gradle.properties`, and archiving the Oct 2025 analysis docs whose fixes are already in code.