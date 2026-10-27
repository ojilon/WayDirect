# storage/modules/cleaner

**Target:** `WayDirect_storage_cleaner` (STATIC)

## Migrate â€” done

| Legacy | Destination |
|--------|-------------|
| `duplicate_finder.cpp/.hpp` | `src/` + `include/WayDirect/storage/cleaner.hpp` |
| `large_files.cpp/.hpp` | same module |

CMake is STATIC; those files are not in the parent `WayDirect_storage` sources.

## Improvements

- Safer delete plans (dry-run JSON file under cache)
- Grouped duplicate resolution payloads (may need real JSON parser)

