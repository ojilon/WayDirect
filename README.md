# WayDirect (Android) — Odin native + Material 3 Java/XML

Android client for browsing local storage and transferring files with a PC companion over a phone hotspot.

**This branch** (`experiment/use_official_odin_android_bindings`) is the Odin-first native rewrite:

- **UI:** Material 3 Java + XML (Fragments, NavigationBarView, ViewBinding)
- **Native engine:** Odin (`native/**/*.odin`) + thin bionic-safe C FFI
- **JNI:** pure Odin (`native/jni/waydirect_jni.odin`) using official Android bindings
- **Build:** `native/build-android.ps1` produces `libwaydirect_engine.so`; Gradle packages Java/resources + the pre-built `.so`
- **Target:** Android 10+ (API 29 min for Odin path; app `minSdk` 26), ABI `arm64-v8a` only on this branch

| Doc | Purpose |
|-----|---------|
| [NEXT_STEPS_ODIN_BINDINGS.md](NEXT_STEPS_ODIN_BINDINGS.md) | Remaining work, `odin bundle android` notes, Java/XML/Material 3 implications |
| [JOURNEY_ODIN_ANDROID_BINDINGS.md](JOURNEY_ODIN_ANDROID_BINDINGS.md) | How official Odin Android bindings were adopted |
| [native/MODULES.md](native/MODULES.md) | Native modular layout + dependency graph |
| [README_ANDROID_END.md](README_ANDROID_END.md) | Broader Android setup / structure notes |
| [docs/](docs/) | Bridge plan, UI guide, ADB, testing, etc. |

---

## Quick start

```bash
# Odin on PATH (dev-2026-11 or compatible): https://odin-lang.org/docs/install/
odin version

# 1) Native shared library (Windows host with NDK + Odin)
pwsh native/build-android.ps1
# → native/build/arm64-v8a/libwaydirect_engine.so
# → copied into app/src/main/jniLibs/arm64-v8a/

# 2) APK
./gradlew :app:assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk

# Unit tests (Java)
./gradlew :app:testDebugUnitTest
```

Set the PC address in `app/src/main/java/com/waydirect/core/Config.java` (`HOST` / `PORT`) before Transfer tests.

Install / launch:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.waydirect.debug/com.waydirect.core.MainActivity
adb logcat -s WayDirectNative WayDirectBridge WayDirectEngine
```

---

## What the app does

| Tab | Role |
|-----|------|
| **Home** | Recent files by day, storage summary, shortcuts |
| **Files** | Browse, live search, create/rename/delete, open image/video/document |
| **Storage** | Stats snapshot (categories), refresh |
| **Transfer** | Queue, upload/download, network status |
| **Cleaner** | Duplicates (size → partial hash → full hash + byte verify), large files |
| **Internals** | Read-only view of private app home (index, cache, logs, manifest) |

**Architecture**

- **Java + XML** — UI (Material 3), navigation, sockets to PC
- **`bridge/`** — doorway to native: action IDs, path registry, leases, result-path cache
- **Odin + C FFI** — work under private app home (`files/WayDirect/`); JNI carries small `{status, path}` replies; large payloads are file-backed
- **Networking** stays on the Java side (`NetworkManager`)

There is **no** C/C++ under `app/`. All native sources live under `native/`.

---

## Project layout

```text
WayDirect/
├── app/                         # Android application (Java + XML only)
│   └── src/main/
│       ├── java/com/waydirect/
│       │   ├── bridge/          # NativeEngine, Bridge, PathRegistry, leases, caches
│       │   ├── core/            # MainActivity, Config, theme/chrome
│       │   ├── ui/              # Fragments, adapters, viewers
│       │   ├── network/         # Hotspot protocol to PC
│       │   ├── storage/         # FileMutator, OrganizeHelper, controllers
│       │   └── transfer/        # Controllers, upload queue
│       ├── res/                 # Layouts, drawables, themes, menus
│       └── jniLibs/arm64-v8a/   # Pre-built libwaydirect_engine.so (from build-android.ps1)
├── native/                      # Odin + thin C FFI (the engine)
│   ├── build-android.ps1        # Builds the .so (Odin + NDK clang for C)
│   ├── MODULES.md
│   ├── jni/waydirect_jni.odin   # JNI_OnLoad + Java_* exports
│   ├── odin/waydirect_core.odin # Action router + C ABI exports
│   ├── core/ src/*.odin
│   ├── storage/ *.odin + dir_walk_android.c + index_c.c
│   ├── documents/ transfer/ media/ preview/
│   └── file_system_bridge/      # android_fs.c + Odin wrappers
├── docs/
├── JOURNEY_ODIN_ANDROID_BINDINGS.md
├── NEXT_STEPS_ODIN_BINDINGS.md
├── gradle.properties            # versionCode, versionName, aurora.abiFilters=arm64-v8a
└── .github/workflows/           # Unit-test CI
```

---

## Native build notes

- **No CMake / no `externalNativeBuild` in Gradle.** The `.so` is produced by `native/build-android.ps1` and dropped into `jniLibs`.
- Odin owns the router and domain logic. Remaining **C** files are intentional bionic-safe wrappers (`opendir` / `readdir` / `stat` / `open` / `read` — no `statx` / `openat2` on API 29 seccomp).
- Large results never cross JNI as big strings; native writes under the app cache and returns `{status, path}`.

See [native/MODULES.md](native/MODULES.md) and [NEXT_STEPS_ODIN_BINDINGS.md](NEXT_STEPS_ODIN_BINDINGS.md) (including why `odin bundle android` does not replace Gradle for this Material 3 UI).

---

## Build / release reference

| Topic | Where |
|-------|--------|
| Version / ABI / signing | `gradle.properties`, `app/build.gradle`, [docs/RELEASE_AND_BUILD.md](docs/RELEASE_AND_BUILD.md) if present |
| Unit tests | [docs/TESTING.md](docs/TESTING.md) |
| adb / logcat | [docs/ADB_GUIDE.md](docs/ADB_GUIDE.md) |
| UI tokens / XML | [docs/XML_UI_GUIDE.md](docs/XML_UI_GUIDE.md), [docs/FRONTEND_IMPROVEMENTS.md](docs/FRONTEND_IMPROVEMENTS.md) |

Default ABI on this branch (`aurora.abiFilters` in `gradle.properties`):

```text
arm64-v8a
```

---

## Related

- PC companion work lives in related repos/branches as you maintain them
- Odin docs: [odin-lang.org/docs](https://odin-lang.org/docs/)
