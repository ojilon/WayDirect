# WayDirect â€” Android end (detailed)

Android app for local file management and transfers with **WayDirectPC** over hotspot.

This document matches the **current** codebase on branch `refactor/native-modules`.

---

## Prerequisites

- Android Studio or command-line Android SDK  
- **JDK 17**  
- **NDK** (version pinned in `app/build.gradle`, e.g. `29.0.14206865`)  
- **CMake** 3.22+  
- Device or emulator (ABI must be in `aurora.abiFilters`)  
- Optional: PC running WayDirectPC for Transfer tests  

Min SDK **26**, compile SDK **36**, target SDK **34**.

---

## Clone & branch

```bash
git clone https://github.com/ojilon/WayDirect.git
cd WayDirect
git checkout refactor/native-modules
```

---

## Configure PC address

```text
app/src/main/java/com/example/WayDirect/core/Config.java
```

```java
public static final String HOST = "192.168.x.x";  // PC hotspot IP
public static final int PORT = 5000;
```

---

## Build & install

```bash
./gradlew :app:testDebugUnitTest   # unit tests
./gradlew :app:assembleDebug      # debug APK
./gradlew :app:assembleRelease    # release (signing optional)

adb install -r app/build/outputs/apk/debug/app-debug.apk
```

| Output | Path |
|--------|------|
| Debug APK | `app/build/outputs/apk/debug/app-debug.apk` |
| Release APK | `app/build/outputs/apk/release/` |

**Version & ABIs** â€” edit only `gradle.properties`:

```properties
app.versionCode=1
app.versionName=0.0.1_0
aurora.abiFilters=arm64-v8a,armeabi-v7a,x86_64
```

Signing: copy `keystore.properties.example` â†’ `keystore.properties` (gitignored), or set `WayDirect_*` env vars.  
See [docs/RELEASE_AND_BUILD.md](docs/RELEASE_AND_BUILD.md).

---

## App screens

Six destinations in a scrollable bottom bar (`MainActivity` + toggle group):

### Home
- Storage used/total from the shared snapshot file (`bridge/Stats` â†’ C++ Action 13)
- Category breakdown + top folders (new `folders` array in the snapshot)
- Shortcuts to Files / Storage / Transfer

### Files
- Left drawer (Downloads, DCIM, Movies, Documents, â€¦) â€” paths via `bridge/AppDirs`
- Remembers its folder + explicit Up button (process-scoped `BrowseSession`)
- List directory (**Action 3**), live scoped search with previous-list-first (**Action 8**, file-backed)
- Long-press item â†’ open / rename / delete; long-press path bar â†’ new file / folder
- Open routes via `FileOpenHelper` â†’ Image / Video / Document activity

### Storage
- Same snapshot as Home + visual category bars + Refresh (forces recompute)
- Large-file scanning moved to the **Cleaner** tab

### Transfer
Sideways tabs (Guide | Transfer | Search | Browse | Network):
- **Guide** â€” connection steps as a static page
- **Transfer** â€” file-transfer card: `/ask` download, `/upload` send, session summary
- **Search** â€” global index search with multi-select send; folders open inline with Up
- **Browse** â€” save-folder picker with filter + Up (remembers folder)
- **Network** â€” test connection, listener, session stats, activity log, recents
- Multi-select queue (`modules/transfer/queue.json`) uploads one file per `/upload`;
  upload basenames are space-sanitized for the protocol (local files untouched)

### Cleaner
- Card grid (Duplicates, Large files, extensible per `docs/CLEANER_IDEAS.md`)
- Sideways utility tabs; each utility scans into its result file under lease

### Internals
- Read-only browser of the private app home (`files/WayDirect/`): index, cache,
  `paths.json` manifest, logs. Files open in `DocumentActivity` via the C++
  preview module (**Action 19**, capped, binary refused).

---

## Architecture

```text
â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€ XML layouts â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”
â”‚  Fragments / Activities (ViewBinding) â”‚
â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”¬â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜
                  â”‚ display only
â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â–¼â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”
â”‚  bridge/: single doorway to native  â”‚
â”‚  PathRegistry (paths.json) Â· leases â”‚
â”‚  result-path cache (PathCache)      â”‚
â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”¬â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜
                  â”‚ paths in, files out, {status} back
â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â–¼â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”
â”‚  native/ C++23 modules + JNI router â”‚
â”‚  files under files/WayDirect/ (private) â”‚
â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜
Java sockets (NetworkManager) stay on the Java side.
```

### Native action IDs

| ID | Purpose | Shape |
|----|---------|-------|
| 3 | List directory | path â†’ JSON list |
| 4 | Network info | tiny JSON |
| 5 | Filter documents | path â†’ JSON list |
| 6 | Start listener | port â†’ status |
| 8 | Search files | `root\|out\|queryâ€¦` â†’ `{status,path}` |
| 9 | Find large files | `root\|min\|max\|out` â†’ `{status,path}` |
| 10 | Find duplicates | `root\|out` â†’ `{status,path}` |
| 11 | Plan organize | `root\|out` â†’ `{status,path}` |
| 12 | Apply organize | `plan\|report` â†’ `{status,path}` |
| 13 | Cached stats | `cache\|root\|age[\|bytes]` â†’ `{status,path}` (age â‰¤ 0 forces recompute) |
| 14 | Init app paths | root â†’ manifest + `all_ready` |
| 15 | Build index | root â†’ `{path,count}` |
| 16 | Invalidate cache | path â†’ status |
| 17 | Index meta | â†’ `{status,path,bytes,modified_unix}` |
| 18 | Search index | `out\|max\|queryâ€¦` â†’ `{status,path}` |
| 19 | Read text file | `path\|bytes` â†’ capped lines (read-only) |

