# Next Steps — Odin Android Bindings for WayDirect

**Branch:** `experiment/use_official_odin_android_bindings`  
**Status:** Native build works (`libWayDirect_engine.so` builds and copies to `jniLibs`)  
**Next:** Port remaining C++ FFI → pure Odin, add `odin bundle android` packaging, then test on device via adb.

---

## 1. Port remaining C++ FFI `.cpp` files to pure Odin

These files have no Odin mirror yet and are compiled by NDK `clang++` as legacy FFI:

| File | Status | Odin target |
|------|--------|-------------|
| `transfer/src/transfer_placeholder.cpp` | stub | `transfer.odin` |
| `transfer/src/transfer_shim.c` | C shim | `transfer.odin` + `index_c.c` |
| `documents/src/documents_placeholder.cpp` | stub | `documents.odin` |
| `media/src/media_placeholder.cpp` | stub | `media.odin` |
| `storage/src/storage_placeholder.cpp` | stub | `storage.odin` |

**Procedure per file:**

1. Create `foo.odin` in the module's `src/` directory with the module's package name.
2. Move the FFI declarations from `foreign import "system:c"` blocks in `waydirect_core.odin` (or create new ones) into the `.odin` file.
3. Implement the logic in Odin, using C wrappers (`stat_c`, `walk_files_c`, etc.) for file I/O — never `core:os` on Android (seccomp blocks `statx`/`openat2` on API 29).
4. Delete the `.cpp`/`.c` file and its `.hpp` if it has no other consumers.
5. Remove the entry from `native/build-android.ps1` `$Sources`.
6. Run `pwsh native/build-android.ps1` to verify the build still succeeds.

---

## 2. `odin bundle android` — APK packaging without Gradle

Odin's compiler has a built-in `bundle android` command that assembles a signed `.apk` (or `.aab`) from a directory layout. This can replace Gradle's `externalNativeBuild`/CMake packaging for the native portion, though Gradle is still needed for the Java/XML UI.

### What `odin bundle android` does

From `D:\Dev\Odin\src\main.cpp` (usage text):

```
odin bundle android <dir> makes an .apk, or an Android App Bundle with -out:<name>.aab, from:
  <dir>/AndroidManifest.xml
  <dir>/res/      (optional) resources, compiled with aapt2
  <dir>/assets/   (optional)
  <dir>/lib/<abi>/*.so, e.g. lib/arm64-v8a/libmain.so
It adds Odin's classes.dex, with the activity org.odin_lang.OdinActivity, and signs the result.
Needs ODIN_ANDROID_SDK (build-tools and a platform), a JDK (JAVA_HOME or PATH),
and for an .aab, ODIN_ANDROID_BUNDLETOOL set to bundletool's .jar.
```

### Required directory layout

```
app/
├── AndroidManifest.xml      ← must declare package + activity + android.app.lib_name
├── res/                     ← optional resources (compiled with aapt2)
├── assets/                  ← optional assets
└── lib/
    └── arm64-v8a/
        └── libWayDirect_engine.so   ← built by build-android.ps1
```

### Required environment

| Variable | Value on this PC | Purpose |
|----------|-----------------|---------|
| `ODIN_ANDROID_SDK` | `D:\Dev\AndroidCLI\sdk` | build-tools (aapt2), platforms (android.jar) |
| `JAVA_HOME` | JDK 17 | javac for classes.dex, APK signing |
| `ODIN_ANDROID_BUNDLETOOL` | path to bundletool `.jar` | only needed for `.aab` output |

### Build command

```powershell
# 1. Build the native .so first
pwsh native/build-android.ps1

# 2. Bundle into APK
$env:ODIN_ANDROID_SDK = "D:\Dev\AndroidCLI\sdk"
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"   # adjust to your JDK path
odin bundle android app -minimum-os-version:29 -out:WayDirect.apk
```

### What gets added automatically

