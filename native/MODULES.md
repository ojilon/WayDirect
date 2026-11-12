# Native modular layout (agent guide) — Odin bindings purge, Android 10 target

Branch: `experiment/use_official_odin_android_bindings` (off `main`) · Odin `dev-2026-11` · target Android 10 (API 29).

This tree has **two languages**: C (C11) and **Odin** (`*.odin`). Odin is the primary language; C remains only as thin, bionic-safe FFI behind Odin's `foreign` declarations. **No C++ sources are compiled any longer.**

This tree keeps everything under **`native/`**.

## Target layout

```text
native/
├── build-android.ps1      # Odin-based build script (replaces CMake)
├── MODULES.md             # this file
├── odin/
│   └── waydirect_core.odin   # action router + C ABI exports
├── core/                  # foundation (Odin)
│   ├── include/waydirect/core/
│   └── src/*.odin
├── storage/               # largest module (Odin + C FFI)
│   ├── include/waydirect/storage/
│   ├── src/*.odin + dir_walk_android.c + index_c.c
│   └── modules/search|cleaner|organizer  (Odin live; legacy .cpp dead)
├── documents/             # Odin + small C helpers
├── transfer/              # Odin + transfer_shim.c
├── media/                 # Odin stub
├── preview/               # Odin capped text preview
├── jni/                   # pure Odin JNI (waydirect_jni.odin)
│   └── waydirect_jni.odin
├── file_system_bridge/    # android_fs.c + fs_android.odin
└── json_utils/            # Odin JSON helpers
```

## Dependency graph (must stay acyclic)

```text
core  (core_heart in Odin)
  ↑
storage (storage in Odin) ──→ documents (documents)
  ↑                            ──→ transfer  (transfer)
  ↑                            ──→ media     (media, leaf)
  ↑
preview (preview — core + storage read-only)
jni: waydirect_jni imports waydirect_core; exports JNI_OnLoad + Java_com_waydirect_bridge_NativeEngine_*
```

## Build system — Odin + NDK clang (no CMake, no C++)

**No CMake. No `externalNativeBuild` in Gradle. No `.cpp` in the build.**

```pwsh
pwsh native/build-android.ps1
```

1. `odin build native/jni -build-mode:obj -target:linux_arm64 -subtarget:android ...`
2. NDK `clang` for the remaining `.c` FFI sources only
3. Link → `native/build/arm64-v8a/libwaydirect_engine.so` → copy into `app/src/main/jniLibs/arm64-v8a/`

Then:

```bash
./gradlew assembleDebug
```

## Rules

1. No `JNIEnv` outside `jni/` — handled by `waydirect_jni.odin` + official bindings.
2. Domain modules depend only downward.
3. Large results → files under app cache; JNI returns `{status, path}`.
4. All Odin file I/O goes through C wrappers — never `core:os` on Android (seccomp / API 29).

## Style

### C (FFI only)
- Plain C11, no C++.
- Headers declare, `.c` files define.
- Only bionic-safe syscalls (opendir/readdir/stat/open/read/mkdir/rename).

### Odin
- `package` per module.
- No `core:os` for filesystem.
- `foreign import "system:c"` + `@(default_calling_convention="c")`.
- `context.temp_allocator` + `defer free_all` per exported proc.
- File-backed replies.

## What was removed / completed in this pass

| Item | Status |
|------|--------|
| Placeholder `.cpp` (storage/documents/media/transfer anchors) | Removed from build + deleted |
| Live logic for search / cleaner / organizer / documents / media | Already in `*.odin` |
| Dead `storage/modules/*/src/*.cpp` | Still on disk but **not compiled**; safe to delete in a later cleanup |
| CMake / externalNativeBuild / C++ shims | Already gone on this branch |

## Gradle

`app/build.gradle` compiles Java + resources only. The `.so` is produced by `build-android.ps1` and placed in `jniLibs`.

## Ordered migration status

1. Read this file + journey / next-steps docs.
2. All live logic is Odin + intentional C FFI.
3. JNI layer is pure Odin.
4. Remaining open items: media metadata, optional deletion of dead module `.cpp` trees, CI smoke of native build if desired.
