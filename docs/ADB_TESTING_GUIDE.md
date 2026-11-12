# ADB Testing Guide — WayDirect Android App

This guide covers the full build → install → run → test cycle using `adb` on a physical device.

---

## Prerequisites

| Tool | Where | Purpose |
|------|-------|---------|
| **Odin compiler** | `https://odin-lang.org` | Builds native `.odin` sources |
| **Android NDK r30** | `D:\Dev\AndroidCLI\sdk\ndk\30.0.16248370` (example) | `clang++` for C/C++ FFI files |
| **Android SDK** | `D:\Dev\AndroidCLI\sdk` (example) | `adb`, `aapt2`, platform tools |
| **JDK 17** | `JAVA_HOME` | Gradle / Android Gradle Plugin |
| **Gradle** | `gradlew` wrapper in repo root | Packages Java + resources + `.so` into APK |
| **Physical Android device** | USB debugging enabled | Test target |

---

## Step 0 — Device setup

1. Enable **Developer options** on your Android device (tap Build number 7 times in Settings → About phone).
2. Enable **USB debugging** in Developer options.
3. Plug the device into your PC via USB.
4. Authorize the PC when prompted on the device.
5. Verify the device is visible:

```powershell
D:\Dev\AndroidCLI\sdk\platform-tools\adb.exe devices
```

Expected output:
```
List of devices attached
0603937134005578	device
```

---

## Step 1 — Build the native shared library (`libwaydirect_engine.so`)

From the **repo root**:

```powershell
powershell -File native/build-android.ps1
```

This does:
1. Runs `odin build native/jni -build-mode:obj -target:linux_arm64 -subtarget:android -reloc-mode:pic -no-entry-point` — compiles all Odin packages (waydirect_jni → waydirect_core → core_heart, storage, documents, transfer, preview, media, json_utils) into object files.
2. Compiles remaining C/C++ FFI source files with NDK `clang++` (e.g. `android_fs.c`, `dir_walk_android.c`, `index_c.c`, `collect_data.c`).
3. Links everything into `native/build/arm64-v8a/libwaydirect_engine.so` (libc++ linked statically).
4. Copies the `.so` to `app/src/main/jniLibs/arm64-v8a/libwaydirect_engine.so` for Gradle packaging.

Expected output:
```
==================================
Build successful
ABI:    arm64-v8a
Output: D:\projects\WayDirect\native\build\arm64-v8a\libwaydirect_engine.so
Copied to: D:\projects\WayDirect\app\src\main\jniLibs\arm64-v8a\libwaydirect_engine.so
==================================
```

> **Config file:** Paths for NDK, SDK, and other build settings are read from `native/build-android.cfg` (gitignored). Create it if missing — see "Config file" below.

---

## Step 2 — Build the debug APK with Gradle

From the **repo root**:

```powershell
.\gradlew :app:clean :app:assembleDebug
```

This:
- Compiles Java sources (`app/src/main/java/`)
- Processes resources (`app/src/main/res/`)
- Merges the native `.so` from `app/src/main/jniLibs/arm64-v8a/`
- Packages `app/build/outputs/apk/debug/app-debug.apk`

The debug APK is signed with the default debug keystore and installed as package `com.waydirect.debug` (applicationIdSuffix `.debug`).

---

## Step 3 — Install the APK on the device

```powershell
D:\Dev\AndroidCLI\sdk\platform-tools\adb.exe install -r app/build/outputs/apk/debug/app-debug.apk
```

Expected output:
```
Performing Streamed Install
Success
```

If the app is already installed, `-r` reinstalls it while keeping data.

---

## Step 4 — Launch the app

```powershell
D:\Dev\AndroidCLI\sdk\platform-tools\adb.exe shell am start -n com.waydirect.debug/com.waydirect.core.MainActivity
```

Expected output:
```
Starting: Intent { cmp=com.waydirect.debug/com.waydirect.core.MainActivity }
```

The app should appear on the device screen with its Material 3 bottom navigation (Home, Files, Storage, Transfer, Cleaner, Internals).

