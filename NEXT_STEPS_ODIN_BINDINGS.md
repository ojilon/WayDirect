# Next Steps — Odin Android Bindings for WayDirect

**Branch:** `experiment/use_official_odin_android_bindings`  
**Status:** Native build works AND app tested successfully on device via adb  
**Done:** App builds, installs, launches, and Odin JNI layer works end-to-end  
**Next:** Remaining C FFI is intentional (bionic-safe walk/index); pure-Odin media metadata later; `odin bundle android` packaging notes

---

## 1. Remaining C++ FFI → pure Odin (completed for placeholders)

The last `.cpp` placeholders (module anchors that existed only so the old CMake modules had a translation unit) have been removed from `native/build-android.ps1` `$Sources` and deleted:

| Former file | Status |
|-------------|--------|
| `transfer/src/transfer_placeholder.cpp` | deleted (logic already in Odin + `transfer_shim.c`) |
| `documents/src/documents_placeholder.cpp` | deleted (logic in `documents.odin` + C helpers) |
| `media/src/media_placeholder.cpp` | deleted (stub in `media.odin`) |
| `storage/src/storage_placeholder.cpp` | deleted (logic in storage `*.odin`) |

**What remains (intentionally C, not C++):**

- `storage/src/dir_walk_android.c`, `index_c.c`
- `storage/modules/search/src/collect_data.c`
- `documents/src/check_if_directory.c`, `doc_filter_helper.c`
- `transfer/src/transfer_shim.c`
- `file_system_bridge/src/android_fs.c`

These are thin, bionic-safe wrappers (opendir/readdir/stat/open/read — no `statx`/`openat2`) that Odin calls via `foreign`. They are not candidates for a pure-Odin rewrite until Odin gains a stable, seccomp-friendly Android FS path or we accept a higher minSdk.

Dead C++ left under `storage/modules/*/src/*.cpp` (duplicate_finder, large_files, file_organizer, file_search) is **not** compiled by the current build script; the live implementations are the corresponding `*.odin` files. They can be deleted in a follow-up cleanup commit once you are sure no local scripts reference them.

**Procedure that was used:**

1. Confirm each module already had a working `.odin` implementation (cleaner, organizer, search, documents, media, transfer).
2. Remove the placeholder `.cpp` entries from `$Sources` in `build-android.ps1`.
3. Delete the placeholder files.
4. Re-run `pwsh native/build-android.ps1` (on the Windows host with Odin + NDK) to verify.

---

## 2. `odin bundle android` — APK packaging without Gradle (notes on Java / XML / Material 3)

Odin's compiler has a built-in `bundle android` command that assembles a signed `.apk` (or `.aab`) from a directory layout. This can replace Gradle's packaging for a **pure-Odin** app, but it does **not** replace Gradle for WayDirect today.

### What `odin bundle android` does

From the Odin compiler usage text:

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
        └── libwaydirect_engine.so   ← built by build-android.ps1
```

### Required environment

| Variable | Purpose |
|----------|---------|
| `ODIN_ANDROID_SDK` | build-tools (aapt2), platforms (android.jar) |
| `JAVA_HOME` | javac for classes.dex, APK signing |
| `ODIN_ANDROID_BUNDLETOOL` | only needed for `.aab` output |

### Build command (example)

```powershell
# 1. Build the native .so first
pwsh native/build-android.ps1

# 2. Bundle into APK
$env:ODIN_ANDROID_SDK = "D:\Dev\AndroidCLI\sdk"
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"   # adjust to your JDK path
odin bundle android app -minimum-os-version:29 -out:WayDirect.apk
```

### What gets added automatically

- `classes.dex` — contains `org.odin_lang.OdinActivity` (a `NativeActivity` subclass) and `org.odin_lang.NativeProxy` (JNI proxy for Odin→Java callbacks). Pre-built and embedded in the Odin distribution.
- Signing — debug keystore by default (`~/.android/debug.keystore`).

### Manifest requirements for pure-Odin path

The `AndroidManifest.xml` must name `org.odin_lang.OdinActivity` (or a custom `NativeActivity` subclass) and set the native library name:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.waydirect">
    <application>
        <activity
            android:name="org.odin_lang.OdinActivity"
            android:exported="true">
            <meta-data android:name="android.app.lib_name" android:value="waydirect_engine"/>
            <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/>
            </intent-filter>
        </activity>
    </application>
</manifest>
```