- `classes.dex` — contains `org.odin_lang.OdinActivity` (a `NativeActivity` subclass) and `org.odin_lang.NativeProxy` (JNI proxy for Odin→Java callbacks). Pre-built from `D:\Dev\Odin\vendor\android\java\_dex/` (`NativeProxy.java`, `OdinActivity.java`) and embedded in `vendor/android/java/classes.dex`.
- Signing — debug keystore by default (`~/.android/debug.keystore`).

### Manifest requirements

The `AndroidManifest.xml` must name `org.odin_lang.OdinActivity` (or a custom `NativeActivity` subclass) and set the native library name:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.waydirect">
    <application>
        <activity
            android:name="org.odin_lang.OdinActivity"
            android:exported="true">
            <meta-data android:name="android.app.lib_name" android:value="WayDirect_engine"/>
            <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/>
            </intent-filter>
        </activity>
    </application>
</manifest>
```

### Notes for WayDirect

- WayDirect's UI is Material 3 Java/XML (Fragments, NavigationBarView, ViewBinding), so **Gradle is still required** for the Java/XML portion. `odin bundle android` produces a pure-Odin `NativeActivity` app — not suitable for WayDirect's current UI without a full rewrite.
- The pragmatic path: keep Gradle for Java/XML + resources, use `build-android.ps1` for the `.so`, then `./gradlew assembleDebug` to package everything into one APK.
- `odin bundle android` is useful for CI or for a future pure-Odin viewer/tool within the project.

---

## 3. Test on device via adb

**Prerequisites:**
- Android phone plugged in via USB, USB debugging enabled
- `adb` on PATH (from `D:\Dev\AndroidCLI\sdk\platform-tools\adb.exe`)

### Commands

```powershell
# 1. Verify device is connected
adb devices

# 2. Build the native .so
pwsh native/build-android.ps1

# 3. Build the APK with Gradle
./gradlew :app:assembleDebug

# 4. Install on device
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 5. Launch the app
adb shell am start -n com.waydirect/.core.MainActivity

# 6. View logs
adb logcat -s WayDirectNative WayDirectBridge WayDirectEngine

# 7. Uninstall (if needed)
adb uninstall com.waydirect
```

### Expected behavior

- App launches, shows Material 3 UI with bottom navigation (Home, Files, Storage, Transfer, Cleaner, Internals)
- `NativeEngine.initEngine()` calls `JNI_OnLoad` in `waydirect_jni.odin`, which sets `android.jni_set_vm(vm)`
- `NativeEngine.initAppPathsAsync()` calls `route_action(ACTION_INIT_APP_PATHS, filesDir)` → Odin `init_app_paths_internal` → writes `paths.json` manifest
- Native file operations (list, search, stats, etc.) go through `route_action` → Odin modules → write result files under `files/WayDirect/cache/` → return `{status, path}`

### Troubleshooting

| Issue | Fix |
|-------|-----|
| `adb: device not found` | Enable USB debugging on phone; check `adb devices` |
| `INSTALL_FAILED_INVALID_APK` | Ensure `build-android.ps1` ran first so `.so` exists in `jniLibs` |
| App crashes on native call | Check `adb logcat` for `WayDirectNative` / `WayDirectBridge` tags; verify Odin symbols match Java action IDs |
| UnsatisfiedLinkError | Confirm `libWayDirect_engine.so` is in `app/src/main/jniLibs/arm64-v8a/` and matches device ABI |

---

## 4. Remaining work after device test

1. Verify all 19 action IDs match between `NativeEngine.java` and `waydirect_core.odin` `route_action`
2. Test each screen: Home, Files, Storage, Transfer, Cleaner, Internals
3. Verify file-backed protocol: large results return `{status, path}`, Java reads from `PathCache`
4. Port remaining `.cpp` FFI files to pure Odin (see Step 1)
5. Consider `odin bundle android` for CI builds of the native portion