---

## Step 5 — View logs (logcat)

Clear old logs, then capture fresh ones:

```powershell
D:\Dev\AndroidCLI\sdk\platform-tools\adb.exe logcat -c
```

Reproduce the action you want to test, then:

```powershell
D:\Dev\AndroidCLI\sdk\platform-tools\adb.exe logcat -d -s WayDirectNative WayDirectBridge WayDirectEngine System.err AndroidRuntime WayDirect
```

**Expected successful output:**
```
I WayDirectNative: initAppPaths: {"status":"paths_set","manifest":"...","all_ready":true,...}
D WayDirectBridge: lease acquired: .../cache/index/files.json by index
D WayDirectBridge: lease released: ...
```

**Expected crash output (if something fails):**
```
E AndroidRuntime: FATAL EXCEPTION: main
E AndroidRuntime: java.lang.UnsatisfiedLinkError: ...
```

---

## Step 6 — Force-stop and restart (for iteration)

```powershell
D:\Dev\AndroidCLI\sdk\platform-tools\adb.exe shell am force-stop com.waydirect.debug
D:\Dev\AndroidCLI\sdk\platform-tools\adb.exe shell am start -n com.waydirect.debug/com.waydirect.core.MainActivity
```

---

## Step 7 — Uninstall

```powershell
D:\Dev\AndroidCLI\sdk\platform-tools\adb.exe uninstall com.waydirect.debug
```

---

## Full one-shot cycle (for quick iteration)

```powershell
# 1. Build native library
powershell -File native/build-android.ps1

# 2. Build APK
.\gradlew :app:clean :app:assembleDebug

# 3. Install
D:\Dev\AndroidCLI\sdk\platform-tools\adb.exe install -r app/build/outputs/apk/debug/app-debug.apk

# 4. Launch
D:\Dev\AndroidCLI\sdk\platform-tools\adb.exe shell am start -n com.waydirect.debug/com.waydirect.core.MainActivity

# 5. Watch logs
D:\Dev\AndroidCLI\sdk\platform-tools\adb.exe logcat -s WayDirectNative WayDirectBridge WayDirectEngine AndroidRuntime
```

---

## Config file (`native/build-android.cfg`)

The build script reads tool paths from a gitignored config file so the repo stays portable across machines. If `native/build-android.cfg` does not exist, the script falls back to environment variables (`ODIN_ANDROID_NDK`, `ODIN_ANDROID_SDK`) and then to documented defaults.

Example `native/build-android.cfg` (create this locally, never commit):

```
# Odin compiler directory (contains odin.exe)
odin_dir=D:\Dev\Odin

# Android NDK root
ndk_dir=D:\Dev\AndroidCLI\sdk\ndk\30.0.16248370

# Android SDK root
sdk_dir=D:\Dev\AndroidCLI\sdk

# Target ABI
abi=arm64-v8a

# Minimum API level
min_api=29
```

Format: `key=value` lines, blank lines and `#` comments ignored.

---

## Troubleshooting

| Issue | Cause | Fix |
|-------|-------|-----|
| `device not found` | USB debugging off or unauthorized | Enable USB debugging, authorize PC on device |
| `INSTALL_FAILED_INVALID_APK` | `.so` missing from `jniLibs` | Run `native/build-android.ps1` first, then Gradle |
| `UnsatisfiedLinkError: couldn't find "libwaydirect_engine.so"` | Wrong `.so` filename casing | Ensure `libwaydirect_engine.so` (lowercase) in `jniLibs/arm64-v8a/` |
| `dlopen failed: library "libc++_shared.so" not found` | libc++ linked dynamically | Ensure `-static-libstdc++` in link flags |
| `dlopen failed: cannot locate symbol "..."` | Missing `.c`/`.cpp` in build script `$Sources` | Add missing FFI source to `native/build-android.ps1` |
| App crashes on launch | Odin symbol mismatch with Java action IDs | Verify action IDs match `NativeEngine.java` ↔ `waydirect_core.odin` |
