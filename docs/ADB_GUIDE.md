# ADB guide: install, logcat, inspect (WayDirect)

All commands run from the repo root on your PC. `adb` lives at
`<sdk>/platform-tools/adb` â€” replace `<sdk>` with your Android SDK path
(or put `platform-tools` on `PATH` and call `adb` directly).
The examples below use a `ADB` placeholder for that binary.

```powershell
$ADB = "<sdk>/platform-tools/adb.exe"
```

Phone prep (once): Settings â†’ Developer options â†’ enable **USB debugging**
(plug in, accept the RSA fingerprint prompt on the phone when asked).

---

## 1. Is the phone visible?

```powershell
& $ADB devices
```

You want your serial plus the word `device`. `unauthorized` means: accept the
prompt on the phone screen, then run it again.

## 2. Build + install the debug APK

```powershell
.\gradlew.bat :app:assembleDebug
& $ADB install -r app/build/outputs/apk/debug/app-debug.apk
```

- `-r` reinstalls over the existing copy. Expect `Success`.
- The debug package is `com.example.WayDirect.debug` (see the
  `applicationIdSuffix ".debug"` in `app/build.gradle`), so it never touches
  a release install.
- First install (no `-r` needed): drop the `-r` flag.

## 3. Launch from the terminal (optional)

```powershell
& $ADB shell am start -n com.example.WayDirect.debug/com.example.WayDirect.core.MainActivity
```

## 4. Watch logs while you tap around

```powershell
& $ADB logcat -c   # clear old noise first
& $ADB logcat | Select-String "WayDirectNative|AndroidRuntime"
```

- `WayDirectNative` â€” our native engine: the `initAppPaths` line prints the
  resolved app root plus the `paths.json` manifest path. If you see
  `paths_not_initialized` anywhere, init never ran.
- `AndroidRuntime` â€” crashes. A `FATAL EXCEPTION` block here means: copy the
  whole block (it names the file/line) and fix, don't guess.
- Everything else (`BufferQueueProducer` fps lines, `GC freed ...`) is normal
  system noise â€” ignore it unless you are chasing jank or memory.

## 5. Inspect the private app home (no root needed)

Internal files live under `getFilesDir()` and are invisible to file
explorers â€” but `run-as` lets you look inside a debuggable build:

```powershell
& $ADB shell run-as com.example.WayDirect.debug ls -R files/WayDirect
& $ADB shell run-as com.example.WayDirect.debug cat files/WayDirect/paths.json
```

## 6. Uninstall / start clean

```powershell
& $ADB uninstall com.example.WayDirect.debug
```

Useful to test first-run init from scratch. If an install ever fails with
signature mismatch, uninstall first, then install.

## 7. Signed vs unsigned APKs

- **Debug** (`assembleDebug`): automatically signed with a dev key.
  Installs and runs anywhere. This is what you iterate with.
- **Unsigned release**: Android refuses to install it
  (`INSTALL_PARSE_FAILED_NO_CERTIFICATES` on older versions). There is no
  way around this â€” a release must be signed.
- **Proper signed release**: create `app/keystore.properties` from
  `keystore.properties.example`, then:

```powershell
.\gradlew.bat :app:assembleRelease
& $ADB install app/build/outputs/apk/release/app-release.apk
```

Verify any APK's signature with:

```powershell
& "<sdk>/build-tools/<version>/apksigner.bat" verify --print-certs app/build/outputs/apk/<variant>/app-<variant>.apk
```

## 8. Typical loop

```powershell
.\gradlew.bat :app:assembleDebug
& $ADB install -r app/build/outputs/apk/debug/app-debug.apk
& $ADB logcat -c
& $ADB logcat | Select-String "WayDirectNative|AndroidRuntime"
# tap around on the phone, read the filtered output
```

extra
```
```bash
adb logcat --pid=$(adb shell pidof -s com.example.WayDirect.debug) *:E
```

That attaches only to your appâ€™s process and shows **Error** level and above, so most system noise is gone.

### Useful variants

**All levels for the app only (still process-filtered):**
```bash
adb logcat --pid=$(adb shell pidof -s com.example.WayDirect.debug)
```

**App + crash/FATAL lines (good for the inflate crash you hit):**
```bash
adb logcat --pid=$(adb shell pidof -s com.example.WayDirect.debug) *:E AndroidRuntime:E
```

**If the process isnâ€™t running yet** (start the app, then run):
```bash
adb logcat -c && adb shell am start -n com.example.WayDirect.debug/com.example.WayDirect.core.MainActivity && adb logcat --pid=$(adb shell pidof -s com.example.WayDirect.debug) *:E
```

**Tag-based filter** (when you care about specific tags like `WayDirectNative` / `WayDirectStorage`):
```bash
adb logcat WayDirectNative:E WayDirectStorage:E AndroidRuntime:E *:S
```

`*:S` silences everything else; only the listed tags at Error+ are shown.

Use the package `com.example.WayDirect` (no `.debug`) if youâ€™re on a release build.
```
