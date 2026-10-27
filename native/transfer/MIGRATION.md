# transfer â€” migration & improvements

**CMake target:** `WayDirect_transfer`  
**Depends on:** `WayDirect_core`, `WayDirect_storage` (paths, staging under temp/)

## Migrate

| Legacy | Destination |
|--------|-------------|
| `transfer_engine.cpp` | `src/transfer_engine.cpp` |
| `transfer_engine.hpp` | `include/WayDirect/transfer/transfer.hpp` |

## Improvements

- [ ] Use `app_paths().temp` for staging incoming/outgoing files
- [ ] Hash/verify helpers in C++; keep discovery/UI in Java until protocol stabilizes
- [ ] Future: LAN/internet transports as additional .cpp behind same facade
- [ ] Do not depend on documents/media (keep graph acyclic)

## Agent checklist

- [x] Sources under src/ + public include
- [x] JNI only calls into this module from `jni/`