Action 7 (inline stats) was retired into 13. See `native/FUTURE_JNI_AND_CPP23.md`
for the roadmap and `docs/BRIDGE_PLAN.md` for the file-backed protocol.

### Main source map

```text
app/src/main/java/com/example/WayDirect/
  bridge/        NativeEngine (moved), Bridge, PathRegistry, PathCache,
                 FileLeases, Stats, AppDirs, NativeCache, StorageCapacity
  core/          MainActivity, Config, ThemePrefs, GlassBlur, UiChrome
  ui/            Home/Files/Storage/Transfer/Cleaner/Internal fragments,
                 Document/Image/Video activities, adapters, BrowseSession
  network/       NetworkManager, NetworkCallback
  storage/       FileMutator, FileIndexer (paths), OrganizeHelper, â€¦
  transfer/      TransferController, TransferQueue, RecentTransfersStore
  utils/         TextSanitizer

native/
  jni/            WayDirect_engine.cpp â€” sole JNI boundary (thin router)
  core/           paths (+manifest), logging, json helpers, text helpers
  storage/        walk/stats/cache/list + search/cleaner/organizer submodules
  documents/      document filter
  transfer/       listener / network info helpers
  media/          placeholder for later
  preview/        read-only text preview for the in-app viewer
  third_party/    nlohmann/json + SQLite (vendored locally, gitignored)
```

C++ style for the tree (structs + free functions, headers declare / `.cpp`
defines, no `class`/`inline`/macros): see `native/MODULES.md`.

---

## Transfer protocol (phone â†” WayDirectPC)

```text
Download:
  phone â†’ /ask <filename>
  pc    â†’ FOUND <size>
  phone â†’ /send
  pc    â†’ <bytes>

Upload:
  phone â†’ /upload <size> <filename>
  pc    â†’ READY (or /send)
  phone â†’ <bytes>
```

Upload basenames are space-sanitized for the protocol token only
(`file name` â†’ `file_name`); the local file is never renamed.

File names for Transfer UI are **absolute paths** for upload (index search /
browse / queue) and land in the public save folder (default `Download/`) for
downloads.

---

## Tests & CI

```bash
./gradlew :app:testDebugUnitTest
```

- Unit tests: `TextSanitizer`, `FileMutator`, `FileItem`, `NetworkManager`
  (upload token spacing)
- CI: `.github/workflows/android-ci.yml`
- How to write more: [docs/TESTING.md](docs/TESTING.md)

---

## Documentation index

| File | Topic |
|------|--------|
| [docs/HOME_AND_FILES.md](docs/HOME_AND_FILES.md) | Home + Files flow |
| [docs/STORAGE_AND_TRANSFER.md](docs/STORAGE_AND_TRANSFER.md) | Storage, Transfer, FileMutator |
| [docs/MEDIA_AND_DOCUMENTS.md](docs/MEDIA_AND_DOCUMENTS.md) | Viewers + third_party |
| [docs/RELEASE_AND_BUILD.md](docs/RELEASE_AND_BUILD.md) | Version, ABI, signing, Python tool |
| [docs/TESTING.md](docs/TESTING.md) | Tests |
| [docs/XML_UI_GUIDE.md](docs/XML_UI_GUIDE.md) | Learn / improve XML UI |
| [docs/ICONS.md](docs/ICONS.md) | Vector launcher & nav icons |
| [docs/BRIDGE_PLAN.md](docs/BRIDGE_PLAN.md) | File-backed Java â†” C++ plan |
| [docs/TRANSFER_CLEANER_PLAN.md](docs/TRANSFER_CLEANER_PLAN.md) | Transfer + Cleaner rework |
| [docs/CLEANER_IDEAS.md](docs/CLEANER_IDEAS.md) | Cleaner utility backlog |
| [docs/ADB_GUIDE.md](docs/ADB_GUIDE.md) | Install / logcat / inspect |

---

## Permissions

Declared in `AndroidManifest.xml`:

- `INTERNET`
- `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE` (+ `requestLegacyExternalStorage`)
- At runtime the app requests **Manage all files access** (`isExternalStorageManager`)
  for full shared-storage browsing on Android 11+.

---

## Troubleshooting

| Issue | What to try |
|-------|-------------|
| Build / NDK fails | Install matching NDK; `./gradlew clean`; check `ndkVersion` |
| App only on your phone | Ensure device ABI is in `aurora.abiFilters` |
| Transfer fails | Same hotspot; WayDirectPC running; correct `Config.HOST`/`PORT` |
| Empty file list | Storage permission; path exists (`/storage/emulated/0/...`) |
| Large scan slow | Expected on full tree; threshold is 10 MB in the Cleaner tab |
| Release unsigned | Add `keystore.properties` or `WayDirect_*` env |
| clangd red in `native/` | Build once, then `:app:copyCompileCommands` refreshes `build/compile_commands.json` (see `.clangd`) |

---

## Status notes

- **Usable** for browse, search, local file ops, storage overview, PC transfer
  queue, duplicates + large-file cleanup
- **Document rendering**: text preview via the C++ `preview` module; rich
  formats (PDF/office) wait on engines under `native/third_party/`
- Cleaner utilities grow per `docs/CLEANER_IDEAS.md`; bridge follow-ups per
  `docs/BRIDGE_PLAN.md` (only Steps 0â€“7 coded; device passes pending per step)