### Why this is *not* a drop-in for WayDirect today (Java / XML / Material 3)

WayDirect's UI is **Material 3 Java + XML**:

- Fragments (`HomeFragment`, `FilesFragment`, `StorageFragment`, `TransferFragment`, `CleanerFragment`, …)
- NavigationBarView / bottom navigation
- ViewBinding, Theme overlays, glass-blur chrome, custom views (`StorageRingView`, adapters, etc.)
- Resource system: layouts, drawables, values, themes under `app/src/main/res/`
- Java bridge layer (`NativeEngine`, `Bridge`, `PathRegistry`, `PathCache`, leases, capacity helpers)

`odin bundle android` produces a **pure-Odin NativeActivity** app. It does **not**:

1. Compile or package arbitrary Java sources (your `com.waydirect.*` package).
2. Process your XML layouts / Material 3 themes / ViewBinding generated classes.
3. Wire the existing `MainActivity` + Fragment navigation graph.
4. Preserve the current JNI surface (`processAction` / action IDs) in the same form — the bundler injects `OdinActivity` + `NativeProxy` instead.

Consequently:

- **Gradle remains required** for the Java/XML/Material 3 portion. The pragmatic path stays: `build-android.ps1` → copy `.so` into `jniLibs` → `./gradlew assembleDebug`.
- An Odin rewrite of *some* of the Java/XML surface is possible later, but it is a product decision, not a build-system switch:
  - **What Odin can reasonably own today:** the entire native engine (already mostly done), file walk/index/search/organize/cleaner, network/transfer core, any future native-drawn preview or terminal.
  - **What would have to be rewritten or abandoned for a pure `odin bundle` path:** every Fragment, every Adapter, ThemePrefs, UiChrome, Material components, the current bottom-nav IA, and the Java-side path/lease/cache helpers (or reimplemented via Odin ↔ Java proxies).
  - **Hybrid middle ground (recommended):** keep Material 3 UI in Java/XML; continue thinning the Java bridge so that more logic lives in Odin; optionally use `vendor:android/java` + `proxy_new` for callbacks; use `odin bundle android` only for a secondary pure-Odin tool (e.g. a headless viewer or a kiosk-style native screen) if needed.

In short: step 2 is valuable for CI of the native library or for a future pure-Odin binary, but it does **not** eliminate the Java/XML/Material 3 stack that defines WayDirect's current UX. Do not delete Gradle or the Java UI sources in pursuit of `odin bundle`.

---

## 3. Test on device via adb

**Prerequisites:**
- Android phone plugged in via USB, USB debugging enabled
- `adb` on PATH

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

- App launches, shows Material 3 UI with bottom navigation
- `NativeEngine.initEngine()` calls into Odin JNI (`JNI_OnLoad` / `android.jni_set_vm`)
- `NativeEngine.initAppPathsAsync()` → Odin `init_app_paths_internal` → `paths.json`
- Native file operations go through `route_action` → Odin modules → cache files → `{status, path}`

### Troubleshooting

| Issue | Fix |
|-------|-----|
| `adb: device not found` | Enable USB debugging; check `adb devices` |
| `INSTALL_FAILED_INVALID_APK` | Ensure `build-android.ps1` ran first so `.so` exists in `jniLibs` |
| App crashes on native call | Check `adb logcat` for tags; verify action IDs match |
| UnsatisfiedLinkError | Confirm `libwaydirect_engine.so` is in `app/src/main/jniLibs/arm64-v8a/` |

---

## Device Test Results (completed earlier)

**Device:** Transsion phone, arm64-v8a  
**Package:** `com.waydirect.debug`  
**Activity:** `com.waydirect.core.MainActivity`

Verified:
- `libwaydirect_engine.so` loads
- `JNI_OnLoad` / path init / lease system work
- No AndroidRuntime crashes on the exercised path

## 4. Remaining work after device test

1. Verify all action IDs still match between `NativeEngine.java` and `waydirect_core.odin`
2. Test each screen: Home, Files, Storage, Transfer, Cleaner, Internals
3. Verify file-backed protocol end-to-end
4. Optional: delete the dead `storage/modules/*/src/*.cpp` files
5. Optional: use `odin bundle android` in CI only for the native `.so` packaging smoke test (not as the primary APK path)
6. Media metadata extraction (still a pure-Odin opportunity)
