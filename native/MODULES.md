# Native modular layout (agent guide) — Odin bindings purge, Android 10 target

Branch: `experiment/use_official_odin_android_bindings` (off `main`) · Odin `dev-2026-11` · target Android 10 (API 29).

This tree has **two languages**: C/C++ (C++23 + C11) and **Odin** (`*.odin`). Odin is the primary language; C/C++ remains only as FFI implementation behind Odin's `foreign import "system:c"` declarations.

This tree keeps everything under **`native/`**.

## Target layout

```text
native/
├── build-android.ps1      # Odin-based build script (replaces CMake)
├── MODULES.md             # this file
├── odin/
│   └── WayDirect_core.odin   # action router + C ABI exports (only Odin package built directly)
├── core/                  # WayDirect_core — foundation only (Odin + C FFI)
│   ├── include/WayDirect/core/
│   │   └── android_fs.h   # bionic allowlist header
│   ├── src/
│   │   ├── *.odin          # core_heart package (json_util, paths, logging, text)
│   │   └── *.cpp/.c        # C++ FFI implementations
│   └── (CMakeLists.txt deleted — see build-android.ps1)
├── storage/               # WayDirect_storage — largest (Odin + C FFI)
│   ├── include/WayDirect/storage/
│   ├── src/
│   │   ├── *.odin          # storage package
│   │   └── *.c/.cpp        # C FFI implementations (index_c.c, dir_walk_android.c, ...)
│   └── modules/search|cleaner|organizer
├── documents/             # WayDirect_documents (Odin + C helpers)
├── transfer/              # WayDirect_transfer (Odin + transfer_shim.c)
├── media/                 # WayDirect_media (Odin stub)
├── preview/               # WayDirect_preview (Odin capped text preview)
├── jni/                   # JNI boundary — Odin only (waydirect_jni.odin, replaces waydirect_engine.c)
│   └── waydirect_jni.odin  # JNI_OnLoad + Java_com_waydirect_bridge_NativeEngine_* exports
├── third_party/           # nlohmann/json + SQLite (gitignored, local only)
```

## Dependency graph (must stay acyclic)

```text
core  (core_heart in Odin)
  ↑
storage (storage in Odin) ──→ documents (documents)
  ↑                            ──→ transfer  (transfer)
  ↑                            ──→ media     (media, leaf)
  ↑
preview (preview — core + storage read-only, not storage write)
jni: waydirect_jni imports waydirect_core; exports JNI_OnLoad + Java_com_waydirect_bridge_NativeEngine_*
```

Odin packages are siblings of their C++ FFI twins (`native/<module>/src/*.odin` imports `../../core/src` relatively). The shared library links the Odin object files + the C/C++ FFI object files (see `native/build-android.ps1`).

## Build system — replaced CMake with Odin + NDK clang

**No CMake. No `externalNativeBuild` in Gradle.**

Build the native shared library:

```pwsh
pwsh native/build-android.ps1
```

This:
1. Runs `odin build native/jni -build-mode:obj -target:linux_arm64 -subtarget:android -reloc-mode:pic -no-entry-point`
   - Builds `waydirect_jni` package which transitively imports `waydirect_core` + all modules
   - Produces object files with JNI exports + core exports
2. Compiles remaining C/C++ FFI sources with NDK `clang++`
3. Links everything into `native/build/arm64-v8a/libWayDirect_engine.so`

Then Gradle packages it:

```bash
./gradlew assembleDebug
```

Gradle's `build.gradle` has been stripped of `externalNativeBuild`/`CMake`. It only compiles Java + resources and copies the pre-built `.so` into `jniLibs`.

## Rules

1. No `JNIEnv` outside `jni/` — JNI is handled entirely by `waydirect_jni.odin` using official bindings (`core:sys/android/jni.odin`, `vendor:android/java/java.odin`).
2. Domain modules depend only downward (core → storage → documents/transfer/media/preview).
3. Large results → files under app cache; JNI returns `{status, path}`.
4. All Odin file I/O goes through C wrappers (`android_fs.c`, `dir_walk_android.c`) — never `core:os` on Android (seccomp blocks `statx`/`openat2` on API 29).

## Style for this tree — C++ + Odin

### C++ (FFI only)
- Plain structs + free functions; no `class`, no inheritance, no virtuals.
- Headers declare, `.cpp` files define.
- `std::string_view` at API edges, `std::filesystem`, `std::error_code` (never exceptions).

### Odin
- `package` per module, one `.odin` per concern.
- No `core:os` for filesystem — use C wrappers (`stat_c`, `walk_files_c`, etc.).
- `foreign import "system:c"` + `@(default_calling_convention="c")` for libc calls.
- `context.temp_allocator` + `defer free_all` per exported proc.
- File-backed replies: JSON spilled to `cache/` file, JNI returns `{status, path}`.

## What was removed (purge)

| Removed | Replaced by |
|---------|-------------|
| `native/CMakeLists.txt` | `native/build-android.ps1` + `odin build` |
| `native/*/CMakeLists.txt` | `native/build-android.ps1` |
| `native/cmake/CompilerFlags.cmake` | clang flags in `build-android.ps1` |
| `native/odin/build_odin.cmake` | `odin build -build-mode:obj` directly |
| `native/jni/waydirect_engine.c` | `native/jni/waydirect_jni.odin` (Odin JNI using official bindings) |
| `native/shim/` (WayDirect_shim.cpp, odin_bionic_shim.h) | Odin `@(export) proc "c"` in `waydirect_core.odin` / `waydirect_jni.odin` |
| `native/build-test/` | deleted (stale prebuilt artifacts) |
| `native/ERROR_ANALYSIS.md` | deleted (superseded) |
| `native/FUTURE_JNI_AND_CPP23.md` | deleted (superseded) |
| Gradle `externalNativeBuild` / `ndkVersion` / `copyCompileCommands` | stripped from `app/build.gradle` |

## Gradle

`app/build.gradle` compiles Java + resources only. No `externalNativeBuild`. The `.so` is built separately by `build-android.ps1` and copied into `app/src/main/jniLibs/arm64-v8a/` before `assembleDebug`.

## Ordered migration

1. Read this file + `docs/NATIVE_OVERVIEW.md` + `docs/BRIDGE_PLAN.md`.
2. All `*.odin` files use `dev-2026-11` syntax (verified with `odin check`).
3. JNI layer is pure Odin (`waydirect_jni.odin`).
4. C++ FFI files remain until ported to pure Odin; they are compiled by `build-android.ps1` with NDK clang.

Remaining open items: port remaining C++ FFI to pure Odin, media metadata extraction, nlohmann/json for deeply nested payloads.