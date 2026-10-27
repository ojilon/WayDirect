# jni â€” migration & improvements

**CMake target:** `WayDirect_engine` (SHARED â†’ `libWayDirect_engine.so`)  
**Depends on:** all domain static libs  
**Rule:** only place allowed to include `<jni.h>` / use `JNIEnv`

## Migrate

| Legacy | Destination |
|--------|-------------|
| `native/WayDirect_engine.cpp` | `native/jni/WayDirect_engine.cpp` |

`jni/CMakeLists.txt` links the module targets; `<WayDirect/...>` resolves through them
(no parent-dir include hacks). The legacy root `native/WayDirect_engine.cpp` is deleted.

## Improvements

- [x] Init path: accept app files dir; call `WayDirect::core::set_app_paths`
  (`ACTION_INIT_APP_PATHS`, fired from `MainActivity.onCreate`). Init is
  check-then-create per directory (`ensure_app_dirs`, status per folder, never
  assumed) and writes `<root>/paths.json` â€” the shared record Java consults
  for per-folder paths instead of re-deriving them. Response returns the
  `manifest` path plus `all_ready`.
- [x] New actions: `BUILD_INDEX` / `INDEX_META` / `SEARCH_INDEX` returning
  **paths + metadata**, not huge JSON (`storage/index.hpp`; JNI only routes)
- [x] Keep single `processAction(id, payload)` entry (see FUTURE_JNI_AND_CPP23.md)
- [x] No domain logic growth in this file â€” only route to modules
  (`build_index` moved down into `WayDirect_storage`)

## Agent checklist

- [x] `WayDirect_engine.cpp` lives under `jni/`
- [x] No other `.cpp` in the tree includes `jni.h`
- [x] Android build still produces `libWayDirect_engine.so`

